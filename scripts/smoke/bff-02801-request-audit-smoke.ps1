param(
    [string]$PortalBaseUrl = "http://127.0.0.1:18086",
    [string]$AdminBaseUrl = "http://127.0.0.1:18085",
    [string]$OpsBaseUrl = "http://127.0.0.1:18084",
    [int]$PortalPort = 18086,
    [int]$AdminPort = 18085,
    [int]$OpsPort = 18084
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$portalTrace = "codex-02801-portal-$stamp"
$adminTrace = "codex-02801-admin-$stamp"
$processes = @()

function Start-PortForward {
    param(
        [string]$Service,
        [int]$Port
    )
    $pfOut = Join-Path $env:TEMP "ircs-02801-$Service-pf.out.log"
    $pfErr = Join-Path $env:TEMP "ircs-02801-$Service-pf.err.log"
    Remove-Item -LiteralPath $pfOut, $pfErr -ErrorAction SilentlyContinue
    $process = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", "svc/$Service", "$($Port):8080") `
        -RedirectStandardOutput $pfOut `
        -RedirectStandardError $pfErr `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 4
    if ($process.HasExited) {
        $err = if (Test-Path $pfErr) { Get-Content -Path $pfErr -Raw } else { "" }
        throw "$Service port-forward exited: $err"
    }
    $script:processes += $process
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{}
    )
    $tmp = Join-Path $env:TEMP ("ircs-smoke-body-" + [guid]::NewGuid() + ".json")
    $args = @("-sS", "-o", $tmp, "-w", "%{http_code}", "-X", $Method)
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "${key}: $($Headers[$key])")
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
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    return $text | ConvertFrom-Json
}

function Exec-Sql {
    param([string]$Sql)
    $Sql | kubectl -n ircs-dev exec -i postgres-0 -- psql -v ON_ERROR_STOP=1 -U postgres -d ircs
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
}

function Cleanup-SmokeData {
    $sql = "delete from request_audit_logs where trace_id in ('$portalTrace', '$adminTrace');"
    Exec-Sql $sql | Out-Null
}

function Show-AuditRows {
    param([string]$TraceId)
    $sql = @"
select trace_id, username, method, path, query_string, status_code, success
from request_audit_logs
where trace_id = '$TraceId'
order by created_at desc;
"@
    Exec-Sql $sql
}

function Find-AuditByTrace {
    param([string]$TraceId)
    for ($attempt = 1; $attempt -le 20; $attempt++) {
        $page = Invoke-JsonRequest "GET" "$OpsBaseUrl/api/v1/ops/request-audit?size=100"
        $rows = @($page.content | Where-Object { $_.traceId -eq $TraceId })
        if ($rows.Count -gt 0) {
            return $rows
        }
        Start-Sleep -Seconds 1
    }
    return @()
}

function Mark {
    param([string]$Name, [int]$Index, [int]$Total)
    Write-Output ("MARK {0}/{1} {2}" -f $Index, $Total, $Name)
}

try {
    Start-PortForward "ircs-portal-bff" $PortalPort
    Start-PortForward "ircs-admin-bff" $AdminPort
    Start-PortForward "ircs-ops-service" $OpsPort

    Cleanup-SmokeData

    Invoke-JsonRequest "GET" "$PortalBaseUrl/api/portal/search/suggest?keyword=codex-02801-$stamp" @{
        "X-Trace-Id" = $portalTrace
        "X-Authenticated-User" = "portal-smoke@example.invalid"
        "User-Agent" = "codex-02801-smoke"
    } | Out-Null

    Invoke-JsonRequest "GET" "$AdminBaseUrl/api/v1/dashboard/statistics" @{
        "X-Trace-Id" = $adminTrace
        "X-Authenticated-User" = "admin-smoke@example.invalid"
        "User-Agent" = "codex-02801-smoke"
    } | Out-Null

    Start-Sleep -Seconds 8

    $portalRows = @(Find-AuditByTrace $portalTrace)
    if ($portalRows.Count -ne 1) {
        Show-AuditRows $portalTrace
        throw "portal-bff audit row not found for trace $portalTrace count=$($portalRows.Count)"
    }
    if ($portalRows[0].path -ne "/api/portal/search/suggest" -or $portalRows[0].statusCode -lt 200 -or $portalRows[0].statusCode -ge 300) {
        throw "portal-bff audit row mismatch: $($portalRows[0] | ConvertTo-Json -Compress)"
    }
    Mark "portal-bff proxied request writes request_audit_logs" 1 2

    $adminRows = @(Find-AuditByTrace $adminTrace)
    if ($adminRows.Count -ne 1) {
        Show-AuditRows $adminTrace
        throw "admin-bff audit row not found for trace $adminTrace count=$($adminRows.Count)"
    }
    if ($adminRows[0].path -ne "/api/v1/dashboard/statistics" -or $adminRows[0].statusCode -lt 200 -or $adminRows[0].statusCode -ge 300) {
        throw "admin-bff audit row mismatch: $($adminRows[0] | ConvertTo-Json -Compress)"
    }
    Mark "admin-bff proxied request writes request_audit_logs" 2 2

    $check = @"
select trace_id, username, method, path, status_code, success
from request_audit_logs
where trace_id in ('$portalTrace', '$adminTrace')
order by trace_id;
"@
    Exec-Sql $check

    Cleanup-SmokeData
    Exec-Sql "select count(*) as request_audit_logs from request_audit_logs where trace_id in ('$portalTrace', '$adminTrace');"
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    foreach ($process in $processes) {
        if ($process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}
