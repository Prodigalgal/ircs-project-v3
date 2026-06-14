param(
    [string]$RabbitBaseUrl = "http://127.0.0.1:19074",
    [int]$RabbitPort = 19074,
    [string]$ElasticsearchBaseUrl = "http://127.0.0.1:19200",
    [int]$ElasticsearchPort = 19200
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$rabbitPfOut = Join-Path $env:TEMP "ircs-02805d-rabbit-pf.out.log"
$rabbitPfErr = Join-Path $env:TEMP "ircs-02805d-rabbit-pf.err.log"
$esPfOut = Join-Path $env:TEMP "ircs-02805d-es-pf.out.log"
$esPfErr = Join-Path $env:TEMP "ircs-02805d-es-pf.err.log"
$rabbitPf = $null
$esPf = $null

$sourceDomainId = [guid]::NewGuid().ToString()
$coverImageId = [guid]::NewGuid().ToString()
$rawVideoId = [guid]::NewGuid().ToString()
$unifiedVideoId = [guid]::NewGuid().ToString()
$domainHash = "codex02805d$stamp"
$domainValue = "https://cdn.02805d.example.invalid"
$originalPath = "covers/$coverImageId.jpg"
$expectedCoverUrl = "$domainValue/$originalPath"

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

function Start-PortForward {
    param(
        [string]$Service,
        [int]$LocalPort,
        [int]$RemotePort,
        [string]$OutFile,
        [string]$ErrFile
    )
    Remove-Item -LiteralPath $OutFile, $ErrFile -ErrorAction SilentlyContinue
    $pf = Start-Process -FilePath kubectl `
        -ArgumentList @("-n", "ircs-dev", "port-forward", $Service, "$($LocalPort):$RemotePort") `
        -RedirectStandardOutput $OutFile `
        -RedirectStandardError $ErrFile `
        -WindowStyle Hidden `
        -PassThru
    Start-Sleep -Seconds 5
    if ($pf.HasExited) {
        $err = if (Test-Path $ErrFile) { Get-Content -Path $ErrFile -Raw } else { "" }
        throw "$Service port-forward exited: $err"
    }
    return $pf
}

function Get-SecretValue {
    param([string]$Key)
    $value64 = kubectl -n ircs-dev get secret ircs-dev-secrets -o jsonpath="{.data.$Key}"
    if (-not $value64) {
        throw "$Key not found"
    }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value64))
}

function Invoke-RabbitPublish {
    param(
        [string]$Exchange,
        [string]$RoutingKey,
        [string]$Payload,
        [string]$ContentType = "application/json"
    )
    $rabbitPassword = Get-SecretValue "RABBITMQ_PASSWORD"
    $body = @{
        properties = @{ content_type = $ContentType }
        routing_key = $RoutingKey
        payload = $Payload
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 8 -Compress
    $requestFile = Join-Path $env:TEMP ("ircs-02805d-rabbit-publish-" + [guid]::NewGuid() + ".json")
    $responseFile = Join-Path $env:TEMP ("ircs-02805d-rabbit-response-" + [guid]::NewGuid() + ".json")
    [System.IO.File]::WriteAllText($requestFile, $body, [System.Text.UTF8Encoding]::new($false))
    try {
        $code = & curl.exe -sS -u "admin:$rabbitPassword" `
            -H "Content-Type: application/json" `
            -o $responseFile `
            -w "%{http_code}" `
            --data-binary "@$requestFile" `
            "$RabbitBaseUrl/api/exchanges/%2F/$Exchange/publish"
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

function Get-EsDocument {
    param(
        [string]$Index,
        [string]$Id
    )
    $elasticPassword = Get-SecretValue "ELASTICSEARCH_PASSWORD"
    $responseFile = Join-Path $env:TEMP ("ircs-02805d-es-doc-" + [guid]::NewGuid() + ".json")
    try {
        $code = & curl.exe -sS -u "elastic:$elasticPassword" `
            -o $responseFile `
            -w "%{http_code}" `
            "$ElasticsearchBaseUrl/$Index/_doc/$Id"
        $text = if (Test-Path $responseFile) { Get-Content -LiteralPath $responseFile -Raw } else { "" }
        if ([int]$code -eq 404) {
            return $null
        }
        if ([int]$code -lt 200 -or [int]$code -ge 300) {
            throw "Elasticsearch get HTTP $code body=$text"
        }
        return $text | ConvertFrom-Json
    } finally {
        Remove-Item -LiteralPath $responseFile -ErrorAction SilentlyContinue
    }
}

function Remove-EsDocument {
    param(
        [string]$Index,
        [string]$Id
    )
    $elasticPassword = Get-SecretValue "ELASTICSEARCH_PASSWORD"
    & curl.exe -sS -u "elastic:$elasticPassword" -X DELETE "$ElasticsearchBaseUrl/$Index/_doc/$Id" | Out-Null
}

function Wait-EsCover {
    param(
        [string]$Index,
        [string]$Id,
    [AllowNull()]$ExpectedCoverUrl
    )
    $lastState = "not checked"
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 2
        $doc = Get-EsDocument $Index $Id
        if ($null -ne $doc -and $doc.found) {
            $cover = $doc._source.coverImageUrl
            $lastState = "found cover='$cover'"
            if (-not [string]::IsNullOrEmpty($ExpectedCoverUrl)) {
                if ($cover -eq $ExpectedCoverUrl) {
                    return $doc
                }
            } else {
                if (-not $cover) {
                    return $doc
                }
            }
        } else {
            $lastState = "missing"
        }
    }
    throw "Elasticsearch document $Index/$Id did not reach expected cover '$ExpectedCoverUrl'; last state: $lastState"
}

function Cleanup-SmokeData {
    $sql = @"
delete from raw_video_unified_video where raw_video_id = '$rawVideoId' or unified_video_id = '$unifiedVideoId';
delete from raw_videos where id = '$rawVideoId';
delete from unified_videos where id = '$unifiedVideoId';
delete from cover_images where id = '$coverImageId';
delete from source_domains where id = '$sourceDomainId' or domain_hash = '$domainHash';
"@
    Exec-Sql $sql | Out-Null
}

try {
    $rabbitPf = Start-PortForward "svc/rabbitmq-svc" $RabbitPort 15672 $rabbitPfOut $rabbitPfErr
    $esPf = Start-PortForward "svc/elasticsearch-svc" $ElasticsearchPort 9200 $esPfOut $esPfErr
    Cleanup-SmokeData

    $setupSql = @"
insert into source_domains (id, created_at, updated_at, version, domain_hash, domain_value, remark)
values ('$sourceDomainId', now(), now(), 0, '$domainHash', '$domainValue', '02805d smoke');
insert into cover_images (id, created_at, updated_at, version, storage_type, original_url, source_domain_id, status)
values ('$coverImageId', now(), now(), 0, 'EXTERNAL', '$originalPath', '$sourceDomainId', 'LOCAL_STORED');
insert into raw_videos (
    id, created_at, updated_at, version, source_vid, source_hash, title, cover_image_id,
    enrichment_status, normalization_status, aggregation_status
) values (
    '$rawVideoId', now(), now(), 0, '02805d-raw-$stamp', '02805draw$stamp',
    'Codex 02805D Raw', '$coverImageId', 'SUCCESS', 'READY', 'PENDING'
);
insert into unified_videos (id, created_at, updated_at, version, title, cover_image_id, metadata_status)
values ('$unifiedVideoId', now(), now(), 0, 'Codex 02805D Unified', '$coverImageId', 'DIRTY');
insert into raw_video_unified_video (raw_video_id, unified_video_id)
values ('$rawVideoId', '$unifiedVideoId');
"@
    Exec-Sql $setupSql | Out-Null

    $total = 5
    $i = 1

    $rawIndexPayload = @{
        entityId = $rawVideoId
        entityType = "RAW_VIDEO"
        operation = "INDEX"
    } | ConvertTo-Json -Compress
    $unifiedIndexPayload = @{
        entityId = $unifiedVideoId
        entityType = "UNIFIED_VIDEO"
        operation = "INDEX"
    } | ConvertTo-Json -Compress
    Invoke-RabbitPublish "x.search" "search.sync.raw" $rawIndexPayload
    Invoke-RabbitPublish "x.search" "search.sync.unified" $unifiedIndexPayload
    Wait-EsCover "ircs_raw_video" $rawVideoId $expectedCoverUrl | Out-Null
    Wait-EsCover "ircs_unified_video" $unifiedVideoId $expectedCoverUrl | Out-Null
    Mark "initial raw/unified search documents include coverImageUrl" $i $total; $i++

    Invoke-RabbitPublish "x.storage" "image.cmd.unlink" $coverImageId "text/plain"
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 2
        $dbValue = Invoke-SqlScalar @"
select
  (select count(*) from raw_videos where id = '$rawVideoId' and cover_image_id is null)::text
  || '|' ||
  (select count(*) from unified_videos where id = '$unifiedVideoId' and cover_image_id is null)::text;
"@
        if ($dbValue -eq "1|1") {
            break
        }
        if ($attempt -eq 29) {
            throw "cover references were not unlinked, value=$dbValue"
        }
    }
    Mark "CMD_IMAGE_UNLINK clears raw/unified cover_image_id in PostgreSQL" $i $total; $i++

    Wait-EsCover "ircs_raw_video" $rawVideoId $null | Out-Null
    Wait-EsCover "ircs_unified_video" $unifiedVideoId $null | Out-Null
    Mark "content image unlink publishes raw/unified search INDEX and clears ES coverImageUrl" $i $total; $i++

    foreach ($queue in @(
        "q.storage.image_unlink",
        "q.storage.image_unlink.dlq",
        "q.storage.image_delete",
        "q.storage.image_delete.dlq",
        "q.search.sync.raw",
        "q.search.sync.raw.dlq",
        "q.search.sync.unified",
        "q.search.sync.unified.dlq"
    )) {
        Assert-QueueEmpty $queue
    }
    Mark "storage/search queues and DLQs are empty after unlink chain" $i $total; $i++

    Cleanup-SmokeData
    Remove-EsDocument "ircs_raw_video" $rawVideoId
    Remove-EsDocument "ircs_unified_video" $unifiedVideoId
    $residue = Invoke-SqlScalar @"
select
  (select count(*) from raw_videos where id = '$rawVideoId')::text
  || '|' ||
  (select count(*) from unified_videos where id = '$unifiedVideoId')::text
  || '|' ||
  (select count(*) from cover_images where id = '$coverImageId')::text
  || '|' ||
  (select count(*) from source_domains where id = '$sourceDomainId' or domain_hash = '$domainHash')::text;
"@
    if ($residue -ne "0|0|0|0") {
        throw "smoke cleanup left DB residue: $residue"
    }
    Mark "smoke cleanup leaves no DB/ES fixture residue" $i $total
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    try { Remove-EsDocument "ircs_raw_video" $rawVideoId } catch { Write-Warning $_ }
    try { Remove-EsDocument "ircs_unified_video" $unifiedVideoId } catch { Write-Warning $_ }
    foreach ($queue in @(
        "q.storage.image_unlink",
        "q.storage.image_unlink.dlq",
        "q.storage.image_delete",
        "q.storage.image_delete.dlq",
        "q.search.sync.raw",
        "q.search.sync.raw.dlq",
        "q.search.sync.unified",
        "q.search.sync.unified.dlq"
    )) {
        try { kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl purge_queue $queue | Out-Null } catch { Write-Warning $_ }
    }
    if ($rabbitPf -and -not $rabbitPf.HasExited) {
        Stop-Process -Id $rabbitPf.Id -Force -ErrorAction SilentlyContinue
    }
    if ($esPf -and -not $esPf.HasExited) {
        Stop-Process -Id $esPf.Id -Force -ErrorAction SilentlyContinue
    }
}
