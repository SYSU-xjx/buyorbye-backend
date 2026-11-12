param(
    [ValidateSet('run', 'verify')]
    [string]$Action = 'run',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$candidates = @($JdkHome)
$candidates += @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
$selectedJdk = $null
foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe'))) {
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $releaseFile) -and
            (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="17(?:\.|"|-)' -Quiet)) {
            $selectedJdk = $candidate
            break
        }
    }
}
if (-not $selectedJdk) { throw 'JDK 17 is required. Pass -JdkHome with your JDK 17 directory.' }

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $selectedJdk
    $env:Path = "$(Join-Path $selectedJdk 'bin');$previousPath"
    Write-Host "Using JDK 17: $selectedJdk"
    if ($Action -eq 'verify') {
        & .\mvnw.cmd -B verify
        if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }
    } else {
        if (-not (Test-Path -LiteralPath '.env')) { throw 'Create .env from .env.example first.' }
        & docker compose up -d --wait --wait-timeout 120 mysql
        if ($LASTEXITCODE -ne 0) { throw 'MySQL did not become healthy. Check Docker Desktop and docker compose logs mysql.' }
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
    }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
}
