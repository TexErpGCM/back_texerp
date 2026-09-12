# Ejecutar desde la raíz del backend así:
#   . .\load-env.ps1
# El punto inicial es importante para cargar las variables en la terminal actual.

$envFile = Join-Path $PSScriptRoot "client.env"

if (-not (Test-Path $envFile)) {
    Write-Error "No existe client.env. Copia client.env.example a client.env y completa los valores reales."
    return
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
        }
    }
}

Write-Host "Variables de TexERP cargadas en esta terminal."
