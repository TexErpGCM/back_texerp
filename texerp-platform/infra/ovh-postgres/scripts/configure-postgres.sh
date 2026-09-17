#!/usr/bin/env bash
set -Eeuo pipefail
set +x
umask 077

ENV_FILE="${1:-}"
if [[ "${EUID}" -ne 0 ]]; then
  echo "Ejecute este instalador mediante sudo." >&2
  exit 1
fi
if [[ -z "${ENV_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "No se encontro el archivo .env remoto." >&2
  exit 1
fi

trap 'rm -f -- "${ENV_FILE}"' EXIT
chmod 600 "${ENV_FILE}"

required=(VPS_SSH_PORT POSTGRES_PORT POSTGRES_DB POSTGRES_APP_USER POSTGRES_APP_PASSWORD POSTGRES_TLS_HOSTNAME POSTGRES_ALLOWED_CIDRS ENABLE_UFW)
read_env_value() {
  local key="$1"
  local line
  line="$(grep -m1 -E "^${key}=" "${ENV_FILE}" || true)"
  line="${line%$'\r'}"
  printf '%s' "${line#*=}"
}
for name in "${required[@]}"; do
  printf -v "${name}" '%s' "$(read_env_value "${name}")"
  if [[ -z "${!name:-}" || "${!name}" == *CHANGE_ME* ]]; then
    echo "Falta completar ${name}." >&2
    exit 1
  fi
done

[[ "${VPS_SSH_PORT}" =~ ^[0-9]{1,5}$ ]] || { echo "Puerto SSH invalido." >&2; exit 1; }
[[ "${POSTGRES_PORT}" =~ ^[0-9]{1,5}$ ]] || { echo "Puerto PostgreSQL invalido." >&2; exit 1; }
(( VPS_SSH_PORT >= 1 && VPS_SSH_PORT <= 65535 )) || { echo "Puerto SSH fuera de rango." >&2; exit 1; }
(( POSTGRES_PORT >= 1 && POSTGRES_PORT <= 65535 )) || { echo "Puerto PostgreSQL fuera de rango." >&2; exit 1; }
[[ "${POSTGRES_DB}" =~ ^[a-zA-Z_][a-zA-Z0-9_]{0,62}$ ]] || { echo "Nombre de base invalido." >&2; exit 1; }
[[ "${POSTGRES_APP_USER}" =~ ^[a-zA-Z_][a-zA-Z0-9_]{0,62}$ ]] || { echo "Nombre de rol invalido." >&2; exit 1; }
[[ "${POSTGRES_TLS_HOSTNAME}" =~ ^[a-zA-Z0-9.:-]+$ ]] || { echo "Nombre TLS invalido." >&2; exit 1; }
[[ "${POSTGRES_ALLOWED_CIDRS}" =~ ^[0-9a-fA-F:.,/\ ]+$ ]] || { echo "Lista CIDR invalida." >&2; exit 1; }
[[ "${ENABLE_UFW}" == "true" || "${ENABLE_UFW}" == "false" ]] || { echo "ENABLE_UFW invalido." >&2; exit 1; }
(( ${#POSTGRES_APP_PASSWORD} >= 24 )) || { echo "La contrasena PostgreSQL debe tener al menos 24 caracteres." >&2; exit 1; }

# 0 (o la ausencia en configuraciones creadas antes de estos limites) significa
# calculo automatico. Los valores explicitos son techos, nunca permisos para
# superar los limites de seguridad calculados desde el VPS.
optional_limits=(POSTGRES_MEMORY_MAX_MIB POSTGRES_CPU_QUOTA_PERCENT POSTGRES_MAX_CONNECTIONS POSTGRES_APP_CONNECTION_LIMIT POSTGRES_WORK_MEM_MIB POSTGRES_STATEMENT_TIMEOUT_MS POSTGRES_TEMP_FILE_LIMIT_MIB)
for name in "${optional_limits[@]}"; do
  printf -v "${name}" '%s' "$(read_env_value "${name}")"
  if [[ -z "${!name:-}" ]]; then
    printf -v "${name}" '%s' '0'
  fi
  [[ "${!name}" =~ ^[0-9]+$ ]] || { echo "${name} debe ser un entero no negativo." >&2; exit 1; }
done

if [[ ! -r /etc/os-release ]]; then
  echo "No se pudo identificar el sistema operativo." >&2
  exit 1
fi
# shellcheck disable=SC1091
source /etc/os-release
case "${ID:-}" in
  ubuntu|debian) ;;
  *) echo "Sistema no soportado: ${ID:-desconocido}. Solo Ubuntu/Debian." >&2; exit 1 ;;
esac

# No sobrescriba una instalacion ajena: una vez administrado por este runbook,
# el marcador permite ejecuciones idempotentes posteriores.
STATE_DIR=/etc/texerp-postgres
if command -v psql >/dev/null 2>&1 && [[ ! -f "${STATE_DIR}/managed" ]]; then
  echo "Se detecto PostgreSQL existente sin marcador TexERP; no se modifico. Revise config_file, hba_file y data_directory y adapte el runbook." >&2
  exit 1
fi

echo "[1/7] Instalando PostgreSQL y utilidades..."
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y postgresql postgresql-contrib openssl ufw
systemctl enable --now postgresql

CURRENT_POSTGRES_PORT="$(pg_lsclusters --no-header | awk '$4 == "online" {print $3; exit}')"
[[ "${CURRENT_POSTGRES_PORT}" =~ ^[0-9]+$ ]] || { echo "No se pudo identificar el puerto del cluster PostgreSQL activo." >&2; exit 1; }
HBA_FILE="$(sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -Atqc 'show hba_file')"
DATA_DIR="$(sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -Atqc 'show data_directory')"
AUTO_CONF="${DATA_DIR}/postgresql.auto.conf"
BACKUP_DIR="/var/backups/texerp-postgres/$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 700 "${BACKUP_DIR}"
cp -a "${HBA_FILE}" "${BACKUP_DIR}/pg_hba.conf"
AUTO_CONF_EXISTED=false
if [[ -f "${AUTO_CONF}" ]]; then
  cp -a "${AUTO_CONF}" "${BACKUP_DIR}/postgresql.auto.conf"
  AUTO_CONF_EXISTED=true
fi

echo "[preflight] Calculando presupuesto seguro de recursos..."
RAM_MIB="$(( $(awk '/MemTotal:/ {print $2}' /proc/meminfo) / 1024 ))"
VCPU="$(nproc)"
RESERVED_MIB=$(( (RAM_MIB * 35 + 99) / 100 ))
(( RESERVED_MIB < 1024 )) && RESERVED_MIB=1024
RAM_CAP_MIB=$(( RAM_MIB * 60 / 100 ))
AVAILABLE_AFTER_RESERVE_MIB=$(( RAM_MIB - RESERVED_MIB ))
(( AVAILABLE_AFTER_RESERVE_MIB < RAM_CAP_MIB )) && RAM_CAP_MIB="${AVAILABLE_AFTER_RESERVE_MIB}"
(( RAM_CAP_MIB >= 512 )) || { echo "RAM insuficiente: no se puede reservar SO/backend y dejar 512 MiB a PostgreSQL." >&2; exit 1; }

MEMORY_MAX_MIB="${RAM_CAP_MIB}"
if (( POSTGRES_MEMORY_MAX_MIB > 0 )); then
  (( POSTGRES_MEMORY_MAX_MIB <= RAM_CAP_MIB )) || { echo "POSTGRES_MEMORY_MAX_MIB excede el maximo seguro calculado (${RAM_CAP_MIB} MiB)." >&2; exit 1; }
  MEMORY_MAX_MIB="${POSTGRES_MEMORY_MAX_MIB}"
fi
MEMORY_HIGH_MIB=$(( MEMORY_MAX_MIB * 90 / 100 ))
CPU_CAP_PERCENT=$(( VCPU * 75 ))
CPU_QUOTA_PERCENT="${CPU_CAP_PERCENT}"
if (( POSTGRES_CPU_QUOTA_PERCENT > 0 )); then
  (( POSTGRES_CPU_QUOTA_PERCENT <= CPU_CAP_PERCENT )) || { echo "POSTGRES_CPU_QUOTA_PERCENT excede 75% de ${VCPU} vCPU (${CPU_CAP_PERCENT}%)." >&2; exit 1; }
  CPU_QUOTA_PERCENT="${POSTGRES_CPU_QUOTA_PERCENT}"
fi

AUTO_MAX_CONNECTIONS=$(( MEMORY_MAX_MIB / 32 ))
(( AUTO_MAX_CONNECTIONS < 30 )) && AUTO_MAX_CONNECTIONS=30
(( AUTO_MAX_CONNECTIONS > 100 )) && AUTO_MAX_CONNECTIONS=100
MAX_CONNECTIONS="${AUTO_MAX_CONNECTIONS}"
if (( POSTGRES_MAX_CONNECTIONS > 0 )); then
  (( POSTGRES_MAX_CONNECTIONS >= 15 && POSTGRES_MAX_CONNECTIONS <= AUTO_MAX_CONNECTIONS )) || { echo "POSTGRES_MAX_CONNECTIONS debe estar entre 15 y ${AUTO_MAX_CONNECTIONS}." >&2; exit 1; }
  MAX_CONNECTIONS="${POSTGRES_MAX_CONNECTIONS}"
fi
APP_CONNECTION_LIMIT=$(( MAX_CONNECTIONS - 10 ))
if (( POSTGRES_APP_CONNECTION_LIMIT > 0 )); then
  (( POSTGRES_APP_CONNECTION_LIMIT <= APP_CONNECTION_LIMIT )) || { echo "POSTGRES_APP_CONNECTION_LIMIT debe dejar 10 conexiones para administracion." >&2; exit 1; }
  APP_CONNECTION_LIMIT="${POSTGRES_APP_CONNECTION_LIMIT}"
fi
SHARED_BUFFERS_MIB=$(( MEMORY_MAX_MIB / 4 ))
(( SHARED_BUFFERS_MIB < 128 )) && SHARED_BUFFERS_MIB=128
EFFECTIVE_CACHE_MIB=$(( MEMORY_MAX_MIB / 2 ))
WORK_MEM_MIB=$(( (MEMORY_MAX_MIB - SHARED_BUFFERS_MIB) / MAX_CONNECTIONS / 4 ))
(( WORK_MEM_MIB < 4 )) && WORK_MEM_MIB=4
(( WORK_MEM_MIB > 16 )) && WORK_MEM_MIB=16
if (( POSTGRES_WORK_MEM_MIB > 0 )); then
  (( POSTGRES_WORK_MEM_MIB <= WORK_MEM_MIB )) || { echo "POSTGRES_WORK_MEM_MIB excede el limite seguro calculado (${WORK_MEM_MIB} MiB)." >&2; exit 1; }
  WORK_MEM_MIB="${POSTGRES_WORK_MEM_MIB}"
fi
MAINTENANCE_WORK_MEM_MIB=$(( MEMORY_MAX_MIB / 8 ))
(( MAINTENANCE_WORK_MEM_MIB < 64 )) && MAINTENANCE_WORK_MEM_MIB=64
(( MAINTENANCE_WORK_MEM_MIB > 256 )) && MAINTENANCE_WORK_MEM_MIB=256
TEMP_FILE_LIMIT_MIB="${POSTGRES_TEMP_FILE_LIMIT_MIB}"; (( TEMP_FILE_LIMIT_MIB == 0 )) && TEMP_FILE_LIMIT_MIB=512
STATEMENT_TIMEOUT_MS="${POSTGRES_STATEMENT_TIMEOUT_MS}"; (( STATEMENT_TIMEOUT_MS == 0 )) && STATEMENT_TIMEOUT_MS=60000
CPU_CORES_FOR_PG=$(( (CPU_QUOTA_PERCENT + 99) / 100 ))
(( CPU_CORES_FOR_PG < 1 )) && CPU_CORES_FOR_PG=1
MAX_WORKER_PROCESSES=$(( CPU_CORES_FOR_PG * 2 ))
(( MAX_WORKER_PROCESSES < 2 )) && MAX_WORKER_PROCESSES=2
(( MAX_WORKER_PROCESSES > 8 )) && MAX_WORKER_PROCESSES=8
MAX_PARALLEL_WORKERS="${CPU_CORES_FOR_PG}"; (( MAX_PARALLEL_WORKERS > 4 )) && MAX_PARALLEL_WORKERS=4
MAX_PARALLEL_PER_GATHER="${MAX_PARALLEL_WORKERS}"; (( MAX_PARALLEL_PER_GATHER > 2 )) && MAX_PARALLEL_PER_GATHER=2
TASKS_MAX=$(( MAX_CONNECTIONS + MAX_WORKER_PROCESSES + 32 ))

DATA_DISK_FREE_PERCENT="$(df -P "${DATA_DIR}" | awk 'NR==2 {gsub(/%/, "", $5); print 100-$5}')"
[[ "${DATA_DISK_FREE_PERCENT}" =~ ^[0-9]+$ ]] || { echo "No se pudo calcular espacio libre del volumen de datos." >&2; exit 1; }
(( DATA_DISK_FREE_PERCENT >= 20 )) || { echo "Bloqueado: el volumen de datos solo tiene ${DATA_DISK_FREE_PERCENT}% libre; se requieren al menos 20%." >&2; exit 1; }
printf 'Recursos: RAM=%s MiB, vCPU=%s; reserva SO/backend=%s MiB; presupuesto PostgreSQL=%s MiB y %s%% CPU. Disco datos libre=%s%%.\n' "${RAM_MIB}" "${VCPU}" "${RESERVED_MIB}" "${MEMORY_MAX_MIB}" "${CPU_QUOTA_PERCENT}" "${DATA_DISK_FREE_PERCENT}"

echo "[2/7] Generando o renovando certificados TLS..."
CA_KEY=/etc/ssl/private/texerp-postgres-ca.key
CA_CERT=/etc/ssl/certs/texerp-postgres-ca.crt
SERVER_KEY=/etc/ssl/private/texerp-postgres.key
SERVER_CERT=/etc/ssl/certs/texerp-postgres.crt
install -d -m 700 "${STATE_DIR}"

if [[ ! -s "${CA_KEY}" || ! -s "${CA_CERT}" ]]; then
  openssl req -x509 -newkey rsa:3072 -sha256 -days 3650 -nodes \
    -subj "/CN=TexERP PostgreSQL Local CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -keyout "${CA_KEY}" -out "${CA_CERT}"
fi

previous_tls_host="$(cat "${STATE_DIR}/tls-hostname" 2>/dev/null || true)"
if [[ ! -s "${SERVER_KEY}" || ! -s "${SERVER_CERT}" || "${previous_tls_host}" != "${POSTGRES_TLS_HOSTNAME}" ]]; then
  temp_dir="$(mktemp -d)"
  trap 'rm -rf -- "${temp_dir:-}"; rm -f -- "${ENV_FILE}"' EXIT
  if [[ "${POSTGRES_TLS_HOSTNAME}" == *:* || "${POSTGRES_TLS_HOSTNAME}" =~ ^[0-9]+(\.[0-9]+){3}$ ]]; then
    san="IP:${POSTGRES_TLS_HOSTNAME}"
  else
    san="DNS:${POSTGRES_TLS_HOSTNAME}"
  fi
  openssl req -new -newkey rsa:3072 -sha256 -nodes \
    -subj "/CN=${POSTGRES_TLS_HOSTNAME}" \
    -addext "subjectAltName=${san}" \
    -keyout "${SERVER_KEY}" -out "${temp_dir}/server.csr"
  cat > "${temp_dir}/extensions.cnf" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=${san}
EOF
  openssl x509 -req -in "${temp_dir}/server.csr" -CA "${CA_CERT}" -CAkey "${CA_KEY}" \
    -CAcreateserial -days 825 -sha256 -extfile "${temp_dir}/extensions.cnf" -out "${SERVER_CERT}"
  printf '%s\n' "${POSTGRES_TLS_HOSTNAME}" > "${STATE_DIR}/tls-hostname"
  rm -rf -- "${temp_dir}"
fi

chown root:root "${CA_KEY}" "${CA_CERT}"
chmod 600 "${CA_KEY}"
chmod 644 "${CA_CERT}"
chown root:postgres "${SERVER_KEY}"
chmod 640 "${SERVER_KEY}"
chown root:root "${SERVER_CERT}"
chmod 644 "${SERVER_CERT}"

echo "[3/7] Configurando escucha, TLS y SCRAM..."
sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -v ON_ERROR_STOP=1 <<SQL
ALTER SYSTEM SET listen_addresses = '*';
ALTER SYSTEM SET port = '${POSTGRES_PORT}';
ALTER SYSTEM SET password_encryption = 'scram-sha-256';
ALTER SYSTEM SET ssl = 'on';
ALTER SYSTEM SET ssl_cert_file = '${SERVER_CERT}';
ALTER SYSTEM SET ssl_key_file = '${SERVER_KEY}';
ALTER SYSTEM SET ssl_min_protocol_version = 'TLSv1.2';
ALTER SYSTEM SET max_connections = '${MAX_CONNECTIONS}';
ALTER SYSTEM SET shared_buffers = '${SHARED_BUFFERS_MIB}MB';
ALTER SYSTEM SET effective_cache_size = '${EFFECTIVE_CACHE_MIB}MB';
ALTER SYSTEM SET work_mem = '${WORK_MEM_MIB}MB';
ALTER SYSTEM SET maintenance_work_mem = '${MAINTENANCE_WORK_MEM_MIB}MB';
ALTER SYSTEM SET temp_file_limit = '${TEMP_FILE_LIMIT_MIB}MB';
ALTER SYSTEM SET statement_timeout = '${STATEMENT_TIMEOUT_MS}ms';
ALTER SYSTEM SET idle_in_transaction_session_timeout = '30000ms';
ALTER SYSTEM SET max_worker_processes = '${MAX_WORKER_PROCESSES}';
ALTER SYSTEM SET max_parallel_workers = '${MAX_PARALLEL_WORKERS}';
ALTER SYSTEM SET max_parallel_workers_per_gather = '${MAX_PARALLEL_PER_GATHER}';
ALTER SYSTEM SET log_rotation_age = '1d';
ALTER SYSTEM SET log_rotation_size = '0';
ALTER SYSTEM SET max_wal_size = '1GB';
ALTER SYSTEM SET checkpoint_timeout = '15min';
SQL

echo "[4/7] Creando el rol limitado y la base..."
sql_password="${POSTGRES_APP_PASSWORD//\'/\'\'}"
role_exists="$(sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -Atqc "select 1 from pg_roles where rolname = '${POSTGRES_APP_USER}'")"
if [[ "${role_exists}" != "1" ]]; then
  sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -v ON_ERROR_STOP=1 <<SQL
SET password_encryption = 'scram-sha-256';
CREATE ROLE "${POSTGRES_APP_USER}" LOGIN PASSWORD '${sql_password}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT ${APP_CONNECTION_LIMIT};
SQL
else
  sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -v ON_ERROR_STOP=1 <<SQL
SET password_encryption = 'scram-sha-256';
ALTER ROLE "${POSTGRES_APP_USER}" WITH LOGIN PASSWORD '${sql_password}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS CONNECTION LIMIT ${APP_CONNECTION_LIMIT};
SQL
fi

db_exists="$(sudo -u postgres psql -p "${CURRENT_POSTGRES_PORT}" -Atqc "select 1 from pg_database where datname = '${POSTGRES_DB}'")"
if [[ "${db_exists}" != "1" ]]; then
  sudo -u postgres createdb -p "${CURRENT_POSTGRES_PORT}" --owner="${POSTGRES_APP_USER}" --encoding=UTF8 "${POSTGRES_DB}"
else
  echo "La base ${POSTGRES_DB} ya existe; no se cambio su propietario."
fi

echo "[5/7] Aplicando reglas HBA administradas..."
managed_block="$(mktemp)"
clean_hba="$(mktemp)"
new_hba="$(mktemp)"
trap 'rm -f -- "${managed_block:-}" "${clean_hba:-}" "${new_hba:-}"; rm -f -- "${ENV_FILE}"' EXIT
{
  echo '# BEGIN TEXERP MANAGED BLOCK'
  IFS=',' read -ra cidrs <<< "${POSTGRES_ALLOWED_CIDRS}"
  for cidr in "${cidrs[@]}"; do
    cidr="${cidr//[[:space:]]/}"
    [[ -n "${cidr}" ]] || continue
    printf 'hostssl\t%s\t%s\t%s\tscram-sha-256\n' "${POSTGRES_DB}" "${POSTGRES_APP_USER}" "${cidr}"
  done
  printf 'hostssl\tall\t%s\t0.0.0.0/0\treject\n' "${POSTGRES_APP_USER}"
  printf 'hostssl\tall\t%s\t::/0\treject\n' "${POSTGRES_APP_USER}"
  printf 'hostnossl\tall\t%s\t0.0.0.0/0\treject\n' "${POSTGRES_APP_USER}"
  printf 'hostnossl\tall\t%s\t::/0\treject\n' "${POSTGRES_APP_USER}"
  echo '# END TEXERP MANAGED BLOCK'
} > "${managed_block}"

awk '
  /^# BEGIN TEXERP MANAGED BLOCK$/ { skip=1; next }
  /^# END TEXERP MANAGED BLOCK$/ { skip=0; next }
  !skip { print }
' "${HBA_FILE}" > "${clean_hba}"

awk -v block="${managed_block}" '
  !inserted && $0 ~ /^[[:space:]]*host/ {
    while ((getline line < block) > 0) print line
    close(block)
    inserted=1
  }
  { print }
  END {
    if (!inserted) {
      while ((getline line < block) > 0) print line
      close(block)
    }
  }
' "${clean_hba}" > "${new_hba}"
chown postgres:postgres "${new_hba}"
chmod 640 "${new_hba}"
install -o postgres -g postgres -m 640 "${new_hba}" "${HBA_FILE}"

echo "[6/7] Configurando UFW..."
if [[ "${ENABLE_UFW}" == "true" ]]; then
  ufw allow proto tcp to any port "${VPS_SSH_PORT}" comment 'TexERP SSH administration'
  firewall_state="${STATE_DIR}/ufw-postgres-rules"
  if [[ -f "${firewall_state}" ]]; then
    while IFS='|' read -r old_port old_cidr; do
      [[ -n "${old_port}" && -n "${old_cidr}" ]] || continue
      ufw --force delete allow proto tcp from "${old_cidr}" to any port "${old_port}" >/dev/null 2>&1 || true
    done < "${firewall_state}"
  fi
  : > "${firewall_state}"
  IFS=',' read -ra cidrs <<< "${POSTGRES_ALLOWED_CIDRS}"
  for cidr in "${cidrs[@]}"; do
    cidr="${cidr//[[:space:]]/}"
    [[ -n "${cidr}" ]] || continue
    ufw allow proto tcp from "${cidr}" to any port "${POSTGRES_PORT}" comment 'TexERP PostgreSQL TLS'
    printf '%s|%s\n' "${POSTGRES_PORT}" "${cidr}" >> "${firewall_state}"
  done
  ufw --force enable
else
  echo "ENABLE_UFW=false: no se modifico UFW. Verifique otro firewall antes de exponer el servicio."
fi

echo "[6.5/7] Aplicando limites duros systemd/cgroups..."
read -r PG_VERSION PG_CLUSTER < <(pg_lsclusters --no-header | awk '$4 == "online" {print $1, $2; exit}')
[[ -n "${PG_VERSION:-}" && -n "${PG_CLUSTER:-}" ]] || { echo "No se pudo identificar el cluster PostgreSQL activo." >&2; exit 1; }
PG_UNIT="postgresql@${PG_VERSION}-${PG_CLUSTER}.service"
systemctl cat "${PG_UNIT}" >/dev/null || { echo "No existe la unidad real ${PG_UNIT}." >&2; exit 1; }
OVERRIDE_DIR="/etc/systemd/system/${PG_UNIT}.d"
OVERRIDE_FILE="${OVERRIDE_DIR}/texerp-resource-limits.conf"
OVERRIDE_EXISTED=false
if [[ -f "${OVERRIDE_FILE}" ]]; then
  cp -a "${OVERRIDE_FILE}" "${BACKUP_DIR}/texerp-resource-limits.conf"
  OVERRIDE_EXISTED=true
fi
install -d -m 755 "${OVERRIDE_DIR}"
cat > "${OVERRIDE_FILE}" <<EOF
# Managed by TexERP OVH PostgreSQL deployer. Do not edit; use .env limits.
[Service]
MemoryHigh=${MEMORY_HIGH_MIB}M
MemoryMax=${MEMORY_MAX_MIB}M
CPUQuota=${CPU_QUOTA_PERCENT}%
TasksMax=${TASKS_MAX}
EOF
systemctl daemon-reload
systemd-analyze verify "${PG_UNIT}"

echo "[7/7] Reiniciando y validando..."
if ! systemctl restart "${PG_UNIT}"; then
  echo "Fallo el reinicio; restaurando HBA, postgresql.auto.conf y override systemd." >&2
  cp -a "${BACKUP_DIR}/pg_hba.conf" "${HBA_FILE}"
  if [[ "${AUTO_CONF_EXISTED}" == "true" ]]; then
    cp -a "${BACKUP_DIR}/postgresql.auto.conf" "${AUTO_CONF}"
  else
    rm -f -- "${AUTO_CONF}"
  fi
  if [[ "${OVERRIDE_EXISTED}" == "true" ]]; then
    cp -a "${BACKUP_DIR}/texerp-resource-limits.conf" "${OVERRIDE_FILE}"
  else
    rm -f -- "${OVERRIDE_FILE}"
  fi
  systemctl daemon-reload
  systemctl restart "${PG_UNIT}"
  exit 1
fi

systemctl show "${PG_UNIT}" -p MemoryHigh -p MemoryMax -p CPUQuotaPerSecUSec -p TasksMax
sudo -u postgres psql -p "${POSTGRES_PORT}" -v ON_ERROR_STOP=1 -Atqc "select current_setting('listen_addresses'), current_setting('port'), current_setting('ssl'), current_setting('ssl_min_protocol_version'), current_setting('password_encryption'), current_setting('max_connections'), current_setting('shared_buffers'), current_setting('effective_cache_size'), current_setting('work_mem'), current_setting('maintenance_work_mem'), current_setting('temp_file_limit'), current_setting('statement_timeout'), current_setting('idle_in_transaction_session_timeout'), current_setting('max_worker_processes'), current_setting('max_parallel_workers'), current_setting('max_parallel_workers_per_gather')"
hba_errors="$(sudo -u postgres psql -p "${POSTGRES_PORT}" -Atqc "select count(*) from pg_hba_file_rules where error is not null")"
[[ "${hba_errors}" == "0" ]] || { echo "PostgreSQL informa errores en pg_hba.conf." >&2; exit 1; }
sudo -u postgres psql -p "${POSTGRES_PORT}" -Atqc "select rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls, rolconnlimit from pg_roles where rolname = '${POSTGRES_APP_USER}'"
ss -lnt | grep -Eq ":${POSTGRES_PORT}[[:space:]]" || { echo "PostgreSQL no escucha en el puerto esperado." >&2; exit 1; }
[[ "${ENABLE_UFW}" != "true" ]] || ufw status verbose

# Prueba acotada: dos consultas locales, sin escribir datos, cada una con un
# timeout de tres segundos. No intenta acercarse al limite de memoria o disco.
echo 'Prueba breve de concurrencia local...'
for _ in 1 2; do
  sudo -u postgres psql -p "${POSTGRES_PORT}" -v ON_ERROR_STOP=1 -Atqc "set statement_timeout = '3s'; select count(*) from generate_series(1, 100000);" >/dev/null &
done
wait
touch "${STATE_DIR}/managed"

echo "Configuracion completada. CA para clientes: ${CA_CERT}"
echo "Copias de configuracion: ${BACKUP_DIR}"
