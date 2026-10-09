# ---------------------------------------------------------------------------
# EasyOA 部署环境引导（PowerShell）。
#
# 仅被 easyoactl.ps1 dot-source 使用。提供：
#   * Ensure-EnvFile      —— .env 不存在时从 .env.example 生成并注入随机密钥
#   * Validate-EnvProd    —— 生产模式 fail-closed 配置校验
#   * Validate-EnvDev     —— 开发模式宽松校验（只提醒，不阻断）
#   * Ensure-TlsProd      —— 生产 TLS 证书检查（缺失时允许显式自签名）
# ---------------------------------------------------------------------------

# 幂等：.env 已存在则直接返回，绝不覆盖用户已有配置。
function Ensure-EnvFile {
    param([string]$Root, [string]$Mode = 'production')
    $envFile = Join-Path $Root '.env'
    $example = Join-Path $Root '.env.example'

    if (Test-Path -LiteralPath $envFile) {
        Info '.env already exists, keeping it untouched.'
        return
    }
    if (-not (Test-Path -LiteralPath $example)) {
        Die '.env not found and .env.example is missing.'
    }

    $pgPassword = New-RandomBase64 24
    $sessionSecret = New-RandomBase64 48

    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in Get-Content -LiteralPath $example) {
        if ($line -match '^POSTGRES_PASSWORD=') {
            $out.Add("POSTGRES_PASSWORD=$pgPassword")
        } elseif ($line -match '^EASYOA_SESSION_SECRET=') {
            $out.Add("EASYOA_SESSION_SECRET=$sessionSecret")
        } elseif ($line -match '^EASYOA_DEV_SEED=') {
            $out.Add("EASYOA_DEV_SEED=$($Mode -eq 'dev')".ToLowerInvariant())
        } else {
            $out.Add($line)
        }
    }
    [System.IO.File]::WriteAllLines($envFile, $out)
    Success 'Created .env from .env.example with random secrets:'
    Success "  POSTGRES_PASSWORD     (random, $($pgPassword.Length) chars)"
    Success "  EASYOA_SESSION_SECRET (random, $($sessionSecret.Length) chars)"
    Warn 'Review .env before exposing this deployment (EASYOA_BASE_URL, ports, etc.).'
}

function Validate-EnvProd {
    param([string]$Root)
    $failed = $false

    if ([string]::IsNullOrWhiteSpace((Get-EnvValue $Root 'POSTGRES_DB'))) {
        Err 'POSTGRES_DB is not set in .env.'; $failed = $true
    }
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue $Root 'POSTGRES_USER'))) {
        Err 'POSTGRES_USER is not set in .env.'; $failed = $true
    }
    $pg = Get-EnvValue $Root 'POSTGRES_PASSWORD'
    if (Test-WeakSecret $pg 16) {
        Err 'POSTGRES_PASSWORD is missing, a placeholder, a known weak value, or shorter than 16 chars.'
        Err '  Generate one with: openssl rand -base64 24'
        $failed = $true
    }
    $secret = Get-EnvValue $Root 'EASYOA_SESSION_SECRET'
    if (Test-WeakSecret $secret 32) {
        Err 'EASYOA_SESSION_SECRET is missing, a placeholder, or shorter than 32 chars.'
        Err '  Generate one with: openssl rand -base64 48'
        $failed = $true
    }
    $profile = Get-EnvValue $Root 'EASYOA_PROFILE'
    if (-not [string]::IsNullOrEmpty($profile) -and $profile -ne 'prod') {
        Err "EASYOA_PROFILE='$profile' is not allowed for production startup (expect 'prod' or unset)."
        Err '  Development/demo profiles must never run as production.'
        $failed = $true
    }
    $seed = Get-EnvValue $Root 'EASYOA_DEV_SEED'
    if ($seed -eq 'true' -or $seed -eq '1') {
        Err 'Set EASYOA_DEV_SEED=false for production.'; $failed = $true
    }
    if (-not $failed) { Success '.env passed production checks.' } else { exit 1 }
}

function Validate-EnvDev {
    param([string]$Root)
    $pg = Get-EnvValue $Root 'POSTGRES_PASSWORD'
    if (Test-WeakSecret $pg 1) {
        Warn '.env POSTGRES_PASSWORD looks like a placeholder (fine for local dev only).'
    }
}

function Get-CertDir {
    param([string]$Root)
    $certDir = Get-EnvValue $Root 'EASYOA_TLS_CERT_DIR'
    if ([string]::IsNullOrEmpty($certDir)) { $certDir = './infra/nginx/certs' }
    if (-not [System.IO.Path]::IsPathRooted($certDir)) {
        $certDir = Join-Path $Root $certDir
    }
    return $certDir
}

function Ensure-TlsProd {
    param([string]$Root, [bool]$AllowSelfSigned)
    $certDir = Get-CertDir $Root
    $crt = Join-Path $certDir 'easyoa.crt'
    $key = Join-Path $certDir 'easyoa.key'

    if ((Test-Path -LiteralPath $crt) -and (Test-Path -LiteralPath $key)) {
        Success "TLS certificate found: $crt"
        return
    }

    Warn "No TLS certificate found in $certDir (expected easyoa.crt + easyoa.key)."
    Warn 'EasyOA does not silently serve production over plain HTTP.'

    $answer = ''
    if ($AllowSelfSigned) {
        $answer = 'y'
    } else {
        Warn 'Self-signed certificates are for local/internal testing only.'
        $answer = Read-Host 'Generate a self-signed certificate for local testing? [y/N]'
    }

    if ($answer -match '^(y|Y|yes|YES)$') {
        $genScript = Join-Path $Root 'scripts\generate-self-signed-cert.sh'
        if (-not (Test-Path -LiteralPath $genScript)) {
            Die "Self-signed generator not found: $genScript"
        }
        $domain = Get-EnvValue $Root 'EASYOA_BASE_URL'
        if ($domain) { $domain = ($domain -replace '^[a-zA-Z]+://', '') -replace '[/:].*$', '' }
        if ([string]::IsNullOrEmpty($domain)) { $domain = 'localhost' }
        Warn 'Self-signed certificates are for local/internal testing only.'
        Warn 'For production, replace them with a certificate from a trusted CA.'
        # 生成脚本是 bash 脚本：优先 bash（Git Bash / WSL），否则用 openssl 直接生成
        $bash = Get-Command bash -ErrorAction SilentlyContinue
        if ($bash) {
            & $bash.Source $genScript $domain $certDir
            if ($LASTEXITCODE -ne 0) { Die 'Self-signed certificate generation failed.' }
        } else {
            $ssl = Find-OpenSslEd25519
            if (-not $ssl) { $cmd = Get-Command openssl -ErrorAction SilentlyContinue; if ($cmd) { $ssl = $cmd.Source } }
            if (-not $ssl) { Die "Cannot generate a self-signed certificate: no bash and no openssl found." }
            New-Item -ItemType Directory -Force -Path $certDir | Out-Null
            & $ssl req -x509 -nodes -newkey rsa:2048 `
                -keyout $key -out $crt -days 825 `
                -subj "/C=CN/O=EasyOA/CN=$domain" `
                -addext "subjectAltName=DNS:$domain,DNS:localhost,IP:127.0.0.1"
            if ($LASTEXITCODE -ne 0) { Die 'Self-signed certificate generation failed.' }
        }
        Success "Self-signed certificate generated for '$domain'."
    } else {
        Die "No TLS certificate. Place easyoa.crt + easyoa.key in $certDir, or re-run with -SelfSignedTls for local testing only."
    }
}
