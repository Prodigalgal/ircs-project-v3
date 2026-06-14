param(
    [string]$BaseUrl = "http://127.0.0.1:18085",
    [int]$Port = 18085
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-02520-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-02520-pf.err.log"
$pf = $null
$trafficKey = "Codex:02520:$stamp"
$ids = [ordered]@{
    retry = [guid]::NewGuid().ToString()
    batchRetry = [guid]::NewGuid().ToString()
    discard = [guid]::NewGuid().ToString()
    batchDiscard = [guid]::NewGuid().ToString()
}

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

function Invoke-Status {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Body = $null,
        [string]$ContentType = "application/json"
    )
    $tmp = Join-Path $env:TEMP ("ircs-smoke-body-" + [guid]::NewGuid() + ".txt")
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
    return $code
}

function Invoke-Text {
    param([string]$Url)
    $tmp = Join-Path $env:TEMP ("ircs-smoke-text-" + [guid]::NewGuid() + ".txt")
    $code = & curl.exe -sS --max-time 10 -o $tmp -w "%{http_code}" $Url
    $text = ""
    if (Test-Path $tmp) {
        $text = Get-Content -LiteralPath $tmp -Raw -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    }
    if ([int]$code -lt 200 -or [int]$code -ge 300) {
        throw "HTTP $code for GET $Url body=$text"
    }
    return $text
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

function Redis {
    param([string[]]$RedisArgs)
    kubectl -n ircs-dev exec deploy/valkey -- redis-cli @RedisArgs
    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli failed with exit code $LASTEXITCODE"
    }
}

function Cleanup-SmokeData {
    $idList = "'" + (($ids.Values) -join "','") + "'"
    $sql = "delete from failed_messages where id in ($idList) or queue_name like 'codex.02520.$stamp%';"
    Exec-Sql $sql | Out-Null
    Redis @("DEL", "traffic:limit:$trafficKey") | Out-Null
}

function Seed-Dlq {
    $sql = @"
insert into failed_messages (id, created_at, updated_at, version, queue_name, routing_key, exchange, payload, exception_stack, retry_count, status)
values
  ('$($ids.retry)', now(), now(), 0, 'codex.02520.$stamp.retry', 'codex.02520.$stamp.retry', '', '{"kind":"retry"}', 'codex stack', 0, 'PENDING'),
  ('$($ids.batchRetry)', now(), now(), 0, 'codex.02520.$stamp.batch.retry', 'codex.02520.$stamp.batch.retry', '', '{"kind":"batch-retry"}', 'codex stack', 0, 'PENDING'),
  ('$($ids.discard)', now(), now(), 0, 'codex.02520.$stamp.discard', 'codex.02520.$stamp.discard', '', '{"kind":"discard"}', 'codex stack', 0, 'PENDING'),
  ('$($ids.batchDiscard)', now(), now(), 0, 'codex.02520.$stamp.batch.discard', 'codex.02520.$stamp.batch.discard', '', '{"kind":"batch-discard"}', 'codex stack', 0, 'PENDING');
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
    Seed-Dlq

    $total = 19
    $i = 1

    $metricsStream = Invoke-Text "$BaseUrl/api/v1/dashboard/metrics/stream"
    if ($metricsStream -notmatch "ingestTpm") { throw "metrics stream did not include metrics payload" }
    Mark "GET /api/v1/dashboard/metrics/stream" $i $total; $i++

    $debug = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/debug/metadata/search?title=Codex%2002520&providerType=TMDB"
    if ($debug.status -ne "DRY_RUN") { throw "metadata debug dry-run mismatch" }
    Mark "GET /api/v1/debug/metadata/search" $i $total; $i++

    Redis @("SET", "traffic:limit:$trafficKey", ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + 60000))) | Out-Null
    Invoke-Status "POST" "$BaseUrl/api/v1/ops/traffic/reset" (@{ key = $trafficKey } | ConvertTo-Json -Compress) | Out-Null
    $exists = Redis @("EXISTS", "traffic:limit:$trafficKey")
    if (($exists | Select-Object -Last 1).Trim() -ne "0") { throw "traffic reset did not delete key" }
    Mark "POST /api/v1/ops/traffic/reset" $i $total; $i++

    Invoke-Status "POST" "$BaseUrl/api/v1/ops/dlq/$($ids.retry)/retry" | Out-Null
    Mark "POST /api/v1/ops/dlq/{id}/retry" $i $total; $i++

    Invoke-Status "POST" "$BaseUrl/api/v1/ops/dlq/batch-retry" (@{ ids = @($ids.batchRetry) } | ConvertTo-Json -Compress) | Out-Null
    Mark "POST /api/v1/ops/dlq/batch-retry" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/ops/dlq/$($ids.discard)" | Out-Null
    Mark "DELETE /api/v1/ops/dlq/{id}" $i $total; $i++

    Invoke-Status "POST" "$BaseUrl/api/v1/ops/dlq/batch-discard" (@{ ids = @($ids.batchDiscard) } | ConvertTo-Json -Compress) | Out-Null
    Mark "POST /api/v1/ops/dlq/batch-discard" $i $total; $i++

    $maintenanceRoutes = @(
        "/api/v1/ops/maintenance/sanitize/init",
        "/api/v1/ops/maintenance/aggregation/reset-init",
        "/api/v1/ops/maintenance/unified/recalculate-init",
        "/api/v1/ops/maintenance/area/clean-init",
        "/api/v1/ops/maintenance/language/clean-init",
        "/api/v1/ops/maintenance/llm/sanitize/init",
        "/api/v1/ops/maintenance/llm/area/init",
        "/api/v1/ops/maintenance/llm/genre/init",
        "/api/v1/ops/maintenance/llm/category/init",
        "/api/v1/ops/maintenance/search/reindex-raw/init",
        "/api/v1/ops/maintenance/search/reindex-unified/init"
    )
    foreach ($route in $maintenanceRoutes) {
        $session = Invoke-JsonRequest "POST" "$BaseUrl$route"
        if (-not $session.sessionId) { throw "missing sessionId for $route" }
        if ($route -eq "/api/v1/ops/maintenance/sanitize/init") {
            $stream = Invoke-Text "$BaseUrl/api/v1/ops/maintenance/stream/$($session.sessionId)"
            if ($stream -notmatch "DONE") { throw "maintenance stream did not finish" }
        }
        Mark ("POST " + $route) $i $total; $i++
    }

    Invoke-Status "POST" "$BaseUrl/api/v1/ops/maintenance/trend/sync" | Out-Null
    Mark "POST /api/v1/ops/maintenance/trend/sync" $i $total

    $check = @"
select id::text, status, retry_count
from failed_messages
where id in ('$($ids.retry)', '$($ids.batchRetry)', '$($ids.discard)', '$($ids.batchDiscard)')
order by id;
"@
    Exec-Sql $check

    Cleanup-SmokeData
    $residue = "select count(*) as failed_messages from failed_messages where queue_name like 'codex.02520.$stamp%';"
    Exec-Sql $residue
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
