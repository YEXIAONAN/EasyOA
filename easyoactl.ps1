# EasyOA Windows compatibility entry. Default help; signed production only.
# Examples: .\easyoactl.ps1 install; .\easyoactl.ps1 dev -DatabaseOnly
[CmdletBinding()]
param(
    [ValidateSet('help','install','start','stop','restart','status','logs','doctor','verify','dev','version','backup','restore','upgrade')]
    [string]$Command = 'help',
    [switch]$SelfSignedTls,
    [switch]$DatabaseOnly,
    [switch]$Follow,
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $Root

. (Join-Path $Root 'scripts\bootstrap\common.ps1')
. (Join-Path $Root 'scripts\bootstrap\deploy-env.ps1')
. (Join-Path $Root 'scripts\bootstrap\dev.ps1')
if ($DatabaseOnly -and $Command -ne 'dev') { Die '-DatabaseOnly requires dev.' }

function Invoke-ProductionCompose {
    $saved=@{}
    $keys=@('POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD','EASYOA_SESSION_SECRET','EASYOA_BASE_URL','EASYOA_HTTP_PORT','EASYOA_HTTPS_PORT','EASYOA_TLS_CERT_DIR','EASYOA_SOURCE_URL','EASYOA_SESSION_TIMEOUT_MINUTES','EASYOA_LOGIN_MAX_FAILURES','EASYOA_LOGIN_LOCK_MINUTES','EASYOA_STORAGE_PATH','EASYOA_PROFILE')
    try {
        foreach($key in $keys) { $saved[$key]=[Environment]::GetEnvironmentVariable($key); [Environment]::SetEnvironmentVariable($key,(Get-EnvValue $Root $key)) }
        $env:EASYOA_PROFILE='prod'
        & docker compose --project-directory $Root --env-file (Join-Path $Root '.env') -f (Join-Path $Root 'docker-compose.yml') @args
    } finally { foreach($key in $keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key]) } }
}

$script:IntegrityResult = ''

function Test-DockerEnvironment {
    Info 'Checking Docker environment...'
    Require-Command 'docker' 'Docker'
    docker compose version 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Die "Docker Compose v2 is required ('docker compose ...'). Legacy 'docker-compose' is not supported."
    }
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Die 'Docker daemon is not available. Start Docker Desktop first.'
    }
    Success 'Docker and Docker Compose v2 are available.'
}

function Get-EasyVersion {
    if (Test-Path -LiteralPath (Join-Path $Root 'VERSION')) {
        $v = (Get-Content -LiteralPath (Join-Path $Root 'VERSION') -Raw).Trim()
        if ($v) {
            if (-not $v.StartsWith('v')) { $v = "v$v" }
            return $v
        }
    }
    if (Test-Path -LiteralPath (Join-Path $Root '.git')) {
        try {
            $tag = git -C $Root describe --tags --exact-match 2>$null
            if ($LASTEXITCODE -eq 0 -and $tag) { return "$tag".Trim() }
        } catch { }
    }
    $pkg = Join-Path $Root 'frontend\package.json'
    if (Test-Path -LiteralPath $pkg) {
        foreach ($line in Get-Content -LiteralPath $pkg) {
            if ($line -match '"version":\s*"([^"]+)"') {
                $v = $Matches[1]
                if (-not $v.StartsWith('v')) { $v = "v$v" }
                return $v
            }
        }
    }
    return 'unknown'
}

function Invoke-IntegrityGate {
    & (Get-Process -Id $PID).Path -NoProfile -File (Join-Path $Root 'scripts/integrity/verify-integrity.ps1') -Root $Root
    if ($LASTEXITCODE -ne 0) { Die 'Official release verification failed. Use easyoactl.ps1 dev for source development.' }
    $script:IntegrityResult = 'VERIFIED (official signed release)'
}

function Wait-ServiceHealthy {
    param([string]$Service, [int]$TimeoutSeconds)
    $cid = (Invoke-ProductionCompose ps -q $Service 2>$null)
    if (-not $cid -or -not "$cid".Trim()) { return $false }
    $cid = "$cid".Trim()
    $elapsed = 0
    while ($true) {
        $state = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $cid 2>$null
        if ("$state".Trim() -eq 'healthy') { return $true }
        if ($elapsed -ge $TimeoutSeconds) {
            Err "$Service did not become healthy within ${TimeoutSeconds}s (state: $state)."
            return $false
        }
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
}

function Wait-HttpsReachable {
    param([int]$Port, [int]$TimeoutSeconds)
    $elapsed=0
    while ($true) {
        & curl.exe -skf --max-time 3 "https://127.0.0.1:$Port/healthz" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & curl.exe -skf --max-time 3 "https://127.0.0.1:$Port/actuator/health" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $body=& curl.exe -skf --max-time 5 "https://127.0.0.1:$Port/api/system/about" 2>$null
                if ($LASTEXITCODE -eq 0) {
                    try {
                        $identity=$body | ConvertFrom-Json
                        if ($identity.data.signatureVerified -eq $true -and $identity.data.version -eq ((Get-EasyVersion) -replace '^v','')) { return $true }
                    } catch { }
                }
            }
        }
        if ($elapsed -ge $TimeoutSeconds) { Err 'HTTPS entry / API health / signed API identity not confirmed.'; return $false }
        Start-Sleep -Seconds 3; $elapsed+=3
    }
}

function Report-StartupFailure {
    Err 'EasyOA did not become healthy. Current service status:'
    Invoke-ProductionCompose ps
    Write-Host 'Inspect logs with:'
    Write-Host '  docker compose logs -f'
    Write-Host '  docker compose logs easyoa-api'
}

function Show-SuccessBanner {
    $version = Get-EasyVersion
    $base = Get-EnvValue $Root 'EASYOA_BASE_URL'
    if ([string]::IsNullOrEmpty($base)) { $base = 'https://localhost' }
    $port = Get-EnvValue $Root 'EASYOA_HTTPS_PORT'
    $url = $base
    if (-not [string]::IsNullOrEmpty($port) -and $port -ne '443') {
        $builder = New-Object System.UriBuilder($base)
        if ($builder.Uri.IsDefaultPort) { $builder.Port = [int]$port; $url = $builder.Uri.AbsoluteUri }
    }

    function Get-State([string]$Service) {
        $cid = (Invoke-ProductionCompose ps -q $Service 2>$null)
        if (-not $cid -or -not "$cid".Trim()) { return 'not running' }
        $state = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}running{{end}}' "$cid".Trim() 2>$null
        return "$state".Trim()
    }

    Write-Host ''
    Write-Host '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
    Write-Host ''
    Write-Host ' EasyOA started successfully ✓'
    Write-Host ''
    Write-Host " Version:     $version"
    Write-Host ' Mode:        Production'
    Write-Host " Integrity:   $($script:IntegrityResult)"
    Write-Host " URL:         $url"
    Write-Host ''
    Write-Host ' Services:'
    Write-Host "   PostgreSQL    $(Get-State 'postgres')"
    Write-Host "   API           $(Get-State 'easyoa-api')"
    Write-Host "   Web           $(Get-State 'easyoa-web')"
    Write-Host '   Nginx         running'
    Write-Host ''
    Write-Host " First run: open $url and complete /setup initialization."
    Write-Host ''
    Write-Host ' Commands:'
    Write-Host '   docker compose ps'
    Write-Host '   docker compose logs -f'
    Write-Host '   docker compose down'
    Write-Host ''
    Write-Host '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
}

function Ensure-ReleaseImagesAndUp {
    $config = Invoke-ProductionCompose config 2>$null
    if ($LASTEXITCODE -ne 0) { Die 'Invoke-ProductionCompose config failed.' }
    $images = @($config | Where-Object { $_ -match '^\s+image:' } |
        ForEach-Object { ($_ -replace '.*image:\s*', '').Trim() } | Sort-Object -Unique)
    if ($images.Count -eq 0) { Die 'No images resolved from docker-compose.yml.' }

    $tarball = Join-Path $Root 'release-images.tar.gz'
    if (-not (Test-Path -LiteralPath $tarball)) { Die 'Signed release image archive is missing.' }
    Info 'Loading verified release images...'
    docker load -i $tarball | Out-Null
    if ($LASTEXITCODE -ne 0) { Die 'docker load failed for the release package.' }
    foreach ($img in $images) {
        docker image inspect $img 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { Die "Release image is missing: $img" }
    }

    Info 'Starting verified release images (no local rebuild)...'
    Invoke-ProductionCompose up -d --no-build --pull never
    if ($LASTEXITCODE -ne 0) { Die 'docker compose up -d failed.' }
}

function Start-Production {
    Info "EasyOA production startup in: $Root"

    Test-DockerEnvironment
    Require-Command curl.exe curl
    Invoke-IntegrityGate

    Ensure-EnvFile $Root
    Validate-EnvProd $Root
    foreach ($key in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'EASYOA_SESSION_SECRET', 'EASYOA_BASE_URL', 'EASYOA_HTTP_PORT', 'EASYOA_HTTPS_PORT', 'EASYOA_TLS_CERT_DIR')) {
        [Environment]::SetEnvironmentVariable($key, (Get-EnvValue $Root $key))
    }
    $env:EASYOA_PROFILE = 'prod'

    Ensure-TlsProd $Root $SelfSignedTls.IsPresent

    Info 'Validating Docker Compose configuration...'
    Invoke-ProductionCompose config -q 2>$null
    if ($LASTEXITCODE -ne 0) { Die 'Invoke-ProductionCompose config validation failed.' }
    Success 'Compose configuration is valid.'

    Ensure-ReleaseImagesAndUp

    Info 'Waiting for health checks...'
    $failed = $false
    if (-not (Wait-ServiceHealthy 'postgres' 120))  { $failed = $true }
    if (-not (Wait-ServiceHealthy 'easyoa-api' 180)) { $failed = $true }
    if (-not (Wait-ServiceHealthy 'easyoa-web' 60))  { $failed = $true }
    $httpsPort = Get-EnvValue $Root 'EASYOA_HTTPS_PORT'
    if ([string]::IsNullOrEmpty($httpsPort)) { $httpsPort = '443' }
    if (-not (Wait-HttpsReachable ([int]$httpsPort) 30)) { $failed = $true }
    if ($failed) {
        Report-StartupFailure
        exit 1
    }
    Success 'All services are healthy.'

    Show-SuccessBanner
}


switch ($Command) {
    'help' { Write-Host 'EasyOA Community Edition'; Write-Host 'Usage: .\easyoactl.ps1 help|install|start|stop|restart|status|logs|doctor|verify|dev|version'; Write-Host 'backup/restore/upgrade require the Linux/macOS easyoactl entry.' }
    'version' { Get-EasyVersion }
    'verify' { Invoke-IntegrityGate }
    'dev' { Start-Development }
    'install' { Start-Production }
    'start' { Start-Production }
    'stop' { Test-DockerEnvironment; Invoke-ProductionCompose stop; if ($LASTEXITCODE) { Die 'Stop failed.' } }
    'restart' { Invoke-IntegrityGate; Invoke-ProductionCompose stop; if ($LASTEXITCODE) { Die 'Stop failed.' }; Start-Production }
    'status' { Write-Host 'EasyOA Community Edition'; Get-EasyVersion; Invoke-IntegrityGate; Invoke-ProductionCompose ps }
    'logs' { $opts=@('logs','--tail','200'); if ($Follow) { $opts+='-f' }; if ($Service) { if ($Service -notin @('postgres','easyoa-api','easyoa-web','nginx')) { Die 'Unknown service.' }; $opts+=$Service }; Invoke-ProductionCompose @opts }
    'doctor' { Test-DockerEnvironment; Validate-EnvProd $Root; Invoke-IntegrityGate; Invoke-ProductionCompose config -q; if ($LASTEXITCODE) { Die 'Compose config failed.' }; Invoke-ProductionCompose ps }
    default { Die "$Command requires Linux/macOS easyoactl; no data was changed." }
}
