[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env'),
    [switch]$PreflightOnly,
    [switch]$AcceptVerifiedHostKey
)

$ErrorActionPreference = 'Stop'
$config = & (Join-Path $PSScriptRoot 'Test-Config.ps1') -EnvFile $EnvFile -Quiet -PassThru

$ssh = Get-Command ssh -ErrorAction Stop
$scp = Get-Command scp -ErrorAction Stop
$target = "$($config['VPS_SSH_USER'])@$($config['VPS_HOST'])"
$scpTarget = if ($config['VPS_HOST'].Contains(':')) {
    "$($config['VPS_SSH_USER'])@[$($config['VPS_HOST'])]"
} else {
    $target
}
$port = $config['VPS_SSH_PORT']
$keyPath = $config['VPS_SSH_KEY_PATH']
$hasKey = $keyPath -and $keyPath -notmatch 'CHANGE_ME'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$remoteDir = "/tmp/texerp-postgres-deploy-$stamp"
$remoteScript = "$remoteDir/configure-postgres.sh"
$remoteEnv = "$remoteDir/.env"
$localScript = Join-Path $PSScriptRoot 'configure-postgres.sh'

$sshArgs = @('-p', $port)
$scpArgs = @('-P', $port)
if ($AcceptVerifiedHostKey) {
    # Solo se usa tras comparacion humana de la huella con la consola OVHcloud.
    # Acepta claves nuevas, pero rechaza una clave ya conocida que haya cambiado.
    $sshArgs += @('-o', 'StrictHostKeyChecking=accept-new')
    $scpArgs += @('-o', 'StrictHostKeyChecking=accept-new')
}
if ($hasKey) {
    $sshArgs += @('-i', $keyPath)
    $scpArgs += @('-i', $keyPath)
}

Write-Host "Plan: cargar archivos temporales, configurar PostgreSQL/TLS/UFW y validar en $target."
Write-Host 'La huella SSH se validara con la politica normal de OpenSSH.'

if ($WhatIfPreference) {
    Write-Host "What if: se configuraria PostgreSQL publico con TLS y SCRAM en $target."
    return
}

try {
    # No incluye secretos y se ejecuta antes de subir archivos o modificar el VPS.
    # sudo -v puede pedir la contrasena en la terminal, nunca en argumentos.
    Write-Host 'Preflight remoto de solo lectura (SO, recursos, PostgreSQL, puertos y firewall)...'
    $preflight = @(
        'id'
        'sudo -n true || sudo -v'
        '. /etc/os-release && printf "OS=%s %s\\n" "$ID" "$VERSION_ID"'
        'printf "ARCH="; uname -m; printf "VCPU="; nproc'
        'free -h; swapon --show || true; df -h /; df -i /'
        "systemctl list-units --type=service --all 'postgresql*' --no-pager || true"
        'sudo ss -lntp; sudo ufw status verbose || true; pg_lsclusters 2>/dev/null || true'
    ) -join "`n"
    & $ssh.Source @sshArgs $target $preflight
    if ($LASTEXITCODE -ne 0) { throw 'El preflight remoto fallo; no se aplicaron cambios.' }
    if ($PreflightOnly) {
        Write-Host 'Preflight remoto completado; no se aplicaron cambios.' -ForegroundColor Green
        return
    }

    Write-Host 'Preflight aprobado; creando directorio temporal remoto...'
    & $ssh.Source @sshArgs $target "install -d -m 700 '$remoteDir'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el directorio temporal remoto.' }

    Write-Host 'Copiando instalador remoto...'
    & $scp.Source @scpArgs $localScript "${scpTarget}:$remoteScript"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el instalador remoto.' }

    Write-Host 'Copiando configuracion temporal protegida...'
    & $scp.Source @scpArgs $EnvFile "${scpTarget}:$remoteEnv"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar temporalmente la configuracion.' }

    & $ssh.Source @sshArgs $target "chmod 700 '$remoteScript'; chmod 600 '$remoteEnv'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron asegurar los permisos temporales.' }

    Write-Host 'Ejecutando instalador remoto...'
    # No se asigna TTY: psql no abre un paginador en automatizaciones y sudo
    # ya fue verificado durante el preflight.
    & $ssh.Source @sshArgs $target "sudo bash '$remoteScript' '$remoteEnv'"
    if ($LASTEXITCODE -ne 0) { throw "La configuracion remota termino con codigo $LASTEXITCODE." }
}
finally {
    & $ssh.Source @sshArgs $target "rm -rf '$remoteDir'" 2>$null
}

Write-Host 'Despliegue remoto completado. Instale el certificado CA y pruebe desde un PC externo.' -ForegroundColor Green
