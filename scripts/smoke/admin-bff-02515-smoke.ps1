param(
    [string]$BaseUrl = "http://127.0.0.1:18084",
    [int]$Port = 18084
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-02515-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-02515-pf.err.log"
$pf = $null
$ids = [ordered]@{
    ds = [guid]::NewGuid().ToString()
    rc = [guid]::NewGuid().ToString()
    rv = [guid]::NewGuid().ToString()
    sd = [guid]::NewGuid().ToString()
}
$playlistId = $null
$resolverId = $null

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
delete from episodes where playlist_id in (select id from playlists where name like 'Codex Playlist $stamp%');
delete from playlists where id = '$playlistId' or name like 'Codex Playlist $stamp%';
delete from video_resolver_sources where id = '$resolverId' or name like 'Codex Resolver $stamp%';
delete from raw_videos where id = '$($ids.rv)' or source_hash = 'codex-02515-hash-$stamp';
delete from raw_category where id = '$($ids.rc)' or source_code = 'codex-02515-cat-$stamp';
delete from source_domains where id = '$($ids.sd)' or domain_value like '%02515-$stamp%';
delete from data_sources where id = '$($ids.ds)' or name = 'Codex 02515 Smoke $stamp';
"@
    Exec-Sql $sql | Out-Null
}

function Seed-SmokeData {
    $hash = ("codex02515" + $stamp).PadRight(64, "0").Substring(0, 64)
    $sql = @"
insert into data_sources (id, created_at, updated_at, version, name, base_url, list_path, list_params, detail_path, detail_params, field_mapping)
values ('$($ids.ds)', now(), now(), 0, 'Codex 02515 Smoke $stamp', 'https://example.invalid', '/list', '{}'::jsonb, '/detail', '{}'::jsonb, '{}'::jsonb);
insert into raw_category (id, created_at, updated_at, version, data_source_id, source_code, source_name, category_id)
values ('$($ids.rc)', now(), now(), 0, '$($ids.ds)', 'codex-02515-cat-$stamp', 'Codex 02515 Category $stamp', null);
insert into raw_videos (id, created_at, updated_at, version, source_vid, source_hash, data_hash, title, data_source_category_id, data_source_id, enrichment_status, normalization_status, aggregation_status, playlist_retry_count, raw_language_str)
values ('$($ids.rv)', now(), now(), 0, 'codex-02515-source-$stamp', 'codex-02515-hash-$stamp', 'codex-02515-data-$stamp', 'Codex Playlist Video $stamp', '$($ids.rc)', '$($ids.ds)', 'PENDING', 'READY', 'PENDING', 0, 'Codex');
insert into source_domains (id, created_at, updated_at, version, domain_hash, domain_value, remark, data_source_id)
values ('$($ids.sd)', now(), now(), 0, '$hash', 'https://source.02515-$stamp.example.test', 'Codex smoke source domain', '$($ids.ds)');
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

    Seed-SmokeData

    $total = 14
    $i = 1

    $playlistBody = @{
        name = "Codex Playlist $stamp"
        videoId = $ids.rv
        episodes = @(@{
            name = "EP1"
            url = "https://cdn.02515-$stamp.example.test/path/ep1.m3u8?token=1"
        })
    } | ConvertTo-Json -Depth 8 -Compress
    $playlist = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/playlists" $playlistBody
    $playlistId = $playlist.id
    if ($playlist.episodes[0].url -notlike "https://cdn.02515-$stamp.example.test*") { throw "playlist create URL mismatch" }
    Mark "POST /api/v1/playlists" $i $total; $i++

    $playlistUpdateBody = @{
        id = $playlistId
        name = "Codex Playlist Updated $stamp"
        episodes = @(@{
            name = "EP2"
            url = "https://cdn.02515-$stamp.example.test/path/ep2.m3u8"
        })
    } | ConvertTo-Json -Depth 8 -Compress
    $playlistUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/playlists/$playlistId" $playlistUpdateBody
    if ($playlistUpdated.name -ne "Codex Playlist Updated $stamp") { throw "playlist update mismatch" }
    Mark "PUT /api/v1/playlists/{id}" $i $total; $i++

    $playlistPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/playlists?page=0&size=10&name=Codex%20Playlist%20Updated%20$stamp&videoTitle=Codex%20Playlist%20Video%20$stamp"
    if ($playlistPage.totalElements -lt 1) { throw "playlist page missing smoke row" }
    Mark "GET /api/v1/playlists" $i $total; $i++

    $playlistDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/playlists/$playlistId"
    if ($playlistDetail.episodes[0].url -notlike "https://cdn.02515-$stamp.example.test*") { throw "playlist detail URL mismatch" }
    Mark "GET /api/v1/playlists/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/playlists/$playlistId" | Out-Null
    Mark "DELETE /api/v1/playlists/{id}" $i $total; $i++

    $sourcePage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/source-domains?page=0&size=10&keyword=source.02515-$stamp"
    if ($sourcePage.totalElements -lt 1) { throw "source domain page missing smoke row" }
    Mark "GET /api/v1/source-domains" $i $total; $i++

    $sourceDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/source-domains/$($ids.sd)"
    if ($sourceDetail.dataSourceName -ne "Codex 02515 Smoke $stamp") { throw "source domain detail mismatch" }
    Mark "GET /api/v1/source-domains/{id}" $i $total; $i++

    $sourceUpdateBody = @{
        id = $ids.sd
        domainValue = "https://source-updated.02515-$stamp.example.test"
        remark = "Codex smoke updated"
        dataSourceId = $ids.ds
    } | ConvertTo-Json -Compress
    $sourceUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/source-domains/$($ids.sd)" $sourceUpdateBody
    if ($sourceUpdated.domainValue -ne "https://source-updated.02515-$stamp.example.test") { throw "source domain update mismatch" }
    Mark "PUT /api/v1/source-domains/{id}" $i $total; $i++

    $resolverBody = @{
        name = "Codex Resolver $stamp"
        isActive = $true
        remark = "Codex resolver smoke"
        lines = @(@{
            name = "line"
            url = "https://resolver.02515-$stamp.example.test/?url={url}"
        })
    } | ConvertTo-Json -Depth 8 -Compress
    $resolver = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/resolvers" $resolverBody
    $resolverId = $resolver.id
    if ($resolver.name -ne "Codex Resolver $stamp") { throw "resolver create mismatch" }
    Mark "POST /api/v1/resolvers" $i $total; $i++

    $resolverUpdateBody = @{
        name = "Codex Resolver Updated $stamp"
        isActive = $true
        remark = "Codex resolver smoke updated"
        lines = @(@{
            name = "line2"
            url = "https://resolver.02515-$stamp.example.test/updated?url={url}"
        })
    } | ConvertTo-Json -Depth 8 -Compress
    $resolverUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/resolvers/$resolverId" $resolverUpdateBody
    if ($resolverUpdated.lines[0].name -ne "line2") { throw "resolver update mismatch" }
    Mark "PUT /api/v1/resolvers/{id}" $i $total; $i++

    $resolverPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/resolvers?page=0&size=10&sort=name,asc"
    if (@($resolverPage.content | Where-Object { $_.id -eq $resolverId }).Count -lt 1) { throw "resolver page missing smoke row" }
    Mark "GET /api/v1/resolvers" $i $total; $i++

    $activeResolvers = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/resolvers/active"
    if (@($activeResolvers | Where-Object { $_.id -eq $resolverId }).Count -lt 1) { throw "resolver active missing smoke row" }
    Mark "GET /api/v1/resolvers/active" $i $total; $i++

    $resolverDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/resolvers/$resolverId"
    if ($resolverDetail.name -ne "Codex Resolver Updated $stamp") { throw "resolver detail mismatch" }
    Mark "GET /api/v1/resolvers/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/resolvers/$resolverId" | Out-Null
    Mark "DELETE /api/v1/resolvers/{id}" $i $total

    Cleanup-SmokeData
    $check = @"
select
  (select count(*) from playlists where name like 'Codex Playlist $stamp%') as playlists,
  (select count(*) from video_resolver_sources where name like 'Codex Resolver $stamp%') as resolvers,
  (select count(*) from source_domains where domain_value like '%02515-$stamp%') as source_domains,
  (select count(*) from raw_videos where source_hash = 'codex-02515-hash-$stamp') as raw_videos,
  (select count(*) from data_sources where name = 'Codex 02515 Smoke $stamp') as data_sources;
"@
    Exec-Sql $check
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
