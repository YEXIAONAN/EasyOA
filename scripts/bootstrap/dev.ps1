# Local Windows development supervisor; dot-sourced by easyoactl.ps1.
function Get-DevValue {
    param([string]$Key, [string]$Default)
    $value = [Environment]::GetEnvironmentVariable($Key)
    if ([string]::IsNullOrEmpty($value)) { $value = Get-EnvValue $Root $Key }
    if ([string]::IsNullOrEmpty($value)) { $value = $Default }
    return $value
}

function Test-DevPort {
    param([string]$Value)
    $port = 0
    if (-not [int]::TryParse($Value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) { return $false }
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $port)
    try { $listener.Start(); return $true } catch { return $false } finally { $listener.Stop() }
}

function Stop-DevTree {
    param([int]$ProcessId)
    Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-DevTree $_.ProcessId }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function Start-DevProcess {
    param([string]$Command, [string]$Directory, [string]$LogPrefix)
    Start-Process -FilePath $env:ComSpec -ArgumentList ('/d /s /c "' + $Command + '"') `
        -WorkingDirectory $Directory -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "$LogPrefix.log" -RedirectStandardError "$LogPrefix.err.log"
}

function Wait-DevHttp {
    param([string]$Url, [System.Diagnostics.Process]$Process, [string]$Label, [int]$TimeoutSeconds)
    $elapsed = 0
    while ($true) {
        curl.exe -fsS --max-time 2 $Url 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        $Process.Refresh()
        if ($Process.HasExited) { Die "$Label exited before becoming ready. See $script:DevLogDir." }
        if ($elapsed -ge $TimeoutSeconds) { Die "$Label did not become ready. See $script:DevLogDir." }
        Start-Sleep -Seconds 2; $elapsed += 2
    }
}

function Start-Development {
    Info 'Development mode: local data only; release integrity is not enforced.'
    Test-DockerEnvironment
    if (-not $DatabaseOnly) {
        Require-Command 'java' 'JDK 21'
        Require-Command 'node' 'Node.js >=22.12'
        Require-Command 'npm.cmd' 'npm'
        Require-Command 'curl.exe' 'curl'
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = (Get-Command java).Source
        $info.Arguments = '-version'
        $info.UseShellExecute = $false
        $info.RedirectStandardError = $true
        $proc = [System.Diagnostics.Process]::Start($info)
        $version = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        if ($version -notmatch 'version "21[.\"]') { Die 'JDK 21 is required. Set JAVA_HOME and PATH to JDK 21.' }
        $nodeVersion = (& node --version).TrimStart('v').Split('.')
        if ([int]$nodeVersion[0] -lt 22 -or ([int]$nodeVersion[0] -eq 22 -and [int]$nodeVersion[1] -lt 12)) {
            Die 'Node.js >=22.12 is required.'
        }
        if (-not (Test-Path (Join-Path $Root 'backend\mvnw.cmd'))) { Die 'backend\mvnw.cmd is missing.' }
    }
    Ensure-EnvFile $Root 'dev'
    Validate-EnvDev $Root
    $envValues = @{}
    if (-not $DatabaseOnly) {
        $apiPort = Get-DevValue 'EASYOA_API_PORT' '8080'
        $webPort = Get-DevValue 'EASYOA_DEV_WEB_PORT' '5173'
        if (-not (Test-DevPort $apiPort)) { Die "API port is invalid or occupied: $apiPort" }
        if (-not (Test-DevPort $webPort)) { Die "Web port is invalid or occupied: $webPort" }
        if ($apiPort -eq $webPort) { Die 'API and Web must use different ports.' }
        $db = Get-DevValue 'POSTGRES_DB' 'easyoa'
        $dbPort = Get-DevValue 'POSTGRES_DEV_PORT' '5432'
        $envValues = @{
            POSTGRES_DB = $db
            POSTGRES_USER = (Get-DevValue 'POSTGRES_USER' 'easyoa')
            POSTGRES_PASSWORD = (Get-DevValue 'POSTGRES_PASSWORD' 'easyoa_dev_password')
            EASYOA_API_PORT = $apiPort
            EASYOA_SESSION_SECRET = (Get-DevValue 'EASYOA_SESSION_SECRET' 'dev-only-session-secret-please-change-32chars')
            EASYOA_DEV_SEED = (Get-DevValue 'EASYOA_DEV_SEED' 'true')
            EASYOA_DB_URL = "jdbc:postgresql://127.0.0.1:${dbPort}/$db"
            EASYOA_STORAGE_PATH = (Join-Path $Root 'storage\files')
            EASYOA_DEV_API_TARGET = "http://127.0.0.1:$apiPort"
            SPRING_PROFILES_ACTIVE = 'dev'
            SERVER_ADDRESS = '127.0.0.1'
        }
    }
    $originals = @{}
    $api = $null; $web = $null
    try {
        foreach ($key in $envValues.Keys) {
            $originals[$key] = [Environment]::GetEnvironmentVariable($key)
            [Environment]::SetEnvironmentVariable($key, $envValues[$key])
        }
        if (-not $DatabaseOnly) {
            $modules = Join-Path $Root 'frontend\node_modules'
            $lock = Join-Path $Root 'frontend\package-lock.json'
            if (-not (Test-Path $modules) -or (Get-Item $lock).LastWriteTime -gt (Get-Item $modules).LastWriteTime) {
                Push-Location (Join-Path $Root 'frontend')
                try { & npm.cmd ci --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { Die 'npm ci failed.' } }
                finally { Pop-Location }
            }
        }
        Invoke-DevCompose config -q
        if ($LASTEXITCODE -ne 0) { Die 'Development Compose configuration is invalid.' }
        Invoke-DevCompose up -d
        if ($LASTEXITCODE -ne 0) { Die 'Development PostgreSQL did not start.' }
        $cid = Invoke-DevCompose ps -q postgres
        if (-not $cid) { Die 'Development PostgreSQL did not start.' }
        $elapsed = 0
        while ($true) {
            $state = docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid".Trim() 2>$null
            if ("$state".Trim() -eq 'healthy') { break }
            if ($elapsed -ge 120) { Die 'PostgreSQL did not become healthy. Inspect the development PostgreSQL logs.' }
            Start-Sleep -Seconds 2; $elapsed += 2
        }
        if ($DatabaseOnly) { Success 'Development PostgreSQL is healthy.'; return }
        $script:DevLogDir = Join-Path $Root ('logs\dev\session-' + [Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $script:DevLogDir -Force | Out-Null
        $api = Start-DevProcess ('"' + (Join-Path $Root 'backend\mvnw.cmd') + '" spring-boot:run -Dspring-boot.run.profiles=dev') `
            (Join-Path $Root 'backend') (Join-Path $script:DevLogDir 'api')
        $web = Start-DevProcess ('"' + (Get-Command npm.cmd).Source + '" run dev -- --host 127.0.0.1 --port ' + $webPort + ' --strictPort') `
            (Join-Path $Root 'frontend') (Join-Path $script:DevLogDir 'web')
        Wait-DevHttp "http://127.0.0.1:$apiPort/actuator/health" $api 'API' 180
        Wait-DevHttp "http://127.0.0.1:$webPort/" $web 'Web' 60
        Success "EasyOA development is ready: http://127.0.0.1:$webPort"
        Info "Logs: $script:DevLogDir. Press Ctrl+C to stop API/Web; database and files are preserved."
        while ($true) {
            $api.Refresh(); $web.Refresh()
            if ($api.HasExited -or $web.HasExited) { Die "An application process exited. See $script:DevLogDir." }
            Start-Sleep -Seconds 2
        }
    } finally {
        foreach ($process in @($api, $web)) { if ($process) { Stop-DevTree $process.Id } }
        foreach ($key in $originals.Keys) { [Environment]::SetEnvironmentVariable($key, $originals[$key]) }
    }
}

function Invoke-DevCompose { & docker compose --project-directory $Root --env-file (Join-Path $Root '.env') -f (Join-Path $Root 'docker-compose.dev.yml') @args }
