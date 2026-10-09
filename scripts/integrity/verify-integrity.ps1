# ---------------------------------------------------------------------------
# EasyOA 官方发布完整性验证（PowerShell，fail closed）。
#
# 输入：integrity/release-public-key.pem + manifest.sha256 + manifest.sig
# 流程：Ed25519 签名验证 → 清单严格解析 → 逐文件 SHA-256 比对
# 任何一步失败都以非 0 退出，绝不"验证失败但继续"。
#
# 用法：powershell -File scripts\integrity\verify-integrity.ps1 [-Root <发布包根目录>]
# ---------------------------------------------------------------------------
[CmdletBinding()]
param(
    [string]$Root = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\bootstrap\common.ps1')

$Root = [System.IO.Path]::GetFullPath($Root)
$IntegrityDir = Join-Path $Root 'integrity'

function Fail-Integrity {
    param([string]$Message)
    Write-Host ''
    Write-Host '[EasyOA Integrity]'
    Write-Host 'Official release verification failed.'
    Write-Host $Message
    Write-Host 'Please restore the official release package.'
    exit 1
}

# --- 0. integrity 目录内容必须精确（不允许意外元数据） ------------------------
if (-not (Test-Path -LiteralPath $IntegrityDir -PathType Container)) {
    Fail-Integrity "Integrity metadata directory is missing: integrity\"
}
$allowed = @('release-public-key.pem', 'manifest.sha256', 'manifest.sig', 'README.md')
$unexpected = Get-ChildItem -LiteralPath $IntegrityDir -Force |
    Where-Object { $allowed -notcontains $_.Name } |
    ForEach-Object { "  $($_.Name)" }
if ($unexpected) {
    Fail-Integrity ("Unexpected files in integrity\:" + [Environment]::NewLine + ($unexpected -join [Environment]::NewLine))
}

# --- 1. 三件元数据必须齐全 -----------------------------------------------------
$missingMeta = @()
foreach ($f in @('release-public-key.pem', 'manifest.sha256', 'manifest.sig')) {
    $p = Join-Path $IntegrityDir $f
    if (-not (Test-Path -LiteralPath $p -PathType Leaf) -or (Get-Item -LiteralPath $p).Length -eq 0) {
        $missingMeta += "  integrity\$f"
    }
}
if ($missingMeta.Count -gt 0) {
    Fail-Integrity ("Integrity metadata missing or empty:" + [Environment]::NewLine + ($missingMeta -join [Environment]::NewLine))
}

# --- 2. 找到支持 Ed25519 的 openssl -------------------------------------------
$OpenSsl = Find-OpenSslEd25519
if (-not $OpenSsl) {
    Err 'No Ed25519-capable openssl found.'
    Err "Install OpenSSL 1.1.1+ (Git for Windows bundles one) and ensure it is in PATH."
    Fail-Integrity 'Verification tool failure: no usable openssl.'
}

# --- 3. 验证清单签名（Ed25519） -------------------------------------------------
$manifest = Join-Path $IntegrityDir 'manifest.sha256'
$sig      = Join-Path $IntegrityDir 'manifest.sig'
$pubkey   = Join-Path $IntegrityDir 'release-public-key.pem'
& $OpenSsl pkeyutl -verify -pubin -inkey $pubkey -sigfile $sig -rawin -in $manifest 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail-Integrity 'Manifest signature is INVALID for the supplied public key.'
}
Info 'Manifest signature: valid (Ed25519).'

# --- 4. 严格解析清单 -------------------------------------------------------------
$lines = [System.IO.File]::ReadAllLines($manifest)
if ($lines.Count -eq 0) { Fail-Integrity 'Manifest is empty: integrity\manifest.sha256' }

$entries = @{}
$parseErrors = New-Object System.Collections.Generic.List[string]
foreach ($line in $lines) {
    if ($line -notmatch '^([0-9a-f]{64})  (\S.*)$' -or $line -match "[`t`r]") {
        $parseErrors.Add("malformed line: $line")
        continue
    }
    $hash = $Matches[1]
    $rel = $Matches[2]
    if ($rel.StartsWith('/') -or $rel.StartsWith('-') -or $rel -match '^[A-Za-z]:' -or $rel -match '(^|/)\.\.(/|$)' -or $rel -match '\\') {
        $parseErrors.Add("unsafe path: $rel")
        continue
    }
    if ($entries.ContainsKey($rel)) {
        $parseErrors.Add("duplicate path: $rel")
        continue
    }
    $entries[$rel] = $hash
}
if ($parseErrors.Count -gt 0) {
    $detail = ($parseErrors | ForEach-Object { "  $_" }) -join [Environment]::NewLine
    Fail-Integrity ("Manifest parse failure:" + [Environment]::NewLine + $detail)
}
if ($entries.Count -eq 0) { Fail-Integrity 'Manifest contains no entries.' }

# --- 5. 清单必须覆盖全部关键内容（与 generate-manifest.sh 同步） ----------------
$required = @('scripts/integrity/protected-files.txt') + @([System.IO.File]::ReadAllLines((Join-Path $Root 'scripts/integrity/protected-files.txt')) | Where-Object { $_ })
$notCovered = $required | Where-Object { -not $entries.ContainsKey($_) }
$notCovered = @($notCovered) + @(Get-ChildItem -LiteralPath (Join-Path $Root 'infra/nginx/conf.d') -Filter '*.conf' |
    ForEach-Object { 'infra/nginx/conf.d/' + $_.Name } | Where-Object { -not $entries.ContainsKey($_) })
$notCovered = @($notCovered) + @(Get-ChildItem -LiteralPath (Join-Path $Root 'frontend/dist') -Recurse -File | ForEach-Object {
    $_.FullName.Substring($Root.TrimEnd('\','/').Length + 1).Replace('\','/')
} | Where-Object { -not $entries.ContainsKey($_) })
if ($notCovered) {
    $detail = ($notCovered | ForEach-Object { "  $_" }) -join [Environment]::NewLine
    Fail-Integrity ("Manifest does not protect required files:" + [Environment]::NewLine + $detail)
}

# --- 6. 逐文件哈希比对 ------------------------------------------------------------
$modified = New-Object System.Collections.Generic.List[string]
$missing  = New-Object System.Collections.Generic.List[string]
foreach ($rel in $entries.Keys) {
    $file = Join-Path $Root ($rel -replace '/', '\')
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        $missing.Add($rel)
        continue
    }
    $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $entries[$rel]) {
        $modified.Add($rel)
    }
}

if ($modified.Count -gt 0 -or $missing.Count -gt 0) {
    $parts = @()
    if ($modified.Count -gt 0) {
        $parts += 'Modified:' + (($modified | Sort-Object | ForEach-Object { "  $_" }) -join [Environment]::NewLine)
    }
    if ($missing.Count -gt 0) {
        $parts += 'Missing:' + (($missing | Sort-Object | ForEach-Object { "  $_" }) -join [Environment]::NewLine)
    }
    Fail-Integrity (($parts -join [Environment]::NewLine))
}

Success "Official release integrity: VERIFIED ($($entries.Count) protected files, signature valid)."
exit 0
