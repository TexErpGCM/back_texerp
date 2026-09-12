[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env'),
    [switch]$Quiet,
    [switch]$PassThru
)

$ErrorActionPreference = 'Stop'

function Read-DotEnv {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "No existe el archivo de configuracion: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { throw "Linea .env invalida (sin mostrar su contenido)." }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

$config = Read-DotEnv -Path $EnvFile
$required = @(
    'VPS_HOST', 'VPS_SSH_PORT', 'VPS_SSH_USER',
    'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_APP_USER',
    'POSTGRES_APP_PASSWORD', 'POSTGRES_TLS_HOSTNAME',
    'POSTGRES_ALLOWED_CIDRS', 'ENABLE_UFW'
)

$errors = [System.Collections.Generic.List[string]]::new()
foreach ($name in $required) {
    if (-not $config.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($config[$name])) {
        $errors.Add("Falta $name.")
    } elseif ($config[$name] -match 'CHANGE_ME') {
        $errors.Add("$name conserva un marcador CHANGE_ME.")
    }
}

if ($config['VPS_SSH_PORT'] -notmatch '^\d{1,5}$' -or [int]$config['VPS_SSH_PORT'] -gt 65535) {
    $errors.Add('VPS_SSH_PORT debe ser un puerto valido.')
}
if ($config['POSTGRES_PORT'] -notmatch '^\d{1,5}$' -or [int]$config['POSTGRES_PORT'] -gt 65535) {
    $errors.Add('POSTGRES_PORT debe ser un puerto valido.')
}
if ($config['VPS_HOST'] -notmatch '^[a-zA-Z0-9.:-]+$') {
    $errors.Add('VPS_HOST debe ser una IP o un nombre DNS simple.')
}
if ($config['VPS_SSH_USER'] -notmatch '^[a-zA-Z_][a-zA-Z0-9_.-]{0,31}$') {
    $errors.Add('VPS_SSH_USER contiene caracteres no permitidos.')
}
if ($config['POSTGRES_DB'] -notmatch '^[a-zA-Z_][a-zA-Z0-9_]{0,62}$') {
    $errors.Add('POSTGRES_DB debe ser un identificador PostgreSQL simple de hasta 63 caracteres.')
}
if ($config['POSTGRES_APP_USER'] -notmatch '^[a-zA-Z_][a-zA-Z0-9_]{0,62}$') {
    $errors.Add('POSTGRES_APP_USER debe ser un identificador PostgreSQL simple de hasta 63 caracteres.')
}
if ($config['POSTGRES_APP_PASSWORD'].Length -lt 24) {
    $errors.Add('POSTGRES_APP_PASSWORD debe tener al menos 24 caracteres.')
}
if ($config['POSTGRES_ALLOWED_CIDRS'] -notmatch '^[0-9a-fA-F:.,/ ]+$') {
    $errors.Add('POSTGRES_ALLOWED_CIDRS contiene caracteres no permitidos.')
}
if ($config['ENABLE_UFW'] -notin @('true', 'false')) {
    $errors.Add('ENABLE_UFW solo admite true o false.')
}

# Los limites son opcionales: 0 significa que el instalador calcula un valor
# conservador a partir de los recursos reales del VPS. Cualquier valor
# explicito solo puede reducir el presupuesto automatico.
$optionalPositiveIntegers = @(
    'POSTGRES_MEMORY_MAX_MIB', 'POSTGRES_CPU_QUOTA_PERCENT',
    'POSTGRES_MAX_CONNECTIONS', 'POSTGRES_APP_CONNECTION_LIMIT',
    'POSTGRES_WORK_MEM_MIB', 'POSTGRES_STATEMENT_TIMEOUT_MS',
    'POSTGRES_TEMP_FILE_LIMIT_MIB'
)
foreach ($name in $optionalPositiveIntegers) {
    if ($config.ContainsKey($name) -and $config[$name] -and $config[$name] -notmatch '^(0|[1-9]\d*)$') {
        $errors.Add("$name debe ser un entero positivo o 0 para calculo automatico.")
    }
}

$keyPath = $config['VPS_SSH_KEY_PATH']
$hasKey = $keyPath -and $keyPath -notmatch 'CHANGE_ME'
$hasPassword = $config['VPS_SSH_PASSWORD']
if (-not $hasKey -and -not $hasPassword) {
    $errors.Add('Indique VPS_SSH_KEY_PATH o VPS_SSH_PASSWORD (esta ultima se pedira interactivamente).')
}
if ($hasKey -and -not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
    $errors.Add('VPS_SSH_KEY_PATH no apunta a un archivo existente.')
}

if ($errors.Count -gt 0) {
    foreach ($item in $errors) { Write-Host "ERROR: $item" -ForegroundColor Red }
    throw "La configuracion tiene $($errors.Count) error(es). No se mostro ningun secreto."
}

if (-not $Quiet) {
    $public = $config['POSTGRES_ALLOWED_CIDRS'] -match '(^|,)\s*(0\.0\.0\.0/0|::/0)\s*(,|$)'
    Write-Host 'Configuracion valida; no se mostraron secretos.' -ForegroundColor Green
    Write-Host "Destino SSH: $($config['VPS_SSH_USER'])@$($config['VPS_HOST']):$($config['VPS_SSH_PORT'])"
    Write-Host "PostgreSQL: puerto $($config['POSTGRES_PORT']); base $($config['POSTGRES_DB']); rol $($config['POSTGRES_APP_USER'])"
    if ($public) {
        Write-Warning 'El acceso incluye Internet completo (0.0.0.0/0 o ::/0).'
    }
}

if ($PassThru) {
    return $config
}
