param(
    [string]$BaseUrl = "http://127.0.0.1:18083",
    [int]$Port = 18083
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-smoke-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-smoke-pf.err.log"
$pf = $null
$actorId = $null
$directorId = $null
$ids = [ordered]@{
    ds = [guid]::NewGuid().ToString()
    sc = [guid]::NewGuid().ToString()
    sg = [guid]::NewGuid().ToString()
    sa = [guid]::NewGuid().ToString()
    sl = [guid]::NewGuid().ToString()
    rc = [guid]::NewGuid().ToString()
    rg = [guid]::NewGuid().ToString()
    ra = [guid]::NewGuid().ToString()
    rl = [guid]::NewGuid().ToString()
    rv = [guid]::NewGuid().ToString()
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
delete from video_raw_languages where video_id = '$($ids.rv)' or raw_language_id = '$($ids.rl)';
delete from raw_videos where id = '$($ids.rv)' or source_hash = 'codex-hash-$stamp';
delete from raw_languages where id = '$($ids.rl)' or source_value = 'Codex Raw Language $stamp';
delete from raw_areas where id = '$($ids.ra)' or source_value = 'Codex Raw Area $stamp';
delete from raw_genres where id = '$($ids.rg)' or source_value = 'Codex Raw Genre $stamp';
delete from raw_category where id = '$($ids.rc)' or source_code = 'codex-cat-$stamp';
delete from standard_languages where id = '$($ids.sl)' or name = 'Codex Language $stamp';
delete from standard_areas where id = '$($ids.sa)' or name = 'Codex Area $stamp';
delete from standard_genre where id = '$($ids.sg)' or name = 'Codex Genre $stamp';
delete from standard_category where id = '$($ids.sc)' or slug = 'codex-category-$stamp';
delete from data_sources where id = '$($ids.ds)' or name = 'Codex Catalog Smoke $stamp';
delete from actors where name like 'Codex Actor%$stamp';
delete from directors where name like 'Codex Director%$stamp';
"@
    Exec-Sql $sql | Out-Null
}

function Seed-CatalogData {
    $sql = @"
insert into data_sources (id, created_at, updated_at, version, name, base_url, list_path, list_params, detail_path, detail_params, field_mapping)
values ('$($ids.ds)', now(), now(), 0, 'Codex Catalog Smoke $stamp', 'https://example.invalid', '/list', '{"pg":"{page}"}'::jsonb, '/detail', '{"ids":"{id}"}'::jsonb, '{"title":"title"}'::jsonb);
insert into standard_category (id, created_at, updated_at, version, name, slug)
values ('$($ids.sc)', now(), now(), 0, 'Codex Category $stamp', 'codex-category-$stamp');
insert into standard_genre (id, created_at, updated_at, version, name)
values ('$($ids.sg)', now(), now(), 0, 'Codex Genre $stamp');
insert into standard_areas (id, created_at, updated_at, version, name, code, region)
values ('$($ids.sa)', now(), now(), 0, 'Codex Area $stamp', 'CXSMOKE', 'Codex Region');
insert into standard_languages (id, created_at, updated_at, version, name, code, english_name, native_name)
values ('$($ids.sl)', now(), now(), 0, 'Codex Language $stamp', 'cx-smoke', 'Codex Language', 'Codex');
insert into raw_category (id, created_at, updated_at, version, data_source_id, source_code, source_name, category_id)
values ('$($ids.rc)', now(), now(), 0, '$($ids.ds)', 'codex-cat-$stamp', 'Codex Raw Category $stamp', '$($ids.sc)');
insert into raw_genres (id, created_at, updated_at, version, source_value, standard_genre_id)
values ('$($ids.rg)', now(), now(), 0, 'Codex Raw Genre $stamp', '$($ids.sg)');
insert into raw_areas (id, created_at, updated_at, version, source_value, standard_area_id)
values ('$($ids.ra)', now(), now(), 0, 'Codex Raw Area $stamp', '$($ids.sa)');
insert into raw_languages (id, created_at, updated_at, version, source_value, standard_language_id)
values ('$($ids.rl)', now(), now(), 0, 'Codex Raw Language $stamp', '$($ids.sl)');
insert into raw_videos (id, created_at, updated_at, version, source_vid, source_hash, data_hash, title, data_source_category_id, data_source_id, enrichment_status, normalization_status, aggregation_status, playlist_retry_count, raw_language_str)
values ('$($ids.rv)', now(), now(), 0, 'codex-source-$stamp', 'codex-hash-$stamp', 'codex-data-$stamp', 'Codex Trace Video $stamp', '$($ids.rc)', '$($ids.ds)', 'PENDING', 'READY', 'PENDING', 0, 'Codex Raw Language $stamp');
insert into video_raw_languages (video_id, raw_language_id) values ('$($ids.rv)', '$($ids.rl)');
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

    Seed-CatalogData

    $peopleTotal = 14
    $p = 1
    $actor = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/actors" (@{name = "Codex Actor $stamp"} | ConvertTo-Json -Compress)
    $actorId = $actor.id
    Mark "POST /api/v1/actors" $p $peopleTotal; $p++
    $actorsPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/actors?page=0&size=10&name=Codex%20Actor%20$stamp"
    if ($actorsPage.totalElements -lt 1) { throw "actors page did not include temp actor" }
    Mark "GET /api/v1/actors" $p $peopleTotal; $p++
    $actorsAll = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/actors/all"
    if (@($actorsAll | Where-Object { $_.id -eq $actorId }).Count -lt 1) { throw "actors all did not include temp actor" }
    Mark "GET /api/v1/actors/all" $p $peopleTotal; $p++
    $actorDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/actors/$actorId"
    if ($actorDetail.name -ne "Codex Actor $stamp") { throw "actor detail mismatch" }
    Mark "GET /api/v1/actors/{id}" $p $peopleTotal; $p++
    $actorPut = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/actors/$actorId" (@{name = "Codex Actor Updated $stamp"} | ConvertTo-Json -Compress)
    if ($actorPut.name -ne "Codex Actor Updated $stamp") { throw "actor update mismatch" }
    Mark "PUT /api/v1/actors/{id}" $p $peopleTotal; $p++
    $actorPatch = Invoke-JsonRequest "PATCH" "$BaseUrl/api/v1/actors/$actorId" (@{name = "Codex Actor Patched $stamp"} | ConvertTo-Json -Compress) "application/merge-patch+json"
    if ($actorPatch.name -ne "Codex Actor Patched $stamp") { throw "actor patch mismatch" }
    Mark "PATCH /api/v1/actors/{id}" $p $peopleTotal; $p++
    Invoke-Status "DELETE" "$BaseUrl/api/v1/actors/$actorId" | Out-Null
    $actorId = $null
    Mark "DELETE /api/v1/actors/{id}" $p $peopleTotal; $p++

    $director = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/directors" (@{name = "Codex Director $stamp"} | ConvertTo-Json -Compress)
    $directorId = $director.id
    Mark "POST /api/v1/directors" $p $peopleTotal; $p++
    $directorsPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/directors?page=0&size=10&name=Codex%20Director%20$stamp"
    if ($directorsPage.totalElements -lt 1) { throw "directors page did not include temp director" }
    Mark "GET /api/v1/directors" $p $peopleTotal; $p++
    $directorsAll = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/directors/all"
    if (@($directorsAll | Where-Object { $_.id -eq $directorId }).Count -lt 1) { throw "directors all did not include temp director" }
    Mark "GET /api/v1/directors/all" $p $peopleTotal; $p++
    $directorDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/directors/$directorId"
    if ($directorDetail.name -ne "Codex Director $stamp") { throw "director detail mismatch" }
    Mark "GET /api/v1/directors/{id}" $p $peopleTotal; $p++
    $directorPut = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/directors/$directorId" (@{name = "Codex Director Updated $stamp"} | ConvertTo-Json -Compress)
    if ($directorPut.name -ne "Codex Director Updated $stamp") { throw "director update mismatch" }
    Mark "PUT /api/v1/directors/{id}" $p $peopleTotal; $p++
    $directorPatch = Invoke-JsonRequest "PATCH" "$BaseUrl/api/v1/directors/$directorId" (@{name = "Codex Director Patched $stamp"} | ConvertTo-Json -Compress) "application/merge-patch+json"
    if ($directorPatch.name -ne "Codex Director Patched $stamp") { throw "director patch mismatch" }
    Mark "PATCH /api/v1/directors/{id}" $p $peopleTotal; $p++
    Invoke-Status "DELETE" "$BaseUrl/api/v1/directors/$directorId" | Out-Null
    $directorId = $null
    Mark "DELETE /api/v1/directors/{id}" $p $peopleTotal

    $catalogTotal = 21
    $c = 1
    $dsPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/data-sources?page=0&size=5&sort=name,asc"
    if (@($dsPage.content | Where-Object { $_.id -eq $ids.ds }).Count -lt 1) { throw "data-sources page missing temp row" }
    Mark "GET /api/v1/data-sources" $c $catalogTotal; $c++
    $dsDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/data-sources/$($ids.ds)"
    if ($dsDetail.name -notlike "Codex Catalog Smoke*") { throw "data-source detail mismatch" }
    Mark "GET /api/v1/data-sources/{id}" $c $catalogTotal; $c++
    $stdCats = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-categories"
    if (@($stdCats | Where-Object { $_.id -eq $ids.sc }).Count -lt 1) { throw "standard-categories list missing temp row" }
    Mark "GET /api/v1/standard-categories" $c $catalogTotal; $c++
    $stdCatsAll = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-categories/all"
    if (@($stdCatsAll | Where-Object { $_.id -eq $ids.sc }).Count -lt 1) { throw "standard-categories all missing temp row" }
    Mark "GET /api/v1/standard-categories/all" $c $catalogTotal; $c++
    $stdCatPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-categories/page?page=0&size=5&name=Codex%20Category%20$stamp"
    if ($stdCatPage.totalElements -lt 1) { throw "standard-categories page missing temp row" }
    Mark "GET /api/v1/standard-categories/page" $c $catalogTotal; $c++
    $stdCatDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-categories/$($ids.sc)"
    if ($stdCatDetail.slug -ne "codex-category-$stamp") { throw "standard-category detail mismatch" }
    Mark "GET /api/v1/standard-categories/{id}" $c $catalogTotal; $c++
    $stdGenres = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-genres"
    if (@($stdGenres | Where-Object { $_.id -eq $ids.sg }).Count -lt 1) { throw "standard-genres list missing temp row" }
    Mark "GET /api/v1/standard-genres" $c $catalogTotal; $c++
    $stdGenrePage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-genres/page?page=0&size=5&name=Codex%20Genre%20$stamp"
    if ($stdGenrePage.totalElements -lt 1) { throw "standard-genres page missing temp row" }
    Mark "GET /api/v1/standard-genres/page" $c $catalogTotal; $c++
    $stdGenreDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-genres/$($ids.sg)"
    if ($stdGenreDetail.name -notlike "Codex Genre*") { throw "standard-genre detail mismatch" }
    Mark "GET /api/v1/standard-genres/{id}" $c $catalogTotal; $c++
    $stdAreas = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-areas"
    if (@($stdAreas | Where-Object { $_.id -eq $ids.sa }).Count -lt 1) { throw "standard-areas list missing temp row" }
    Mark "GET /api/v1/standard-areas" $c $catalogTotal; $c++
    $stdAreaPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-areas/page?page=0&size=5&sort=name,asc"
    if (@($stdAreaPage.content | Where-Object { $_.id -eq $ids.sa }).Count -lt 1) { throw "standard-areas page missing temp row" }
    Mark "GET /api/v1/standard-areas/page" $c $catalogTotal; $c++
    $stdLangs = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/standard-languages"
    if (@($stdLangs | Where-Object { $_.id -eq $ids.sl }).Count -lt 1) { throw "standard-languages list missing temp row" }
    Mark "GET /api/v1/standard-languages" $c $catalogTotal; $c++
    $rawCatPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-categories?page=0&size=5&sourceName=Codex%20Raw%20Category%20$stamp&dataSourceId=$($ids.ds)&isUnmapped=false"
    if ($rawCatPage.totalElements -lt 1) { throw "raw-categories page missing temp row" }
    Mark "GET /api/v1/raw-categories" $c $catalogTotal; $c++
    $rawCatDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-categories/$($ids.rc)"
    if ($rawCatDetail.sourceCode -ne "codex-cat-$stamp") { throw "raw-category detail mismatch" }
    Mark "GET /api/v1/raw-categories/{id}" $c $catalogTotal; $c++
    $rawGenrePage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-genres?page=0&size=5&keyword=Codex%20Raw%20Genre%20$stamp&isUnmapped=false"
    if ($rawGenrePage.totalElements -lt 1) { throw "raw-genres page missing temp row" }
    Mark "GET /api/v1/raw-genres" $c $catalogTotal; $c++
    $rawGenreDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-genres/$($ids.rg)"
    if ($rawGenreDetail.sourceValue -notlike "Codex Raw Genre*") { throw "raw-genre detail mismatch" }
    Mark "GET /api/v1/raw-genres/{id}" $c $catalogTotal; $c++
    $rawAreaPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-areas?page=0&size=5&keyword=Codex%20Raw%20Area%20$stamp&isUnmapped=false"
    if ($rawAreaPage.totalElements -lt 1) { throw "raw-areas page missing temp row" }
    Mark "GET /api/v1/raw-areas" $c $catalogTotal; $c++
    $rawAreaDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-areas/$($ids.ra)"
    if ($rawAreaDetail.sourceValue -notlike "Codex Raw Area*") { throw "raw-area detail mismatch" }
    Mark "GET /api/v1/raw-areas/{id}" $c $catalogTotal; $c++
    $rawLangPage = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-languages?page=0&size=5&keyword=Codex%20Raw%20Language%20$stamp&isUnmapped=false"
    if ($rawLangPage.totalElements -lt 1) { throw "raw-languages page missing temp row" }
    Mark "GET /api/v1/raw-languages" $c $catalogTotal; $c++
    $trace = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-languages/$($ids.rl)/trace"
    if (@($trace | Where-Object { $_.videoId -eq $ids.rv -and $_.originalUrl -like "*ids=codex-source-*" }).Count -lt 1) { throw "raw-language trace mismatch" }
    Mark "GET /api/v1/raw-languages/{id}/trace" $c $catalogTotal; $c++
    $rawLangDetail = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/raw-languages/$($ids.rl)"
    if ($rawLangDetail.sourceValue -notlike "Codex Raw Language*") { throw "raw-language detail mismatch" }
    Mark "GET /api/v1/raw-languages/{id}" $c $catalogTotal

    $zones = Invoke-JsonRequest "GET" "$BaseUrl/api/v1/common/timezones"
    if (($zones -contains "Asia/Shanghai") -and ($zones -contains "UTC") -and ($zones -contains "America/New_York")) {
        Write-Output "MARK 1/1 GET /api/v1/common/timezones"
    } else {
        throw "common timezones missing expected zones"
    }

    Cleanup-SmokeData
    $checkSql = @"
select
  (select count(*) from actors where name like 'Codex Actor%$stamp') as actors,
  (select count(*) from directors where name like 'Codex Director%$stamp') as directors,
  (select count(*) from data_sources where name = 'Codex Catalog Smoke $stamp') as data_sources,
  (select count(*) from raw_videos where source_hash = 'codex-hash-$stamp') as raw_videos,
  (select count(*) from raw_languages where source_value = 'Codex Raw Language $stamp') as raw_languages;
"@
    Exec-Sql $checkSql
}
finally {
    if ($actorId) {
        try { Invoke-Status "DELETE" "$BaseUrl/api/v1/actors/$actorId" | Out-Null } catch {}
    }
    if ($directorId) {
        try { Invoke-Status "DELETE" "$BaseUrl/api/v1/directors/$directorId" | Out-Null } catch {}
    }
    try { Cleanup-SmokeData } catch {}
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
