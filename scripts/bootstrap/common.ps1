# ---------------------------------------------------------------------------
# EasyOA 启动脚本共享函数库（PowerShell）。
#
# 仅被 dot-source 使用，不直接执行。提供：
#   * 统一日志（Info / Success / Warn / Err / Die）
#   * 随机密钥生成（.NET RNG，base64 输出）
#   * 支持 Ed25519 的 openssl 探测（PATH + Git for Windows 常见位置）
#   * .env 键值解析（只读解析，绝不 invoke .env）
# ---------------------------------------------------------------------------

$script:UseColor = if ($Host.UI -and $Host.UI.RawUI) { $true } else { $false }

function Write-EasyLog {
    param([string]$Level, [string]$Color, [string]$Message)
    if ($script:UseColor) {
        Write-Host "[$Level]" -ForegroundColor $Color -NoNewline
        Write-Host " $Message"
    } else {
        Write-Host "[$Level] $Message"
    }
}

function Info    { param([string]$Message) Write-EasyLog 'INFO'    'Cyan'  $Message }
function Success { param([string]$Message) Write-EasyLog 'OK'      'Green' $Message }
function Warn    { param([string]$Message) Write-EasyLog 'WARN'    'Yellow' $Message }
function Err     { param([string]$Message) Write-EasyLog 'ERROR'   'Red'   $Message }
function Die     {
    param([string]$Message)
    Err $Message
    exit 1
}

function Require-Command {
    param([string]$Name, [string]$Purpose)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Die "$Purpose is required, but '$Name' was not found in PATH."
    }
}

function New-RandomBase64 {
    param([int]$Bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $buf = New-Object byte[] $Bytes
        $rng.GetBytes($buf)
        return [Convert]::ToBase64String($buf)
    } finally {
        $rng.Dispose()
    }
}

# 在 PATH 与 Git for Windows 常见位置中找一个支持 Ed25519 的 openssl，
# 返回其完整路径；找不到返回 $null。能力只能实测（不能只看版本号）。
function Find-OpenSslEd25519 {
    $candidates = @()
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    if ($env:ProgramFiles) {
        $gitOpenssl = Join-Path $env:ProgramFiles 'Git\usr\bin\openssl.exe'
        if (Test-Path $gitOpenssl) { $candidates += $gitOpenssl }
    }

    foreach ($bin in $candidates) {
        try {
            $tmp = New-Item -ItemType Directory -Path ([System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), [System.IO.Path]::GetRandomFileName()))
            $key = Join-Path $tmp 'k.pem'
            $pub = Join-Path $tmp 'pub.pem'
            $msg = Join-Path $tmp 'm'
            $sig = Join-Path $tmp 'm.sig'
            & $bin genpkey -algorithm ed25519 -out $key 2>$null | Out-Null
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path $key)) { continue }
            [System.IO.File]::WriteAllText($msg, 'probe')
            & $bin pkey -in $key -pubout -out $pub 2>$null | Out-Null
            & $bin pkeyutl -sign -inkey $key -rawin -in $msg -out $sig 2>$null | Out-Null
            if ($LASTEXITCODE -ne 0) { continue }
            & $bin pkeyutl -verify -pubin -inkey $pub -sigfile $sig -rawin -in $msg 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { return $bin }
        } catch {
            continue
        } finally {
            if ($tmp -and (Test-Path $tmp)) { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
        }
    }
    return $null
}

# 从 <root>\.env 只读解析变量值（不执行任何代码）
function Get-EnvValue {
    param([string]$Root, [string]$Key)
    $envFile = Join-Path $Root '.env'
    if (-not (Test-Path $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        if ($line -match ('^' + [regex]::Escape($Key) + '=(.*)$')) {
            return $Matches[1].Trim().Trim('"')
        }
    }
    return $null
}

# 占位符 / 已知弱值 / 长度不足 → 返回 $true（弱）
function Test-WeakSecret {
    param([string]$Value, [int]$MinLength)
    if ([string]::IsNullOrEmpty($Value)) { return $true }
    $lc = $Value.ToLowerInvariant()
    $known = @('password123', 'easyoa123', 'changeme', 'change_me', 'admin123', '123456', 'secret', 'easyoa_dev_password')
    if ($known -contains $lc) { return $true }
    if ($Value.StartsWith('CHANGE_ME')) { return $true }
    if ($Value.Length -lt $MinLength) { return $true }
    return $false
}
