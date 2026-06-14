param(
    [string]$BaseUrl = "http://127.0.0.1:18086",
    [int]$Port = 18086
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-portal-bff-02519-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-portal-bff-02519-pf.err.log"
$pf = $null
$email = "codex.02519.$stamp@example.invalid"
$password = "Codex02519$stamp"
$nickname = "Codex Smoke $stamp"
$avatarFile = Join-Path $env:TEMP "ircs-02519-avatar-$stamp.png"

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [string]$ContentType = "application/json"
    )
    $tmp = Join-Path $env:TEMP ("ircs-smoke-body-" + [guid]::NewGuid() + ".json")
    $requestBodyFile = $null
    $args = @("-sS", "-o", $tmp, "-w", "%{http_code}", "-X", $Method)
    if ($null -ne $Body) {
        $requestBodyFile = Join-Path $env:TEMP ("ircs-smoke-request-" + [guid]::NewGuid() + ".json")
        [System.IO.File]::WriteAllText($requestBodyFile, $Body, [System.Text.Encoding]::UTF8)
        $args += @("-H", "Content-Type: $ContentType", "--data-binary", "@$requestBodyFile")
    }
    $args += $Url
    $code = & curl.exe @args
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    if ($requestBodyFile) {
        Remove-Item -LiteralPath $requestBodyFile -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for $Method $Url body=$text"
    }
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return $text | ConvertFrom-Json
}

function Invoke-AvatarUpload {
    param(
        [string]$Url,
        [string]$Token,
        [string]$Path
    )
    $tmp = Join-Path $env:TEMP ("ircs-smoke-avatar-body-" + [guid]::NewGuid() + ".json")
    $code = & curl.exe -sS -o $tmp -w "%{http_code}" -X POST `
        -H "Authorization: Bearer $Token" `
        -F "file=@$Path;type=image/png;filename=avatar.png" `
        $Url
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for POST $Url body=$text"
    }
    return $text | ConvertFrom-Json
}

function Solve-Pow {
    param([object]$Challenge)
    $difficulty = [int]$Challenge.difficulty
    $prefix = "0" * $difficulty
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        for ($i = 0; $i -lt 10000000; $i++) {
            $nonce = [string]$i
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($Challenge.id + $nonce)
            $hashBytes = $sha.ComputeHash($bytes)
            $hash = -join ($hashBytes | ForEach-Object { $_.ToString("x2") })
            if ($hash.StartsWith($prefix)) {
                return @{ id = $Challenge.id; nonce = $nonce }
            }
        }
    } finally {
        $sha.Dispose()
    }
    throw "PoW solve failed for challenge $($Challenge.id)"
}

function New-PowVerification {
    $challenge = Invoke-JsonRequest "GET" "$BaseUrl/api/portal/auth/pow"
    return Solve-Pow $challenge
}

function Mark {
    param([string]$Name, [int]$Index, [int]$Total)
    Write-Output ("MARK {0}/{1} {2}" -f $Index, $Total, $Name)
}

function Exec-Sql {
    param([string]$Sql)
    $Sql | kubectl -n ircs-dev exec -i postgres-0 -- psql -v ON_ERROR_STOP=1 -U postgres -d ircs
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
}

function Cleanup-SmokeData {
    $sql = "delete from members where email = '$email';"
    Exec-Sql $sql | Out-Null
}

try {
    Remove-Item -LiteralPath $pfOut, $pfErr, $avatarFile -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", "svc/ircs-portal-bff", "$($Port):8080") `
        -RedirectStandardOutput $pfOut `
        -RedirectStandardError $pfErr `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $pfErr) { Get-Content -Path $pfErr -Raw } else { "" }
        throw "portal-bff port-forward exited: $err"
    }

    Cleanup-SmokeData

    $registerBody = @{
        email = $email
        password = $password
        nickname = $nickname
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    Invoke-JsonRequest "POST" "$BaseUrl/api/portal/auth/register" $registerBody | Out-Null
    Exec-Sql "update members set status = 'ACTIVE' where email = '$email';" | Out-Null
    Write-Output "SETUP portal member registered"

    $loginBody = @{
        email = $email
        password = $password
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    $tokenResponse = Invoke-JsonRequest "POST" "$BaseUrl/api/portal/auth/login" $loginBody
    if (-not $tokenResponse.token) {
        throw "portal login did not return token"
    }
    Write-Output "SETUP portal member login"

    [System.IO.File]::WriteAllBytes($avatarFile, [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    $avatar = Invoke-AvatarUpload "$BaseUrl/api/portal/profile/avatar" $tokenResponse.token $avatarFile
    if (-not $avatar.url -or -not $avatar.url.StartsWith("/media/avatars/") -or -not $avatar.url.EndsWith(".png")) {
        throw "avatar upload response mismatch"
    }
    Mark "POST /api/portal/profile/avatar" 1 1

    $check = "select avatar_url from members where email = '$email';"
    $rows = Exec-Sql $check
    if (($rows -join "`n") -notmatch [regex]::Escape($avatar.url)) {
        throw "member avatar_url was not updated"
    }

    Cleanup-SmokeData
    $residue = "select count(*) as members from members where email = '$email';"
    Exec-Sql $residue
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    Remove-Item -LiteralPath $avatarFile -ErrorAction SilentlyContinue
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
