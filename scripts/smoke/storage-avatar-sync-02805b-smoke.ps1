param(
    [string]$RabbitBaseUrl = "http://127.0.0.1:19073",
    [int]$RabbitPort = 19073
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$rabbitPfOut = Join-Path $env:TEMP "ircs-02805b-rabbit-pf.out.log"
$rabbitPfErr = Join-Path $env:TEMP "ircs-02805b-rabbit-pf.err.log"
$rabbitPf = $null

$memberId = [guid]::NewGuid().ToString()
$email = "codex.02805b.$stamp@example.invalid"
$localAvatarUrl = "/media/avatars/codex-02805b-$stamp.png"

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

function Invoke-SqlScalar {
    param([string]$Sql)
    $rows = $Sql | kubectl -n ircs-dev exec -i postgres-0 -- psql -v ON_ERROR_STOP=1 -U postgres -d ircs -t -A
    if ($LASTEXITCODE -ne 0) {
        throw "psql scalar failed with exit code $LASTEXITCODE"
    }
    $value = $rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 1
    if ($null -eq $value) {
        return $null
    }
    return $value.Trim()
}

function Start-RabbitPortForward {
    Remove-Item -LiteralPath $rabbitPfOut, $rabbitPfErr -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", "svc/rabbitmq-svc", "$($RabbitPort):15672") `
        -RedirectStandardOutput $rabbitPfOut `
        -RedirectStandardError $rabbitPfErr `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $rabbitPfErr) { Get-Content -Path $rabbitPfErr -Raw } else { "" }
        throw "rabbitmq port-forward exited: $err"
    }
    return $pf
}

function Get-RabbitPassword {
    $password64 = kubectl -n ircs-dev get secret ircs-dev-secrets -o jsonpath="{.data.RABBITMQ_PASSWORD}"
    if (-not $password64) {
        throw "RABBITMQ_PASSWORD not found"
    }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($password64))
}

function Invoke-RabbitPublish {
    param([string]$MemberId)
    $rabbitPassword = Get-RabbitPassword
    $body = @{
        properties = @{ content_type = "text/plain" }
        routing_key = "image.avatar_sync"
        payload = $MemberId
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 8 -Compress
    $requestFile = Join-Path $env:TEMP ("ircs-02805b-rabbit-publish-" + [guid]::NewGuid() + ".json")
    $responseFile = Join-Path $env:TEMP ("ircs-02805b-rabbit-response-" + [guid]::NewGuid() + ".json")
    [System.IO.File]::WriteAllText($requestFile, $body, [System.Text.UTF8Encoding]::new($false))
    try {
        $code = & curl.exe -sS -u "admin:$rabbitPassword" `
            -H "Content-Type: application/json" `
            -o $responseFile `
            -w "%{http_code}" `
            --data-binary "@$requestFile" `
            "$RabbitBaseUrl/api/exchanges/%2F/x.storage/publish"
        $text = if (Test-Path $responseFile) { Get-Content -LiteralPath $responseFile -Raw } else { "" }
        if ([int]$code -lt 200 -or [int]$code -ge 300) {
            throw "RabbitMQ publish HTTP $code body=$text"
        }
        $response = $text | ConvertFrom-Json
        if (-not $response.routed) {
            throw "RabbitMQ publish was not routed: $text"
        }
    } finally {
        Remove-Item -LiteralPath $requestFile, $responseFile -ErrorAction SilentlyContinue
    }
}

function Get-QueueParts {
    param([string]$Name)
    $lines = kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages consumers
    $line = $lines | Where-Object { $_ -match "^$([regex]::Escape($Name))\s" } | Select-Object -First 1
    if (-not $line) {
        throw "queue not found: $Name"
    }
    return $line -split "\s+"
}

function Assert-QueueEmpty {
    param([string]$Name)
    $parts = Get-QueueParts $Name
    if ([int]$parts[1] -ne 0) {
        throw "queue $Name has $($parts[1]) messages"
    }
}

function Wait-QueueEmpty {
    param([string]$Name)
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Seconds 2
        $parts = Get-QueueParts $Name
        if ([int]$parts[1] -eq 0) {
            return
        }
    }
    throw "queue $Name did not drain"
}

function Cleanup-SmokeData {
    $sql = @"
delete from members where id = '$memberId' or email = '$email';
"@
    Exec-Sql $sql | Out-Null
}

try {
    $rabbitPf = Start-RabbitPortForward
    Cleanup-SmokeData

    $setupSql = @"
insert into members (id, created_at, updated_at, version, email, password_hash, nickname, avatar_url, role, status)
values ('$memberId', now(), now(), 0, '$email', 'noop', 'Codex 02805B Smoke', '$localAvatarUrl', 'MEMBER', 'ACTIVE');
"@
    Exec-Sql $setupSql | Out-Null

    $total = 4
    $i = 1

    $queueParts = Get-QueueParts "q.storage.avatar_sync"
    if ([int]$queueParts[2] -lt 1) {
        throw "q.storage.avatar_sync has no consumers"
    }
    Mark "SYNC_AVATAR consumer listens on q.storage.avatar_sync" $i $total; $i++

    Invoke-RabbitPublish $memberId
    Wait-QueueEmpty "q.storage.avatar_sync"
    Start-Sleep -Seconds 2
    $avatarAfter = Invoke-SqlScalar "select avatar_url from members where id = '$memberId';"
    if ($avatarAfter -ne $localAvatarUrl) {
        throw "R2 disabled dev path changed avatar_url: $avatarAfter"
    }
    Mark "R2 disabled dev path acks fixture and leaves member avatar_url unchanged" $i $total; $i++

    Assert-QueueEmpty "q.storage.avatar_sync"
    Assert-QueueEmpty "q.storage.avatar_sync.dlq"
    Mark "SYNC_AVATAR queues are empty and DLQ has no residue" $i $total; $i++

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $httpRouteOutput = kubectl -n ircs-dev get httproute --no-headers 2>&1
    $httpRouteExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    $httpRouteText = ($httpRouteOutput | Out-String).Trim()
    if ($httpRouteExitCode -ne 0 -and $httpRouteText -notmatch "No resources found") {
        throw "failed to list HTTPRoute: $httpRouteText"
    }
    if ($httpRouteExitCode -eq 0 -and $httpRouteText -and $httpRouteText -notmatch "No resources found") {
        throw "unexpected HTTPRoute in ircs-dev: $httpRouteText"
    }
    Cleanup-SmokeData
    $residue = Invoke-SqlScalar "select count(*) from members where id = '$memberId' or email = '$email';"
    if ($residue -ne "0") {
        throw "smoke cleanup left member residue: $residue"
    }
    Mark "smoke cleanup leaves no DB residue and no HTTPRoute conflict" $i $total
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    foreach ($queue in @("q.storage.avatar_sync", "q.storage.avatar_sync.dlq")) {
        try { kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl purge_queue $queue | Out-Null } catch { Write-Warning $_ }
    }
    if ($rabbitPf -and -not $rabbitPf.HasExited) {
        Stop-Process -Id $rabbitPf.Id -Force -ErrorAction SilentlyContinue
    }
}
