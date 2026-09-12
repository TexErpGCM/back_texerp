# Prompt: validar conexion desde un PC

Usa `$ovh-postgres-vps` para validar un cliente externo sin cambiar el servidor. Instala o ubica de forma segura el certificado CA descargado por SSH, construye una conexion con `sslmode=verify-full` y prueba la base y el rol definidos en `.env`. Confirma mediante `pg_stat_ssl` que la sesion usa TLS. Comprueba tambien que una contraseña incorrecta y `sslmode=disable` sean rechazados. No muestres la contraseña ni una URL que la contenga; redacta cualquier salida sensible.
