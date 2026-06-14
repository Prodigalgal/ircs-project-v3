param(
    [string]$BaseUrl = "http://127.0.0.1:18085",
    [int]$Port = 18085
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-02517-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-02517-pf.err.log"
$pf = $null
$configKey = "codex.02517.smoke.$stamp"
$credentialId = $null

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
delete from sys_credentials where name like 'Codex 02517 Smoke $stamp%';
delete from system_configs where config_key = '$configKey';
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

    $total = 9
    $i = 1

    $template = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/credentials/templates?provider=TMDB"
    if (@($template | Where-Object { $_.key -eq "api_key" }).Count -lt 1) {
        throw "TMDB credential template does not expose api_key"
    }
    Mark "GET /api/v1/credentials/templates?provider=TMDB" $i $total; $i++

    $credentialBody = @{
        provider = "TMDB"
        name = "Codex 02517 Smoke $stamp"
        payload = @{ api_key = "codex-02517-$stamp" }
        enabled = $true
        priority = 10
        rateLimit = 3
        rateLimitUnit = "SECOND"
        dayLimit = 100
        monthLimit = 1000
        classALimit = 11
        classBLimit = 22
        remark = "025-17 smoke create"
    } | ConvertTo-Json -Depth 6 -Compress
    $credential = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/credentials" $credentialBody
    $credentialId = $credential.id
    if ($credential.provider -ne "TMDB" -or $credential.enabled -ne $true) {
        throw "credential create mismatch"
    }
    Mark "POST /api/v1/credentials" $i $total; $i++

    $credentialUpdateBody = @{
        provider = "TMDB"
        name = "Codex 02517 Smoke $stamp Updated"
        payload = @{ api_key = "codex-02517-$stamp-updated" }
        enabled = $false
        priority = 20
        rateLimit = 7
        rateLimitUnit = "MINUTE"
        dayLimit = 200
        monthLimit = 2000
        classALimit = 33
        classBLimit = 44
        remark = "025-17 smoke update"
    } | ConvertTo-Json -Depth 6 -Compress
    $credentialUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/credentials/$credentialId" $credentialUpdateBody
    if ($credentialUpdated.name -ne "Codex 02517 Smoke $stamp Updated" -or $credentialUpdated.enabled -ne $false) {
        throw "credential update mismatch"
    }
    Mark "PUT /api/v1/credentials/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/credentials/$credentialId" | Out-Null
    Mark "DELETE /api/v1/credentials/{id}" $i $total; $i++

    Invoke-Status "POST" "$BaseUrl/api/v1/credentials/refresh" | Out-Null
    Mark "POST /api/v1/credentials/refresh" $i $total; $i++

    $configBody = @{
        key = $configKey
        value = "codex-02517-value-$stamp"
        description = "025-17 smoke create"
    } | ConvertTo-Json -Compress
    $config = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/configs" $configBody
    if ($config.key -ne $configKey -or $config.value -ne "codex-02517-value-$stamp") {
        throw "config create mismatch"
    }
    Mark "POST /api/v1/configs" $i $total; $i++

    $configUpdateBody = @{
        key = $configKey
        value = "codex-02517-value-$stamp-updated"
        description = "025-17 smoke update"
    } | ConvertTo-Json -Compress
    $configUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/configs/$configKey" $configUpdateBody
    if ($configUpdated.value -ne "codex-02517-value-$stamp-updated") {
        throw "config update mismatch"
    }
    Mark "PUT /api/v1/configs/{key}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/configs/$configKey" | Out-Null
    Mark "DELETE /api/v1/configs/{key}" $i $total; $i++

    $testBody = @{
        host = "localhost"
        port = 25
        username = "codex"
    } | ConvertTo-Json -Compress
    $testResult = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/configs/test/MAIL" $testBody
    if ($testResult.mode -ne "dry-run" -or $testResult.type -ne "MAIL") {
        throw "config test mismatch"
    }
    Mark "POST /api/v1/configs/test/{type}" $i $total

    Cleanup-SmokeData
    $check = @"
select
  (select count(*) from sys_credentials where name like 'Codex 02517 Smoke $stamp%') as sys_credentials,
  (select count(*) from system_configs where config_key = '$configKey') as system_configs;
"@
    Exec-Sql $check
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
