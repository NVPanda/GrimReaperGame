[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 3001,
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = $PSScriptRoot
$testScript = Join-Path $root "run-tests.ps1"
$developmentScript = Join-Path $root "run-development.ps1"
$baseUrl = "http://localhost:$Port"
$listener = $null

try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
}
catch {
    throw "A porta $Port já está em uso ou não está disponível. Feche o serviço atual ou escolha outra porta."
}
finally {
    if ($null -ne $listener) {
        $listener.Stop()
    }
}

Write-Host "Executando testes e builds antes de iniciar o servidor..."
& $testScript
if ($LASTEXITCODE -ne 0) {
    throw "O ambiente de testes falhou; o servidor não será iniciado."
}

$powershell = (Get-Process -Id $PID).Path
$childArguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$developmentScript`"",
    "-Port", $Port.ToString(),
    "-SkipClientSetup"
)

Write-Host "Iniciando o ambiente local..."
$server = Start-Process -FilePath $powershell `
    -ArgumentList $childArguments `
    -WorkingDirectory $root `
    -PassThru

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    $server.Refresh()
    if ($server.HasExited) {
        throw "O processo do servidor encerrou antes de ficar pronto (código $($server.ExitCode))."
    }

    try {
        $response = Invoke-WebRequest "$baseUrl/api/catalogo" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    }
    catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    throw "O servidor não respondeu em $baseUrl após $StartupTimeoutSeconds segundos. Verifique o console do servidor."
}

Write-Host "Servidor pronto em $baseUrl"
Start-Process -FilePath $baseUrl
Write-Host "O servidor continua no console separado. Feche esse console para pará-lo."
