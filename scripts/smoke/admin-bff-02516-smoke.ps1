param(
    [string]$BaseUrl = "http://127.0.0.1:18085",
    [int]$Port = 18085
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$pfOut = Join-Path $env:TEMP "ircs-admin-bff-02516-pf.out.log"
$pfErr = Join-Path $env:TEMP "ircs-admin-bff-02516-pf.err.log"
$pf = $null
$ids = [ordered]@{
    ds = $null
    standardCategory = $null
    standardGenre = $null
    standardArea = $null
    standardLanguage = $null
    rawCategory = [guid]::NewGuid().ToString()
    rawGenre = $null
    rawArea = $null
    rawLanguage = $null
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
delete from video_raw_languages where raw_language_id in (select id from raw_languages where source_value like 'Codex Raw Language 02516 $stamp%');
delete from video_raw_areas where raw_area_id in (select id from raw_areas where source_value like 'Codex Raw Area 02516 $stamp%');
delete from video_raw_genres where raw_genre_id in (select id from raw_genres where source_value like 'Codex Raw Genre 02516 $stamp%');
delete from raw_category where id = '$($ids.rawCategory)' or source_code = 'codex-02516-cat-$stamp';
delete from raw_languages where source_value like 'Codex Raw Language 02516 $stamp%';
delete from raw_areas where source_value like 'Codex Raw Area 02516 $stamp%';
delete from raw_genres where source_value like 'Codex Raw Genre 02516 $stamp%';
delete from standard_languages where name like 'Codex Language 02516 $stamp%';
delete from standard_areas where name like 'Codex Area 02516 $stamp%';
delete from standard_genre where name like 'Codex Genre 02516 $stamp%';
delete from standard_category where slug like 'codex-02516-$stamp%';
delete from data_sources where name like 'Codex 02516 Smoke $stamp%';
"@
    Exec-Sql $sql | Out-Null
}

function Seed-RawCategory {
    $sql = @"
insert into raw_category (id, created_at, updated_at, version, data_source_id, source_code, source_name, category_id)
values ('$($ids.rawCategory)', now(), now(), 0, '$($ids.ds)', 'codex-02516-cat-$stamp', 'Codex Raw Category 02516 $stamp', null);
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

    $total = 25
    $i = 1

    $dsBody = @{
        name = "Codex 02516 Smoke $stamp"
        baseUrl = "http://ircs-config-service.ircs-dev.svc.cluster.local:8080/"
        listPath = "/api/v1/common/timezones"
        listParams = "{}"
        detailPath = "/api/v1/common/timezones"
        detailParams = "{}"
        fieldMapping = '{"title":"vod_name"}'
    } | ConvertTo-Json -Depth 6 -Compress
    $ds = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/data-sources" $dsBody
    $ids.ds = $ds.id
    if ($ds.baseUrl -ne "http://ircs-config-service.ircs-dev.svc.cluster.local:8080") { throw "data-source baseUrl normalization failed" }
    Mark "POST /api/v1/data-sources" $i $total; $i++

    $dsUpdateBody = @{
        id = $ids.ds
        name = "Codex 02516 Smoke $stamp Updated"
        baseUrl = "http://ircs-config-service.ircs-dev.svc.cluster.local:8080"
        listPath = "api/v1/common/timezones"
        listParams = "{}"
        detailPath = "/api/v1/common/timezones"
        detailParams = "{}"
        fieldMapping = '{"title":"vod_name_updated"}'
    } | ConvertTo-Json -Depth 6 -Compress
    $dsUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/data-sources/$($ids.ds)" $dsUpdateBody
    if ($dsUpdated.listPath -ne "/api/v1/common/timezones") { throw "data-source listPath normalization failed" }
    Mark "PUT /api/v1/data-sources/{id}" $i $total; $i++

    $dsPatchBody = @{
        id = $ids.ds
        name = "Codex 02516 Smoke $stamp Patched"
    } | ConvertTo-Json -Depth 4 -Compress
    $dsPatched = Invoke-JsonRequest "PATCH" "$BaseUrl/api/v1/data-sources/$($ids.ds)" $dsPatchBody "application/merge-patch+json"
    if ($dsPatched.name -ne "Codex 02516 Smoke $stamp Patched") { throw "data-source patch mismatch" }
    Mark "PATCH /api/v1/data-sources/{id}" $i $total; $i++

    $fetchBody = @{
        requestType = "LIST"
        baseUrl = "http://ircs-config-service.ircs-dev.svc.cluster.local:8080"
        listPath = "/api/v1/common/timezones"
    } | ConvertTo-Json -Depth 4 -Compress
    $fetch = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/data-sources/fetch-sample" $fetchBody
    if (@($fetch | Where-Object { $_ -eq "Asia/Shanghai" }).Count -lt 1) { throw "fetch-sample did not return timezones" }
    Mark "POST /api/v1/data-sources/fetch-sample" $i $total; $i++

    $categoryBody = @{ name = "Codex Category 02516 $stamp"; slug = "codex-02516-$stamp" } | ConvertTo-Json -Compress
    $category = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/standard-categories" $categoryBody
    $ids.standardCategory = $category.id
    Mark "POST /api/v1/standard-categories" $i $total; $i++

    $categoryPutBody = @{ id = $ids.standardCategory; name = "Codex Category 02516 $stamp Updated"; slug = "codex-02516-$stamp-updated" } | ConvertTo-Json -Compress
    $categoryUpdated = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/standard-categories/$($ids.standardCategory)" $categoryPutBody
    if ($categoryUpdated.slug -ne "codex-02516-$stamp-updated") { throw "standard category update mismatch" }
    Mark "PUT /api/v1/standard-categories/{id}" $i $total; $i++

    $categoryPatchBody = @{ id = $ids.standardCategory; name = "Codex Category 02516 $stamp Patched" } | ConvertTo-Json -Compress
    $categoryPatched = Invoke-JsonRequest "PATCH" "$BaseUrl/api/v1/standard-categories/$($ids.standardCategory)" $categoryPatchBody "application/merge-patch+json"
    if ($categoryPatched.name -ne "Codex Category 02516 $stamp Patched") { throw "standard category patch mismatch" }
    Mark "PATCH /api/v1/standard-categories/{id}" $i $total; $i++

    $genre = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/standard-genres" (@{ name = "Codex Genre 02516 $stamp" } | ConvertTo-Json -Compress)
    $ids.standardGenre = $genre.id
    Mark "POST /api/v1/standard-genres" $i $total; $i++

    $genreUpdate = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/standard-genres/$($ids.standardGenre)" (@{ id = $ids.standardGenre; name = "Codex Genre 02516 $stamp Updated" } | ConvertTo-Json -Compress)
    if ($genreUpdate.name -ne "Codex Genre 02516 $stamp Updated") { throw "standard genre update mismatch" }
    Mark "PUT /api/v1/standard-genres/{id}" $i $total; $i++

    $area = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/standard-areas" (@{ name = "Codex Area 02516 $stamp"; code = "C$($stamp.Substring($stamp.Length - 6))"; region = "Codex" } | ConvertTo-Json -Compress)
    $ids.standardArea = $area.id
    Mark "POST /api/v1/standard-areas" $i $total; $i++

    $language = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/standard-languages" (@{ name = "Codex Language 02516 $stamp"; code = "cx-$stamp"; englishName = "Codex Language"; nativeName = "Codex" } | ConvertTo-Json -Compress)
    $ids.standardLanguage = $language.id
    Mark "POST /api/v1/standard-languages" $i $total; $i++

    Seed-RawCategory
    $rawCategoryBody = @{
        id = $ids.rawCategory
        sourceName = "Codex Raw Category 02516 $stamp Updated"
        categoryId = $ids.standardCategory
    } | ConvertTo-Json -Compress
    $rawCategory = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/raw-categories/$($ids.rawCategory)" $rawCategoryBody
    if ($rawCategory.categoryId -ne $ids.standardCategory) { throw "raw category update mismatch" }
    Mark "PUT /api/v1/raw-categories/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/raw-categories/$($ids.rawCategory)" | Out-Null
    Mark "DELETE /api/v1/raw-categories/{id}" $i $total; $i++

    $rawGenre = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/raw-genres" (@{ sourceValue = "Codex Raw Genre 02516 $stamp"; standardGenreId = $ids.standardGenre } | ConvertTo-Json -Compress)
    $ids.rawGenre = $rawGenre.id
    Mark "POST /api/v1/raw-genres" $i $total; $i++

    $rawGenreUpdate = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/raw-genres/$($ids.rawGenre)" (@{ id = $ids.rawGenre; sourceValue = "Codex Raw Genre 02516 $stamp Updated"; standardGenreId = $ids.standardGenre } | ConvertTo-Json -Compress)
    if ($rawGenreUpdate.sourceValue -ne "Codex Raw Genre 02516 $stamp Updated") { throw "raw genre update mismatch" }
    Mark "PUT /api/v1/raw-genres/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/raw-genres/$($ids.rawGenre)" | Out-Null
    Mark "DELETE /api/v1/raw-genres/{id}" $i $total; $i++

    $rawArea = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/raw-areas" (@{ sourceValue = "Codex Raw Area 02516 $stamp"; standardAreaId = $ids.standardArea } | ConvertTo-Json -Compress)
    $ids.rawArea = $rawArea.id
    Mark "POST /api/v1/raw-areas" $i $total; $i++

    $rawAreaUpdate = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/raw-areas/$($ids.rawArea)" (@{ id = $ids.rawArea; sourceValue = "Codex Raw Area 02516 $stamp Updated"; standardAreaId = $ids.standardArea } | ConvertTo-Json -Compress)
    if ($rawAreaUpdate.sourceValue -ne "Codex Raw Area 02516 $stamp Updated") { throw "raw area update mismatch" }
    Mark "PUT /api/v1/raw-areas/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/raw-areas/$($ids.rawArea)" | Out-Null
    Mark "DELETE /api/v1/raw-areas/{id}" $i $total; $i++

    $rawLanguage = Invoke-JsonRequest "POST" "$BaseUrl/api/v1/raw-languages" (@{ sourceValue = "Codex Raw Language 02516 $stamp"; standardLanguageId = $ids.standardLanguage } | ConvertTo-Json -Compress)
    $ids.rawLanguage = $rawLanguage.id
    Mark "POST /api/v1/raw-languages" $i $total; $i++

    $rawLanguageUpdate = Invoke-JsonRequest "PUT" "$BaseUrl/api/v1/raw-languages/$($ids.rawLanguage)" (@{ id = $ids.rawLanguage; sourceValue = "Codex Raw Language 02516 $stamp Updated"; standardLanguageId = $ids.standardLanguage } | ConvertTo-Json -Compress)
    if ($rawLanguageUpdate.sourceValue -ne "Codex Raw Language 02516 $stamp Updated") { throw "raw language update mismatch" }
    Mark "PUT /api/v1/raw-languages/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/raw-languages/$($ids.rawLanguage)" | Out-Null
    Mark "DELETE /api/v1/raw-languages/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/standard-categories/$($ids.standardCategory)" | Out-Null
    Mark "DELETE /api/v1/standard-categories/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/standard-genres/$($ids.standardGenre)" | Out-Null
    Mark "DELETE /api/v1/standard-genres/{id}" $i $total; $i++

    Invoke-Status "DELETE" "$BaseUrl/api/v1/data-sources/$($ids.ds)" | Out-Null
    Mark "DELETE /api/v1/data-sources/{id}" $i $total

    Cleanup-SmokeData
    $check = @"
select
  (select count(*) from data_sources where name like 'Codex 02516 Smoke $stamp%') as data_sources,
  (select count(*) from standard_category where slug like 'codex-02516-$stamp%') as standard_categories,
  (select count(*) from standard_genre where name like 'Codex Genre 02516 $stamp%') as standard_genres,
  (select count(*) from standard_areas where name like 'Codex Area 02516 $stamp%') as standard_areas,
  (select count(*) from standard_languages where name like 'Codex Language 02516 $stamp%') as standard_languages,
  (select count(*) from raw_category where source_code = 'codex-02516-cat-$stamp') as raw_categories,
  (select count(*) from raw_genres where source_value like 'Codex Raw Genre 02516 $stamp%') as raw_genres,
  (select count(*) from raw_areas where source_value like 'Codex Raw Area 02516 $stamp%') as raw_areas,
  (select count(*) from raw_languages where source_value like 'Codex Raw Language 02516 $stamp%') as raw_languages;
"@
    Exec-Sql $check
} finally {
    try { Cleanup-SmokeData } catch { Write-Warning $_ }
    if ($pf -and -not $pf.HasExited) {
        Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue
    }
}
