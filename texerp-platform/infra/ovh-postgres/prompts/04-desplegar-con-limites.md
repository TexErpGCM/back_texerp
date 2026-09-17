# Prompt para GPT-5.6 Terra (razonamiento alto)

Actua como administrador senior de Linux y PostgreSQL. Usa el skill `$ovh-postgres-vps` y los archivos de `texerp-platform/infra/ovh-postgres/` para desplegar en mi VPS OVHcloud la base PostgreSQL descrita en `.env`, exponerla a los CIDR configurados y limitar de forma medible los recursos que puede consumir. Trabaja hasta completar y validar el resultado; no te limites a entregarme instrucciones.

## Objetivo obligatorio

- Acceder por SSH usando `.env` sin mostrar, copiar a logs ni incluir secretos en argumentos de procesos.
- Instalar o configurar PostgreSQL en Ubuntu/Debian de forma reproducible e idempotente.
- Crear solamente la base y el rol de aplicacion indicados; el rol debe quedar sin privilegios administrativos.
- Exigir TLS con verificacion de identidad y autenticacion `scram-sha-256`.
- Aplicar `POSTGRES_ALLOWED_CIDRS` en `pg_hba.conf` y UFW, conservando primero el acceso SSH.
- Limitar memoria, CPU, conexiones, paralelismo, consultas largas, transacciones inactivas y archivos temporales de PostgreSQL sin impedir el funcionamiento normal del sistema operativo y del backend.

## Procedimiento

1. Valida `.env` sin imprimir valores sensibles. Si falta una credencial imprescindible, detente e indica unicamente el nombre de la variable faltante.
2. Verifica la huella SSH mediante la politica normal de OpenSSH. No uses `StrictHostKeyChecking=no`.
3. Antes de cambiar el VPS, realiza un preflight de solo lectura: distribucion y version, CPU y RAM totales, swap, espacio e inodos, servicios activos, puertos, UFW, version/cluster/unidad real de PostgreSQL y consumo actual. Detecta si el VPS comparte recursos con la aplicacion.
4. Calcula un presupuesto para PostgreSQL a partir de los recursos reales. Reserva para SO, SSH y backend al menos el mayor valor entre 1 GiB y 35 % de la RAM. No asignes a PostgreSQL mas del 60 % de la RAM ni mas del 75 % de la capacidad total de CPU, salvo que `.env` defina limites inferiores. Explica brevemente los valores elegidos sin revelar secretos.
5. Implementa los limites duros sobre la unidad real del cluster, por ejemplo `postgresql@<version>-<cluster>.service`, no sobre la unidad meta `postgresql.service`. Usa un override systemd administrado con `MemoryHigh`, `MemoryMax`, `CPUQuota` y `TasksMax`. Recuerda que `CPUQuota=100%` equivale aproximadamente a un nucleo; calcula el porcentaje respecto al numero real de vCPU. Evita un `MemoryMax` tan bajo que provoque reinicios por OOM.
6. Ajusta PostgreSQL dentro de ese presupuesto. Como minimo revisa y fija de manera coherente: `max_connections`, `shared_buffers`, `effective_cache_size`, `work_mem`, `maintenance_work_mem`, `temp_file_limit`, `statement_timeout`, `idle_in_transaction_session_timeout`, `max_worker_processes`, `max_parallel_workers` y `max_parallel_workers_per_gather`. Considera que `work_mem` puede multiplicarse por conexion y operacion; no lo calcules como si fuera una reserva global unica.
7. Limita tambien el rol y la base de aplicacion con `CONNECTION LIMIT` compatibles con `max_connections`, dejando conexiones reservadas para administracion. No habilites `trust` ni acceso remoto para `postgres`.
8. Controla crecimiento operativo con rotacion de logs y valores razonables de WAL/checkpoints. PostgreSQL no ofrece una cuota total de disco por base: no afirmes haber creado una si no existe un volumen o quota real. Conserva al menos 20 % del disco libre y, si no es posible, reportalo como bloqueo antes de cargar datos.
9. Integra los nuevos parametros de limites en `.env.example`, `.env`, validadores, instalador y documentacion del proyecto para que un segundo despliegue produzca el mismo estado. No sobrescribas cambios ajenos. Realiza copias con marca de tiempo antes de modificar PostgreSQL o systemd y prepara rollback.
10. Ejecuta el despliegue. Si la Edge Network Firewall de OVHcloud bloquea el puerto, no declares exito: especifica la regla exacta pendiente en el panel.

## Validacion obligatoria

- Ejecuta `systemd-analyze verify` sobre el override o una validacion equivalente antes de reiniciar.
- Reinicia el cluster solo despues de validar configuracion y conserva la sesion SSH hasta confirmar que vuelve a estar operativo.
- Comprueba `systemctl show` para `MemoryHigh`, `MemoryMax`, `CPUQuotaPerSecUSec` y `TasksMax` en la unidad efectiva.
- Comprueba mediante SQL todos los parametros configurados, atributos y limites del rol/base, reglas HBA sin errores y una sesion TLS visible en `pg_stat_ssl`.
- Verifica escucha y UFW. Desde un PC externo prueba que `sslmode=verify-full` funciona con la CA, que una contraseña incorrecta falla y que `sslmode=disable` es rechazado.
- Realiza una prueba breve y acotada de consumo/concurrencia que no ponga en riesgo el VPS. No provoques deliberadamente OOM ni llenes el disco.

## Entrega

Informa: recursos detectados; presupuesto asignado; valores PostgreSQL y limites systemd efectivos; archivos modificados y backups; pruebas realizadas y resultado; cadena de conexion redactada sin contraseña; ruta del certificado CA; acciones pendientes en OVHcloud o en clientes. No declares la tarea completada si falta la prueba externa o si algun limite solo fue escrito pero no esta activo.
