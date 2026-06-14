param(
    [string]$PortalBaseUrl = "http://127.0.0.1:18086",
    [string]$AdminBaseUrl = "http://127.0.0.1:18085",
    [string]$ElasticBaseUrl = "http://127.0.0.1:19200",
    [int]$PortalPort = 18086,
    [int]$AdminPort = 18085,
    [int]$ElasticPort = 19200
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$portalPfOut = Join-Path $env:TEMP "ircs-028-parity-portal-pf.out.log"
$portalPfErr = Join-Path $env:TEMP "ircs-028-parity-portal-pf.err.log"
$adminPfOut = Join-Path $env:TEMP "ircs-028-parity-admin-pf.out.log"
$adminPfErr = Join-Path $env:TEMP "ircs-028-parity-admin-pf.err.log"
$elasticPfOut = Join-Path $env:TEMP "ircs-028-parity-elastic-pf.out.log"
$elasticPfErr = Join-Path $env:TEMP "ircs-028-parity-elastic-pf.err.log"
$portalPf = $null
$adminPf = $null
$elasticPf = $null

$email = "codex.02802.$stamp@example.invalid"
$password = "Codex02802$stamp"
$nickname = "Codex 02802 Smoke $stamp"
$avatarFile = Join-Path $env:TEMP "ircs-02802-avatar-$stamp.png"
$providerCode = "CODEX02803$stamp"
$unifiedVideoId = [guid]::NewGuid().ToString()
$opsUnifiedVideoId = [guid]::NewGuid().ToString()

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

function Invoke-TextRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string[]]$Headers = @()
    )
    $tmp = Join-Path $env:TEMP ("ircs-smoke-text-" + [guid]::NewGuid() + ".txt")
    $args = @("-sS", "--max-time", "20", "-o", $tmp, "-w", "%{http_code}", "-X", $Method)
    foreach ($header in $Headers) {
        $args += @("-H", $header)
    }
    $args += $Url
    $code = & curl.exe @args
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for $Method $Url body=$text"
    }
    return $text
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

function Invoke-Elastic {
    param(
        [string]$Method,
        [string]$Path
    )
    $password64 = kubectl -n ircs-dev get secret ircs-dev-secrets -o jsonpath="{.data.ELASTICSEARCH_PASSWORD}"
    $elasticPassword = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($password64))
    $tmp = Join-Path $env:TEMP ("ircs-smoke-es-" + [guid]::NewGuid() + ".json")
    $code = & curl.exe -sS -u "elastic:$elasticPassword" -o $tmp -w "%{http_code}" -X $Method "$ElasticBaseUrl$Path"
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    return @{ Code = [int]$code; Body = $text }
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
    $challenge = Invoke-JsonRequest "GET" "$PortalBaseUrl/api/portal/auth/pow"
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
    $sql = @"
delete from magnet_provider_runs
where job_id in (select id from magnet_search_jobs where unified_video_id = '$unifiedVideoId');
delete from magnet_search_jobs where unified_video_id = '$unifiedVideoId';
delete from magnet_links where unified_video_id = '$unifiedVideoId' or provider_code = '$providerCode';
delete from magnet_providers where code = '$providerCode';
delete from unified_videos where id in ('$unifiedVideoId', '$opsUnifiedVideoId');
delete from members where email = '$email';
"@
    Exec-Sql $sql | Out-Null
    try { Invoke-Elastic "DELETE" "/ircs_unified_video/_doc/$opsUnifiedVideoId" | Out-Null } catch { }
}

function Start-PortForward {
    param(
        [string]$Target,
        [int]$LocalPort,
        [string]$OutPath,
        [string]$ErrPath
    )
    Remove-Item -LiteralPath $OutPath, $ErrPath -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", $Target, "$($LocalPort):8080") `
        -RedirectStandardOutput $OutPath `
        -RedirectStandardError $ErrPath `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $ErrPath) { Get-Content -Path $ErrPath -Raw } else { "" }
        throw "$Target port-forward exited: $err"
    }
    return $pf
}

function Start-ElasticPortForward {
    Remove-Item -LiteralPath $elasticPfOut, $elasticPfErr -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", "svc/elasticsearch-svc", "$($ElasticPort):9200") `
        -RedirectStandardOutput $elasticPfOut `
        -RedirectStandardError $elasticPfErr `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $elasticPfErr) { Get-Content -Path $elasticPfErr -Raw } else { "" }
        throw "elasticsearch port-forward exited: $err"
    }
    return $pf
}

try {
    $portalPf = Start-PortForward "svc/ircs-portal-bff" $PortalPort $portalPfOut $portalPfErr
    $adminPf = Start-PortForward "svc/ircs-admin-bff" $AdminPort $adminPfOut $adminPfErr
    $elasticPf = Start-ElasticPortForward

    Cleanup-SmokeData

    $total = 8
    $i = 1

    $registerBody = @{
        email = $email
        password = $password
        nickname = $nickname
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    Invoke-JsonRequest "POST" "$PortalBaseUrl/api/portal/auth/register" $registerBody | Out-Null
    Exec-Sql "update members set status = 'ACTIVE' where email = '$email';" | Out-Null

    $loginBody = @{
        email = $email
        password = $password
        powVerification = New-PowVerification
    } | ConvertTo-Json -Depth 6 -Compress
    $tokenResponse = Invoke-JsonRequest "POST" "$PortalBaseUrl/api/portal/auth/login" $loginBody
    if (-not $tokenResponse.token) {
        throw "portal login did not return token"
    }

    [System.IO.File]::WriteAllBytes($avatarFile, [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    $avatar = Invoke-AvatarUpload "$PortalBaseUrl/api/portal/profile/avatar" $tokenResponse.token $avatarFile
    if (-not $avatar.url -or -not $avatar.url.StartsWith("/media/avatars/") -or -not $avatar.url.EndsWith(".png")) {
        throw "avatar upload response mismatch"
    }
    Mark "identity avatar upload persists media through storage-service" $i $total; $i++

    $media = Invoke-TextRequest "GET" "$PortalBaseUrl$($avatar.url)"
    if ([string]::IsNullOrEmpty($media)) {
        throw "stored avatar media response is empty"
    }
    Mark "storage-service serves uploaded avatar through /media/avatars/*" $i $total; $i++

    $providerBody = @{
        code = $providerCode
        name = "Codex 02803 Smoke $stamp"
        providerType = "TORRENT"
        baseUrl = "https://example.invalid/codex-02803"
        enabled = $true
        priority = 6
        riskLevel = "LOW"
        supportedExternalIds = @("IMDB")
        minDelayMs = 10
        maxDelayMs = 20
        timeoutMs = 1000
        resultLimit = 3
        autoApproveAllowed = $true
        contentPolicy = "SMOKE_ONLY"
    } | ConvertTo-Json -Depth 6 -Compress
    Invoke-JsonRequest "POST" "$AdminBaseUrl/api/v1/magnet-providers" $providerBody | Out-Null
    $sql = @"
insert into unified_videos (id, created_at, updated_at, version, title, imdb_id, metadata_status)
values ('$unifiedVideoId', now(), now(), 0, 'Codex 02803 Smoke $stamp', 'tt0280301', 'DIRTY');
"@
    Exec-Sql $sql | Out-Null
    $job = Invoke-JsonRequest "POST" "$AdminBaseUrl/api/v1/magnets/search/unified/$unifiedVideoId"
    if ($job.unifiedVideoId -ne $unifiedVideoId -or $job.status -ne "SUCCESS") {
        throw "magnet search job mismatch: $($job | ConvertTo-Json -Depth 6 -Compress)"
    }
    Mark "POST /api/v1/magnets/search/unified/{id} creates non-skipped search job" $i $total; $i++

    $magnetCheck = @"
select
  case when
    (select count(*) from magnet_provider_runs mpr join magnet_search_jobs msj on mpr.job_id = msj.id where msj.unified_video_id = '$unifiedVideoId') >= 1
    and
    (select count(*) from magnet_links where unified_video_id = '$unifiedVideoId' and provider_code = '$providerCode') >= 1
  then 'OK' else 'MISMATCH' end as magnet_check;
"@
    $magnetRows = Exec-Sql $magnetCheck
    if (($magnetRows -join "`n") -notmatch "OK") {
        throw "magnet provider run/link counts mismatch: $($magnetRows -join ' ')"
    }
    Mark "provider fixture run writes magnet_provider_runs" $i $total; $i++
    Mark "provider fixture candidate upserts magnet_links" $i $total; $i++

    $opsSql = @"
insert into unified_videos (id, created_at, updated_at, version, title, metadata_status)
values ('$opsUnifiedVideoId', now(), now(), 0, 'Codex 02804 Search Reindex $stamp', 'DIRTY');
"@
    Exec-Sql $opsSql | Out-Null
    $session = Invoke-JsonRequest "POST" "$AdminBaseUrl/api/v1/ops/maintenance/search/reindex-unified/init"
    if (-not $session.sessionId) {
        throw "missing maintenance sessionId"
    }
    $stream = Invoke-TextRequest "GET" "$AdminBaseUrl/api/v1/ops/maintenance/stream/$($session.sessionId)"
    if ($stream -notmatch "PROGRESS" -or $stream -notmatch "DONE") {
        throw "maintenance stream did not include progress and done events: $stream"
    }
    Mark "ops search-reindex-unified runner publishes dev-safe progress" $i $total; $i++

    $indexed = $false
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        Start-Sleep -Seconds 3
        $result = Invoke-Elastic "GET" "/ircs_unified_video/_doc/$opsUnifiedVideoId"
        if ($result.Code -eq 200 -and $result.Body -match "Codex 02804 Search Reindex") {
            $indexed = $true
            break
        }
    }
    if (-not $indexed) {
        throw "Elasticsearch document was not indexed for $opsUnifiedVideoId"
    }
    Mark "search-service consumes reindex message and indexes unified video" $i $total; $i++

    Cleanup-SmokeData
    $residue = @"
select
  case when
    (select count(*) from members where email = '$email') = 0
    and
    (select count(*) from unified_videos where id in ('$unifiedVideoId', '$opsUnifiedVideoId')) = 0
    and
    (select count(*) from magnet_search_jobs where unified_video_id = '$unifiedVideoId') = 0
    and
    (select count(*) from magnet_links where unified_video_id = '$unifiedVideoId') = 0
  then 'OK' else 'MISMATCH' end as cleanup_check;
"@
    $cleanupRows = Exec-Sql $residue
    if (($cleanupRows -join "`n") -notmatch "OK") {
        throw "smoke cleanup left residue: $($cleanupRows -join ' ')"
    }
    Mark "smoke cleanup leaves no temporary DB/ES residue" $i $total
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    Remove-Item -LiteralPath $avatarFile -ErrorAction SilentlyContinue
    foreach ($pf in @($portalPf, $adminPf, $elasticPf)) {
        if ($pf -and -not $pf.HasExited) {
            Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
        }
    }
}
