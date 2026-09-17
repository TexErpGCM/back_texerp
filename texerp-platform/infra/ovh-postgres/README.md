# PostgreSQL publico en un VPS OVHcloud

Este directorio contiene el procedimiento reproducible para instalar PostgreSQL en un VPS Ubuntu/Debian, crear una base y un rol de aplicacion, exigir TLS y SCRAM-SHA-256, y abrir el puerto a los CIDR autorizados.

> **Advertencia:** `0.0.0.0/0,::/0` permite intentos de conexion desde todo Internet. La contrasena no es una barrera suficiente por si sola. Este procedimiento reduce el riesgo con TLS, SCRAM, un rol sin privilegios administrativos y UFW, pero una VPN o una lista de IPs `/32` sigue siendo preferible.

## Archivos

- `.env`: credenciales reales, ignoradas por Git.
- `.env.example`: contrato de configuracion sin secretos.
- `scripts/Test-Config.ps1`: valida la configuracion local sin imprimir secretos.
- `scripts/Deploy-OvhPostgres.ps1`: carga temporalmente el instalador y ejecuta la configuracion por SSH.
- `scripts/configure-postgres.sh`: instalador remoto idempotente para Ubuntu/Debian.
- `prompts/`: prompts preparados para configurar, auditar o conectar clientes.
- `../../../.agents/skills/ovh-postgres-vps/`: skill reutilizable por Codex.

## 1. Requisitos

1. VPS OVHcloud con Ubuntu 22.04/24.04 o Debian 12/13 y una IP publica.
2. Usuario SSH con `sudo` y acceso por llave recomendado.
3. Cliente OpenSSH (`ssh` y `scp`) en el PC desde el que se despliega.
4. Si la **Edge Network Firewall** de OVHcloud esta activa, reglas TCP de entrada para el puerto SSH y `POSTGRES_PORT`. Mantenga una regla `ESTABLISHED` y el rechazo final segun la politica del panel.
5. Copia o snapshot reciente del VPS si ya aloja otros servicios.

## 2. Completar y validar `.env`

Edite `.env`. Use una contrasena aleatoria de al menos 24 caracteres. `VPS_SSH_PASSWORD` puede quedar vacio: OpenSSH la pedira interactivamente si no se usa llave. La automatizacion nunca lee ni pasa esa contrasena como argumento.

Desde PowerShell:

```powershell
Set-Location .\texerp-platform\infra\ovh-postgres
.\scripts\Test-Config.ps1
```

Antes de la primera conexion compare la huella SSH mostrada por OpenSSH con la huella disponible en la consola de OVHcloud. El script no desactiva `StrictHostKeyChecking`.

## 3. Desplegar

La ejecucion instala paquetes y modifica PostgreSQL/UFW en el VPS. Revise primero el plan:

```powershell
.\scripts\Deploy-OvhPostgres.ps1 -WhatIf
```

Cuando este conforme:

```powershell
.\scripts\Deploy-OvhPostgres.ps1
```

El script hace primero un preflight de solo lectura por SSH (identidad, SO, RAM, vCPU, swap, disco/inodos, servicios, puertos, UFW y clúster PostgreSQL). Solo después copia `.env` a un directorio temporal del VPS con permisos `600`, ejecuta el instalador con `sudo` y elimina los archivos temporales al terminar. PostgreSQL conserva copias con marca de tiempo de `postgresql.auto.conf`, `pg_hba.conf` y el override systemd antes de modificarlos. Una instalación PostgreSQL preexistente que no tenga el marcador del runbook no se altera automáticamente.

## 4. Configuracion OVHcloud que no puede hacerse por SSH

Si la Edge Network Firewall esta habilitada para la IP del VPS, abra en el panel:

- TCP entrante al puerto SSH desde su IP administrativa, o temporalmente desde cualquier origen si su IP cambia.
- TCP entrante a `POSTGRES_PORT` desde los mismos CIDR de `POSTGRES_ALLOWED_CIDRS`.
- Trafico TCP `ESTABLISHED` segun la guia de OVHcloud.
- Rechazo final del resto del trafico si esa es la politica de la IP.

La regla del panel y UFW deben permitir el flujo; si cualquiera lo bloquea, el cliente no conectara.

## 5. Instalar el certificado en un cliente

El despliegue genera una CA local y firma un certificado servidor con SAN para `POSTGRES_TLS_HOSTNAME`. Descargue solo el certificado publico de la CA por SSH mediante un canal cuya huella ya verifico:

```powershell
New-Item -ItemType Directory -Force "$env:APPDATA\postgresql" | Out-Null
scp -P 22 ubuntu@VPS_HOST:/etc/ssl/certs/texerp-postgres-ca.crt "$env:APPDATA\postgresql\root.crt"
```

En Linux/macOS, use `~/.postgresql/root.crt` con permisos apropiados. Si ya tiene un `root.crt`, no lo sobrescriba: agregue el certificado o indique una ruta dedicada con `sslrootcert`.

Prueba con `psql`:

```powershell
$env:PGSSLMODE = 'verify-full'
$env:PGSSLROOTCERT = "$env:APPDATA\postgresql\root.crt"
psql --host=POSTGRES_TLS_HOSTNAME --port=5432 --dbname=texerp --username=texerp_app
```

`sslmode=require` cifra el trafico, pero no verifica plenamente la identidad del servidor. Para clientes estables use `verify-full` y el certificado descargado.

## 6. Verificacion esperada

En el VPS:

```bash
sudo -u postgres psql -Atqc "show listen_addresses; show ssl; show password_encryption;"
sudo -u postgres psql -Atqc "select rolname, rolsuper, rolcreatedb, rolcreaterole from pg_roles where rolname = 'texerp_app';"
sudo ss -lntp | grep ':5432'
sudo ufw status verbose
```

Desde un PC externo, la conexion con contraseña correcta y `verify-full` debe funcionar; una contraseña incorrecta y una conexion sin TLS deben fallar.

## 7. Operacion y limites

- El instalador reserva para el SO, SSH y backend el mayor de 1 GiB o 35 % de la RAM. PostgreSQL no recibe más de 60 % de la RAM ni 75 % de las vCPU (en systemd, `100%` equivale aproximadamente a un núcleo). El volumen que contiene los datos debe conservar como mínimo 20 % libre.
- Con los valores `POSTGRES_*` de límites en `.env`, `0` significa cálculo automático. Los valores explícitos son techos inferiores: el instalador rechaza cualquiera que supere el presupuesto seguro del VPS. Ajusta `max_connections`, buffers, memoria por operación, temporales, timeouts y paralelismo, y deja conexiones para administración fuera del límite del rol.
- El límite duro se escribe en la unidad real `postgresql@<version>-<cluster>.service`, con `MemoryHigh`, `MemoryMax`, `CPUQuota` y `TasksMax`; no se aplica a la unidad meta `postgresql.service`. El despliegue ejecuta `systemd-analyze verify` y muestra los valores efectivos de systemd antes de declararse correcto.
- PostgreSQL no tiene una cuota total nativa por base. El procedimiento limita temporales, WAL/checkpoints y exige capacidad libre, pero no afirma crear una cuota de disco si no existe volumen o quota del sistema operativo.
- El instalador no habilita copias de seguridad, monitoreo ni alta disponibilidad. Configure backups antes de cargar datos importantes.
- Para revocar acceso publico, cambie `POSTGRES_ALLOWED_CIDRS` por IPs `/32`, vuelva a desplegar y replique los mismos CIDR en la Edge Network Firewall.
- No use el rol `postgres` desde Internet. El archivo HBA generado solo publica la base y el rol de aplicacion indicados.
- Rote `POSTGRES_APP_PASSWORD` si el `.env` fue compartido o expuesto y vuelva a ejecutar el despliegue.

## 8. Validacion externa obligatoria

Una vez descargada la CA, desde un equipo que esté incluido en `POSTGRES_ALLOWED_CIDRS` confirme una conexión con `sslmode=verify-full`. Mantenga esa sesión abierta y, desde el VPS, confirme que `pg_stat_ssl` muestra SSL activo para el rol de aplicación. Después pruebe de forma interactiva que una contraseña errónea y `sslmode=disable` fallan. No pase la contraseña por argumentos ni la guarde en el historial del terminal.

Si hay una **Edge Network Firewall** activa en OVHcloud, el resultado no es exitoso hasta que exista una regla TCP de entrada al `POSTGRES_PORT` desde cada CIDR de `POSTGRES_ALLOWED_CIDRS`, además de la regla SSH administrativa y de tráfico establecido que corresponda.

## Fuentes tecnicas

- [PostgreSQL: conexiones TCP protegidas con TLS](https://www.postgresql.org/docs/current/ssl-tcp.html)
- [PostgreSQL: reglas `pg_hba.conf`](https://www.postgresql.org/docs/current/auth-pg-hba-conf.html)
- [PostgreSQL: autenticacion por contraseña y SCRAM](https://www.postgresql.org/docs/current/auth-password.html)
- [Ubuntu Server: instalar y configurar PostgreSQL](https://documentation.ubuntu.com/server/how-to/databases/install-postgresql/index.html)
- [Ubuntu Server: firewall UFW](https://ubuntu.com/server/docs/security-firewall/)
- [OVHcloud: Network Security Dashboard](https://help.ovhcloud.com/csm/en-au-network-security-dashboard?id=kb_article_view&sysparm_article=KB0060692)
