[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = $PSScriptRoot
$clientDir = Join-Path $root "cliente"
$backendDir = Join-Path $root "backend"

foreach ($command in @("npm", "java")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatório não encontrado no PATH: $command"
    }
}

$gradleWrapper = Join-Path $backendDir "gradlew.bat"
if (-not (Test-Path $gradleWrapper -PathType Leaf)) {
    throw "Gradle Wrapper do backend não encontrado: $gradleWrapper"
}

Write-Host "Executando os testes do backend..."
Push-Location $backendDir
try {
    & $gradleWrapper test
    if ($LASTEXITCODE -ne 0) { throw "Os testes do backend falharam." }
}
finally {
    Pop-Location
}

Write-Host "Instalando dependências do cliente pelo lockfile..."
Push-Location $clientDir
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci falhou." }

    Write-Host "Executando os testes do cliente..."
    & npm test
    if ($LASTEXITCODE -ne 0) { throw "Os testes do cliente falharam." }

    Write-Host "Validando o build do cliente..."
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "O build do cliente falhou." }
}
finally {
    Pop-Location
}

Write-Host "Testes e builds concluídos com sucesso."
