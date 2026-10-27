[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 3001
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = $PSScriptRoot
$clientDir = Join-Path $root "cliente"
$backendDir = Join-Path $root "backend"
$clientDist = Join-Path $clientDir "dist"
$catalogPath = Join-Path $backendDir "src\main\resources\catalogo.json"
$jwtSecret = $env:JWT_SEGREDO

if ([string]::IsNullOrWhiteSpace($jwtSecret) -or
    [System.Text.Encoding]::UTF8.GetByteCount($jwtSecret) -lt 32) {
    throw "Defina JWT_SEGREDO com pelo menos 32 bytes antes de iniciar o servidor."
}

foreach ($command in @("npm", "java")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatório não encontrado no PATH: $command"
    }
}

$gradleWrapper = Join-Path $backendDir "gradlew.bat"
if (-not (Test-Path $gradleWrapper -PathType Leaf)) {
    throw "Gradle Wrapper do backend não encontrado: $gradleWrapper"
}

$publicUrl = $env:URL_PUBLICA
if ([string]::IsNullOrWhiteSpace($publicUrl)) {
    $publicUrl = "http://localhost:$Port"
}

$publicUri = $null
if (-not [Uri]::TryCreate($publicUrl, [UriKind]::Absolute, [ref]$publicUri) -or
    $publicUri.Scheme -notin @("http", "https")) {
    throw "URL_PUBLICA deve ser uma URL absoluta http:// ou https://."
}

Write-Host "Instalando dependências e testando o cliente..."
Push-Location $clientDir
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci falhou." }

    & npm test
    if ($LASTEXITCODE -ne 0) { throw "Os testes do cliente falharam." }

    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "O build do cliente falhou." }
}
finally {
    Pop-Location
}

Write-Host "Testando e empacotando o backend..."
Push-Location $backendDir
try {
    & $gradleWrapper test installDist
    if ($LASTEXITCODE -ne 0) { throw "Os testes ou o empacotamento do backend falharam." }
}
finally {
    Pop-Location
}

$libraryDir = Join-Path $backendDir "build\install\jogo-backend\lib"
$libraries = @(Get-ChildItem $libraryDir -Filter "*.jar" -File -ErrorAction SilentlyContinue)
if ($libraries.Count -eq 0) {
    throw "Bibliotecas da distribuição do backend não foram encontradas: $libraryDir"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java não encontrado no PATH."
}

$javaExe = $null
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $javaFromHome = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $javaFromHome -PathType Leaf) {
        $javaExe = $javaFromHome
    }
}
if ($null -eq $javaExe) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javaExe = $javaCommand.Source
    }
}
if ($null -eq $javaExe) {
    throw "Java não encontrado no PATH nem em JAVA_HOME."
}

$javaHomeDir = Split-Path (Split-Path $javaExe -Parent) -Parent
$javaReleaseFile = Join-Path $javaHomeDir "release"
if (-not (Test-Path $javaReleaseFile -PathType Leaf)) {
    throw "Não foi possível verificar a versão do JDK em $javaHomeDir."
}
$javaRelease = Get-Content $javaReleaseFile -Raw
if ($javaRelease -notmatch '(?m)^JAVA_VERSION="([^"]+)"') {
    throw "Não foi possível determinar a versão do JDK em $javaHomeDir."
}
$javaVersion = $matches[1]
if ($javaVersion -match '^1\.(\d+)') {
    $javaMajorVersion = [int]$matches[1]
}
else {
    $javaMajorVersion = [int]($javaVersion.Split(".")[0])
}
if ($javaMajorVersion -lt 17) {
    throw "O servidor requer Java 17 ou superior. Java selecionado: $javaVersion."
}

$classpath = ($libraries | ForEach-Object { $_.FullName.Replace("\", "/") }) -join ";"
$argumentsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("jogo-production-{0}.args" -f [Guid]::NewGuid())
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($argumentsFile, @("-cp", "`"$classpath`"", "jogo.ApplicationKt"), $utf8WithoutBom)

$env:PORTA = $Port.ToString()
$env:HOST = "127.0.0.1"
$env:URL_PUBLICA = $publicUri.AbsoluteUri.TrimEnd("/")
$env:CATALOGO_PATH = $catalogPath
$env:CLIENTE_DIST_PATH = $clientDist
if ([string]::IsNullOrWhiteSpace($env:ORIGENS_PERMITIDAS)) {
    $env:ORIGENS_PERMITIDAS = $publicUri.Authority
}

Write-Host "Aplicação escutando na porta $Port; URL pública configurada: $($env:URL_PUBLICA)"
Write-Host "Para produção pública, configure URL_PUBLICA com o domínio HTTPS e use um proxy TLS."
Write-Host "Pressione Ctrl+C para encerrar."
Push-Location $backendDir
try {
    & $javaExe "@$argumentsFile"
    if ($LASTEXITCODE -ne 0) {
        throw "O servidor encerrou com código $LASTEXITCODE."
    }
}
finally {
    Pop-Location
    if (Test-Path $argumentsFile) {
        Remove-Item -LiteralPath $argumentsFile -Force
    }
}
