param(
    [string]$BaseUrl = "http://127.0.0.1:18085",
    [int]$Port = 18085
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-02518-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-02518-pf.err.log"
$pf = $null
$providerCode = "CODEX02518$stamp"
$providerCodeUpdated = "CODEX02518U$stamp"
$unifiedVideoId = [guid]::NewGuid().ToString()

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
delete from magnet_links where unified_video_id = '$unifiedVideoId' or provider_code in ('$providerCode', '$providerCodeUpdated');
delete from unified_videos where id = '$unifiedVideoId' or title like 'Codex 02518 Smoke $stamp%';
delete from magnet_providers where code in ('$providerCode', '$providerCodeUpdated') or name like 'Codex 02518 Smoke $stamp%';
"@
    Exec-Sql $sql | Out-Null
}

function Seed-UnifiedVideo {
    $sql = @"
insert into unified_videos (id, created_at, updated_at, version, title, metadata_status)
values ('$unifiedVideoId', now(), now(), 0, 'Codex 02518 Smoke $stamp', 'DIRTY');
"@
    Exec-Sql $sql | Out-Null
}

try {
    Remove-Item -LiteralPath $pfOut, $pfErr -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", "svc/ircs-admin-bff", "$($Port):8080") `
        -RedirectStandardOutput $pfOut `
        -RedirectStandardError $pfErr `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $pfErr) { Get-Content -Path $pfErr -Raw } else { "" }
        throw "admin-bff port-forward exited: $err"
    }

    Cleanup-SmokeData
    Seed-UnifiedVideo

    $total = 3
    $i = 1

    $createBody = @{
        code = $providerCode
        name = "Codex 02518 Smoke $stamp"
        providerType = "TORRENT"
        baseUrl = "https://example.invalid/codex-02518"
        enabled = $true
        priority = 7
        riskLevel = "LOW"
        supportedExternalIds = @("TMDB", "IMDB")
        minDelayMs = 10
        maxDelayMs = 20
        timeoutMs = 1000
        resultLimit = 3
        autoApproveAllowed = $false
        contentPolicy = "SMOKE_ONLY"
    } | ConvertTo-Json -Depth 6 -Compress
    $provider = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/magnet-providers" $createBody
    if ($provider.code -ne $providerCode -or -not $provider.id) {
        throw "magnet provider create mismatch"
    }
    Mark "POST /api/v1/magnet-providers" $i $total; $i++

    $updateBody = @{
        code = $providerCodeUpdated
        name = "Codex 02518 Smoke $stamp Updated"
        providerType = "TORRENT"
        baseUrl = "https://example.invalid/codex-02518-updated"
        enabled = $false
        priority = 8
        riskLevel = "MEDIUM"
        supportedExternalIds = @("TMDB")
        minDelayMs = 11
        maxDelayMs = 22
        timeoutMs = 1200
        resultLimit = 4
        autoApproveAllowed = $false
        contentPolicy = "SMOKE_ONLY_UPDATED"
    } | ConvertTo-Json -Depth 6 -Compress
    $updated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/magnet-providers/$($provider.id)" $updateBody
    if ($updated.id -ne $provider.id -or $updated.code -ne $providerCodeUpdated -or $updated.enabled -ne $false) {
        throw "magnet provider update mismatch"
    }
    Mark "PUT /api/v1/magnet-providers/{id}" $i $total; $i++

    $job = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/magnets/search/unified/$unifiedVideoId"
    if ($job.unifiedVideoId -ne $unifiedVideoId -or $job.status -ne "SKIPPED" -or $job.skippedReason -ne "DEV_MVP_EXTERNAL_SEARCH_DISABLED") {
        throw "magnet unified search job mismatch"
    }
    Mark "POST /api/v1/magnets/search/unified/{id}" $i $total

    $check = @"
select
  (select count(*) from magnet_providers where code = '$providerCodeUpdated') as magnet_providers,
  (select count(*) from magnet_search_jobs where unified_video_id = '$unifiedVideoId' and status = 'SKIPPED') as magnet_search_jobs;
"@
    Exec-Sql $check

    Cleanup-SmokeData
    $residue = @"
select
  (select count(*) from magnet_providers where code in ('$providerCode', '$providerCodeUpdated')) as magnet_providers,
  (select count(*) from unified_videos where id = '$unifiedVideoId') as unified_videos,
  (select count(*) from magnet_search_jobs where unified_video_id = '$unifiedVideoId') as magnet_search_jobs;
"@
    Exec-Sql $residue
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
