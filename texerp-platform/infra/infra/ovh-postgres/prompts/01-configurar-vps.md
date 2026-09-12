# Prompt: configurar PostgreSQL en el VPS

Usa `$ovh-postgres-vps` y trabaja con `texerp-platform/infra/ovh-postgres/.env`.

Valida el archivo sin mostrar secretos. Conectate al VPS por SSH, verifica la huella del host y confirma que es Ubuntu/Debian compatible. Muestra primero el plan y los cambios detectados. Luego ejecuta el despliegue preparado, valida PostgreSQL, TLS, SCRAM, el rol sin privilegios administrativos, el puerto y UFW. No desactives controles SSH, no publiques el superusuario `postgres` y no imprimas contraseñas. Si la Edge Network Firewall de OVHcloud bloquea el puerto, detente y dime exactamente que regla debo crear en el panel. Entrega evidencias sanitizadas y los pasos para instalar el certificado en un PC cliente.
