[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 3001,
    [string]$PublicUrl,
    [string]$AllowedOrigins,
    [switch]$SkipClientSetup
)

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

$publicUrlValue = $PublicUrl
if ([string]::IsNullOrWhiteSpace($publicUrlValue)) {
    $publicUrlValue = "http://localhost:$Port"
}
$publicUri = $null
if (-not [Uri]::TryCreate($publicUrlValue, [UriKind]::Absolute, [ref]$publicUri) -or
    $publicUri.Scheme -notin @("http", "https") -or
    -not [string]::IsNullOrEmpty($publicUri.UserInfo) -or
    $publicUri.AbsolutePath -ne "/" -or
    -not [string]::IsNullOrEmpty($publicUri.Query) -or
    -not [string]::IsNullOrEmpty($publicUri.Fragment)) {
    throw "PublicUrl deve ser uma origem absoluta http:// ou https://, sem caminho, credenciais, query ou fragmento."
}
$publicUrlValue = $publicUri.GetLeftPart([UriPartial]::Authority)

$origensValor = $AllowedOrigins
if ([string]::IsNullOrWhiteSpace($origensValor)) {
    $origensLista = @("localhost", "localhost:$Port", "127.0.0.1:$Port", $publicUri.Authority) |
        Select-Object -Unique
    $origensValor = $origensLista -join ","
}

$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $env:JWT_SEGREDO = [Convert]::ToBase64String($bytes)
}
finally {
    $rng.Dispose()
}

$env:HOST = "127.0.0.1"
$env:PORTA = $Port.ToString()
$env:URL_PUBLICA = $publicUrlValue
$env:ORIGENS_PERMITIDAS = $origensValor
$env:CATALOGO_PATH = Join-Path $backendDir "src\main\resources\catalogo.json"
$env:CLIENTE_DIST_PATH = Join-Path $clientDir "dist"
$env:MP_ACCESS_TOKEN = ""

if ($SkipClientSetup) {
    if (-not (Test-Path (Join-Path $clientDir "dist\index.html") -PathType Leaf)) {
        throw "Build do cliente não encontrado. Execute run-tests.ps1 ou remova -SkipClientSetup."
    }
}
else {
    Write-Host "Instalando dependências do cliente..."
    Push-Location $clientDir
    try {
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw "npm ci falhou." }

        Write-Host "Gerando os arquivos estáticos..."
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "O build do cliente falhou." }
    }
    finally {
        Pop-Location
    }
}

Write-Host "Iniciando backend de desenvolvimento em http://127.0.0.1:$Port"
Write-Host "URL pública da aplicação: $publicUrlValue"
Write-Host "O segredo JWT é temporário e muda a cada execução. Checkout do Mercado Pago está desativado."
Write-Host "Pressione Ctrl+C para encerrar."
Push-Location $backendDir
try {
    & $gradleWrapper run
    if ($LASTEXITCODE -ne 0) {
        throw "O servidor de desenvolvimento encerrou com código $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
