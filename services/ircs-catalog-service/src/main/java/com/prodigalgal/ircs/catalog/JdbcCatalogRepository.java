package com.prodigalgal.ircs.catalog;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class JdbcCatalogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<StandardCategorySummary> listStandardCategories() {
        return jdbcTemplate.query(
                """
                select id, name, slug
                from standard_category
                order by name
                """,
                (rs, rowNum) -> new StandardCategorySummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug")));
    }

    public List<StandardGenreSummary> listStandardGenres() {
        return jdbcTemplate.query(
                """
                select id, name, code
                from standard_genre
                order by name
                """,
                (rs, rowNum) -> new StandardGenreSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code")));
    }

    public List<StandardAreaSummary> listStandardAreas() {
        return jdbcTemplate.query(
                """
                select id, name, code, region
                from standard_areas
                order by name
                """,
                (rs, rowNum) -> new StandardAreaSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("region")));
    }

    public List<StandardLanguageSummary> listStandardLanguages() {
        return jdbcTemplate.query(
                """
                select id, name, code, english_name, native_name
                from standard_languages
                order by name
                """,
                (rs, rowNum) -> new StandardLanguageSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("english_name"),
                        rs.getString("native_name")));
    }

    public List<RawCategorySummary> listRawCategories() {
        return jdbcTemplate.query(
                """
                select rc.id,
                       rc.data_source_id,
                       ds.name as data_source_name,
                       rc.source_code,
                       rc.source_name,
                       rc.category_id,
                       sc.name as category_name,
                       sc.slug as category_slug
                from raw_category rc
                left join data_sources ds on rc.data_source_id = ds.id
                left join standard_category sc on rc.category_id = sc.id
                order by ds.name nulls last, rc.source_name
                """,
                (rs, rowNum) -> new RawCategorySummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("data_source_id", UUID.class),
                        rs.getString("data_source_name"),
                        rs.getString("source_code"),
                        rs.getString("source_name"),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("category_name"),
                        rs.getString("category_slug")));
    }

    public List<DataSourceSummary> listDataSources() {
        return jdbcTemplate.query(
                """
                select id,
                       name,
                       base_url,
                       list_path,
                       detail_path,
                       coalesce(transport_mode, 'AUTO') as transport_mode,
                       coalesce(http_protocol, 'AUTO') as http_protocol,
                       coalesce(ip_version_policy, 'AUTO') as ip_version_policy,
                       coalesce(dns_resolver_type, 'SYSTEM') as dns_resolver_type
                from data_sources
                order by name
                """,
                (rs, rowNum) -> new DataSourceSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("base_url"),
                        rs.getString("list_path"),
                        rs.getString("detail_path"),
                        rs.getString("transport_mode"),
                        rs.getString("http_protocol"),
                        rs.getString("ip_version_policy"),
                        rs.getString("dns_resolver_type")));
    }

    public CatalogPage<DataSourceRead> pageDataSources(CatalogPageRequest pageRequest) {
        return queryPage(
                """
                select id,
                       name,
                       base_url,
                       list_path,
                       list_params::text as list_params,
                       detail_path,
                       detail_params::text as detail_params,
                       field_mapping::text as field_mapping,
                       coalesce(transport_mode, 'AUTO') as transport_mode,
                       coalesce(http_protocol, 'AUTO') as http_protocol,
                       coalesce(ip_version_policy, 'AUTO') as ip_version_policy,
                       coalesce(dns_resolver_type, 'SYSTEM') as dns_resolver_type,
                       dns_resolver_endpoint,
                       connect_timeout_ms,
                       read_timeout_ms,
                       user_agent,
                       created_at,
                       updated_at
                """,
                "from data_sources",
                new MapSqlParameterSource(),
                pageRequest,
                Map.of(
                        "id", "id",
                        "name", "name",
                        "createdAt", "created_at",
                        "updatedAt", "updated_at"),
                "name asc, id asc",
                "id asc",
                dataSourceReadMapper());
    }

    public Optional<DataSourceRead> findDataSource(UUID id) {
        return queryOptional(
                """
                select id,
                       name,
                       base_url,
                       list_path,
                       list_params::text as list_params,
                       detail_path,
                       detail_params::text as detail_params,
                       field_mapping::text as field_mapping,
                       coalesce(transport_mode, 'AUTO') as transport_mode,
                       coalesce(http_protocol, 'AUTO') as http_protocol,
                       coalesce(ip_version_policy, 'AUTO') as ip_version_policy,
                       coalesce(dns_resolver_type, 'SYSTEM') as dns_resolver_type,
                       dns_resolver_endpoint,
                       connect_timeout_ms,
                       read_timeout_ms,
                       user_agent,
                       created_at,
                       updated_at
                from data_sources
                where id = :id
                """,
                new MapSqlParameterSource("id", id),
                dataSourceReadMapper());
    }

    public Optional<DataSourceRead> findDataSourceByName(String name) {
        return queryOptional(
                """
                select id,
                       name,
                       base_url,
                       list_path,
                       list_params::text as list_params,
                       detail_path,
                       detail_params::text as detail_params,
                       field_mapping::text as field_mapping,
                       coalesce(transport_mode, 'AUTO') as transport_mode,
                       coalesce(http_protocol, 'AUTO') as http_protocol,
                       coalesce(ip_version_policy, 'AUTO') as ip_version_policy,
                       coalesce(dns_resolver_type, 'SYSTEM') as dns_resolver_type,
                       dns_resolver_endpoint,
                       connect_timeout_ms,
                       read_timeout_ms,
                       user_agent,
                       created_at,
                       updated_at
                from data_sources
                where lower(name) = lower(:name)
                limit 1
                """,
                new MapSqlParameterSource("name", name),
                dataSourceReadMapper());
    }

    public List<StandardCategoryRead> listStandardCategoryReads() {
        return jdbcTemplate.query(
                """
                select id, name, slug, created_at, updated_at
                from standard_category
                order by name
                """,
                standardCategoryReadMapper());
    }

    public CatalogPage<StandardCategoryRead> pageStandardCategories(
            CatalogPageRequest pageRequest,
            String name) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> predicates = new ArrayList<>();
        addLikePredicate(predicates, params, "name", "name", name);
        return queryPage(
                """
                select id, name, slug, created_at, updated_at
                """,
                fromWhere("standard_category", predicates),
                params,
                pageRequest,
                Map.of(
                        "id", "id",
                        "name", "name",
                        "slug", "slug",
                        "createdAt", "created_at",
                        "updatedAt", "updated_at"),
                "name asc, id asc",
                "id asc",
                standardCategoryReadMapper());
    }

    public Optional<StandardCategoryRead> findStandardCategory(UUID id) {
        return queryOptional(
                """
                select id, name, slug, created_at, updated_at
                from standard_category
                where id = :id
                """,
                new MapSqlParameterSource("id", id),
                standardCategoryReadMapper());
    }

    public List<StandardGenreRead> listStandardGenreReads() {
        return jdbcTemplate.query(
                """
                select id, name, code
                from standard_genre
                order by name
                """,
                standardGenreReadMapper());
    }

    public CatalogPage<StandardGenreRead> pageStandardGenres(CatalogPageRequest pageRequest, String name) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> predicates = new ArrayList<>();
        addLikePredicate(predicates, params, "name", "name", name);
        return queryPage(
                "select id, name, code",
                fromWhere("standard_genre", predicates),
                params,
                pageRequest,
                Map.of("id", "id", "name", "name", "code", "code"),
                "name asc, id asc",
                "id asc",
                standardGenreReadMapper());
    }

    public Optional<StandardGenreRead> findStandardGenre(UUID id) {
        return queryOptional(
                """
                select id, name, code
                from standard_genre
                where id = :id
                """,
                new MapSqlParameterSource("id", id),
                standardGenreReadMapper());
    }

    public List<StandardAreaRead> listStandardAreaReads() {
        return jdbcTemplate.query(
                """
                select id, name, code, region, created_at, updated_at
                from standard_areas
                order by name
                """,
                standardAreaReadMapper());
    }

    public CatalogPage<StandardAreaRead> pageStandardAreas(CatalogPageRequest pageRequest) {
        return queryPage(
                """
                select id, name, code, region, created_at, updated_at
                """,
                "from standard_areas",
                new MapSqlParameterSource(),
                pageRequest,
                Map.of(
                        "id", "id",
                        "name", "name",
                        "code", "code",
                        "region", "region",
                        "createdAt", "created_at",
                        "updatedAt", "updated_at"),
                "name asc, id asc",
                "id asc",
                standardAreaReadMapper());
    }

    public List<StandardLanguageRead> listStandardLanguageReads() {
        return jdbcTemplate.query(
                """
                select id, name, code, english_name, native_name
                from standard_languages
                order by name
                """,
                standardLanguageReadMapper());
    }

    public CatalogPage<RawCategoryRead> pageRawCategories(
            CatalogPageRequest pageRequest,
            RawCategoryQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> predicates = new ArrayList<>();
        addLikePredicate(predicates, params, "rc.source_name", "sourceName", query.sourceName());
        if (query.dataSourceId() != null) {
            predicates.add("rc.data_source_id = :dataSourceId");
            params.addValue("dataSourceId", query.dataSourceId());
        }
        addMappedPredicate(predicates, "rc.category_id", query.isUnmapped(), true);

        return queryPage(
                rawCategorySelect(),
                rawCategoryFromWhere(predicates),
                params,
                pageRequest,
                Map.of(
                        "id", "rc.id",
                        "sourceCode", "rc.source_code",
                        "sourceName", "rc.source_name",
                        "dataSourceName", "ds.name",
                        "categoryName", "sc.name",
                        "createdAt", "rc.created_at",
                        "updatedAt", "rc.updated_at"),
                "ds.name asc nulls last, rc.source_name asc nulls last, rc.id asc",
                "rc.id asc",
                rawCategoryReadMapper());
    }

    public Optional<RawCategoryRead> findRawCategory(UUID id) {
        return queryOptional(
                rawCategorySelect() + " " + rawCategoryFromWhere(List.of("rc.id = :id")),
                new MapSqlParameterSource("id", id),
                rawCategoryReadMapper());
    }

    public DataSourceRead createDataSource(DataSourceAdminRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into data_sources (
                    id, created_at, updated_at, version, name, base_url, list_path,
                    list_params, detail_path, detail_params, field_mapping,
                    transport_mode, http_protocol, ip_version_policy, dns_resolver_type, dns_resolver_endpoint,
                    connect_timeout_ms, read_timeout_ms, user_agent
                )
                values (
                    :id, :now, :now, 0, :name, :baseUrl, :listPath,
                    cast(:listParams as jsonb), :detailPath, cast(:detailParams as jsonb),
                    cast(:fieldMapping as jsonb),
                    :transportMode, :httpProtocol, :ipVersionPolicy, :dnsResolverType, :dnsResolverEndpoint,
                    :connectTimeoutMs, :readTimeoutMs, :userAgent
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", request.name())
                        .addValue("baseUrl", request.baseUrl())
                        .addValue("listPath", request.listPath())
                        .addValue("listParams", request.listParams())
                        .addValue("detailPath", request.detailPath())
                        .addValue("detailParams", request.detailParams())
                        .addValue("fieldMapping", request.fieldMapping())
                        .addValue("transportMode", request.transportMode())
                        .addValue("httpProtocol", request.httpProtocol())
                        .addValue("ipVersionPolicy", request.ipVersionPolicy())
                        .addValue("dnsResolverType", request.dnsResolverType())
                        .addValue("dnsResolverEndpoint", request.dnsResolverEndpoint())
                        .addValue("connectTimeoutMs", request.connectTimeoutMs())
                        .addValue("readTimeoutMs", request.readTimeoutMs())
                        .addValue("userAgent", request.userAgent()));
        return findDataSource(id).orElseThrow();
    }

    public Optional<DataSourceRead> updateDataSource(UUID id, DataSourceAdminRequest request, boolean partial) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id)
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        List<String> assignments = new ArrayList<>();
        addAssignment(assignments, params, "name", "name", request.name(), partial);
        addAssignment(assignments, params, "base_url", "baseUrl", request.baseUrl(), partial);
        addAssignment(assignments, params, "list_path", "listPath", request.listPath(), partial);
        addJsonAssignment(assignments, params, "list_params", "listParams", request.listParams(), partial);
        addAssignment(assignments, params, "detail_path", "detailPath", request.detailPath(), partial);
        addJsonAssignment(assignments, params, "detail_params", "detailParams", request.detailParams(), partial);
        addJsonAssignment(assignments, params, "field_mapping", "fieldMapping", request.fieldMapping(), partial);
        addAssignment(assignments, params, "transport_mode", "transportMode", request.transportMode(), partial);
        addAssignment(assignments, params, "http_protocol", "httpProtocol", request.httpProtocol(), partial);
        addAssignment(assignments, params, "ip_version_policy", "ipVersionPolicy", request.ipVersionPolicy(), partial);
        addAssignment(assignments, params, "dns_resolver_type", "dnsResolverType", request.dnsResolverType(), partial);
        addAssignment(assignments, params, "dns_resolver_endpoint", "dnsResolverEndpoint", request.dnsResolverEndpoint(), partial);
        addAssignment(assignments, params, "connect_timeout_ms", "connectTimeoutMs", request.connectTimeoutMs(), partial);
        addAssignment(assignments, params, "read_timeout_ms", "readTimeoutMs", request.readTimeoutMs(), partial);
        addAssignment(assignments, params, "user_agent", "userAgent", request.userAgent(), partial);
        assignments.add("updated_at = :updatedAt");
        if (assignments.size() == 1) {
            return findDataSource(id);
        }
        updateWithAssignments("data_sources", assignments, params);
        return findDataSource(id);
    }

    public SeedResult seedDataSource(
            String name,
            String baseUrl,
            String apiPath,
            String listParams,
            String detailParams,
            String fieldMapping) {
        Optional<DataSourceSeedRow> existing = queryOptional(
                """
                select id,
                       base_url,
                       list_path,
                       list_params::text as list_params,
                       detail_path,
                       detail_params::text as detail_params,
                       field_mapping::text as field_mapping
                from data_sources
                where lower(name) = lower(:name)
                limit 1
                """,
                new MapSqlParameterSource("name", name),
                (rs, rowNum) -> new DataSourceSeedRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("base_url"),
                        rs.getString("list_path"),
                        rs.getString("list_params"),
                        rs.getString("detail_path"),
                        rs.getString("detail_params"),
                        rs.getString("field_mapping")));
        if (existing.isPresent()) {
            DataSourceSeedRow row = existing.get();
            MapSqlParameterSource params = new MapSqlParameterSource("id", row.id())
                    .addValue("updatedAt", Timestamp.from(Instant.now()))
                    .addValue("baseUrl", baseUrl)
                    .addValue("apiPath", apiPath)
                    .addValue("listParams", listParams)
                    .addValue("detailParams", detailParams)
                    .addValue("fieldMapping", fieldMapping);
            List<String> assignments = new ArrayList<>();
            if (!StringUtils.hasText(row.baseUrl())) {
                assignments.add("base_url = :baseUrl");
            }
            if (!StringUtils.hasText(row.listPath())) {
                assignments.add("list_path = :apiPath");
            }
            if (!StringUtils.hasText(row.listParams())) {
                assignments.add("list_params = cast(:listParams as jsonb)");
            }
            if (!StringUtils.hasText(row.detailPath())) {
                assignments.add("detail_path = :apiPath");
            }
            if (!StringUtils.hasText(row.detailParams())) {
                assignments.add("detail_params = cast(:detailParams as jsonb)");
            }
            if (!StringUtils.hasText(row.fieldMapping())) {
                assignments.add("field_mapping = cast(:fieldMapping as jsonb)");
            }
            if (assignments.isEmpty()) {
                return SeedResult.unchanged(row.id());
            }
            assignments.add("updated_at = :updatedAt");
            assignments.add("version = coalesce(version, 0) + 1");
            updateWithAssignments("data_sources", assignments, params);
            return SeedResult.updated(row.id());
        }

        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into data_sources (
                    id, created_at, updated_at, version, name, base_url, list_path,
                    list_params, detail_path, detail_params, field_mapping
                )
                values (
                    :id, :now, :now, 0, :name, :baseUrl, :apiPath,
                    cast(:listParams as jsonb), :apiPath, cast(:detailParams as jsonb),
                    cast(:fieldMapping as jsonb)
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", name)
                        .addValue("baseUrl", baseUrl)
                        .addValue("apiPath", apiPath)
                        .addValue("listParams", listParams)
                        .addValue("detailParams", detailParams)
                        .addValue("fieldMapping", fieldMapping));
        return SeedResult.inserted(id);
    }

    public List<DataSourceSeedMappingRow> listDataSourceSeedMappings() {
        return jdbcTemplate.query(
                """
                select id, name, field_mapping::text as field_mapping
                from data_sources
                order by name
                """,
                (rs, rowNum) -> new DataSourceSeedMappingRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("field_mapping")));
    }

    public SeedResult updateDataSourceFieldMapping(UUID id, String fieldMapping) {
        namedParameterJdbcTemplate.update(
                """
                update data_sources
                set field_mapping = cast(:fieldMapping as jsonb),
                    updated_at = :updatedAt,
                    version = coalesce(version, 0) + 1
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("fieldMapping", fieldMapping)
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
        return SeedResult.updated(id);
    }

    public StandardCategoryRead createStandardCategory(StandardCategoryAdminRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_category (id, created_at, updated_at, version, name, slug)
                values (:id, :now, :now, 0, :name, :slug)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", request.name())
                        .addValue("slug", request.slug()));
        return findStandardCategory(id).orElseThrow();
    }

    public Optional<StandardCategoryRead> updateStandardCategory(
            UUID id,
            StandardCategoryAdminRequest request,
            boolean partial) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id)
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        List<String> assignments = new ArrayList<>();
        addAssignment(assignments, params, "name", "name", request.name(), partial);
        addAssignment(assignments, params, "slug", "slug", request.slug(), partial);
        assignments.add("updated_at = :updatedAt");
        if (assignments.size() == 1) {
            return findStandardCategory(id);
        }
        updateWithAssignments("standard_category", assignments, params);
        return findStandardCategory(id);
    }

    public StandardGenreRead createStandardGenre(StandardGenreAdminRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_genre (id, created_at, updated_at, version, name, code)
                values (:id, :now, :now, 0, :name, :code)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", request.name())
                        .addValue("code", stableCode(request.code(), request.name())));
        return findStandardGenre(id).orElseThrow();
    }

    public Optional<StandardGenreRead> updateStandardGenre(UUID id, StandardGenreAdminRequest request) {
        int updated = namedParameterJdbcTemplate.update(
                """
                update standard_genre
                set name = :name,
                    code = :code,
                    updated_at = :updatedAt
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", request.name())
                        .addValue("code", stableCode(request.code(), request.name()))
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
        return updated == 0 ? Optional.empty() : findStandardGenre(id);
    }

    public StandardAreaRead createStandardArea(StandardAreaAdminRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_areas (id, created_at, updated_at, version, name, code, region)
                values (:id, :now, :now, 0, :name, :code, :region)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", request.name())
                        .addValue("code", request.code())
                        .addValue("region", request.region()));
        return queryOptional(
                        """
                        select id, name, code, region, created_at, updated_at
                        from standard_areas
                        where id = :id
                        """,
                        new MapSqlParameterSource("id", id),
                        standardAreaReadMapper())
                .orElseThrow();
    }

    public StandardLanguageRead createStandardLanguage(StandardLanguageAdminRequest request) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_languages (
                    id, created_at, updated_at, version, name, code, english_name, native_name
                )
                values (:id, :now, :now, 0, :name, :code, :englishName, :nativeName)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", request.name())
                        .addValue("code", request.code())
                        .addValue("englishName", request.englishName())
                        .addValue("nativeName", request.nativeName()));
        return queryOptional(
                        """
                        select id, name, code, english_name, native_name
                        from standard_languages
                        where id = :id
                        """,
                        new MapSqlParameterSource("id", id),
                        standardLanguageReadMapper())
                .orElseThrow();
    }

    public Optional<RawCategoryRead> updateRawCategory(UUID id, RawCategoryAdminRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sourceName", request.sourceName())
                .addValue("categoryId", request.categoryId())
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        namedParameterJdbcTemplate.update(
                """
                update raw_category
                set source_name = coalesce(:sourceName, source_name),
                    category_id = :categoryId,
                    updated_at = :updatedAt
                where id = :id
                """,
                params);
        return findRawCategory(id);
    }

    public SeedResult syncRawCategory(
            UUID dataSourceId,
            String sourceCode,
            String sourceName,
            String inferredCategoryName) {
        UUID inferredCategoryId = null;
        if (StringUtils.hasText(inferredCategoryName)) {
            inferredCategoryId = findIdByText("standard_category", "name", inferredCategoryName).orElse(null);
        }
        if (inferredCategoryId == null) {
            inferredCategoryId = findIdByText("standard_category", "name", "其他").orElse(null);
        }
        Optional<RawCategorySeedRow> existing = queryOptional(
                """
                select id, source_name, category_id
                from raw_category
                where data_source_id = :dataSourceId and source_code = :sourceCode
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("dataSourceId", dataSourceId)
                        .addValue("sourceCode", sourceCode),
                (rs, rowNum) -> new RawCategorySeedRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("source_name"),
                        rs.getObject("category_id", UUID.class)));
        if (existing.isPresent()) {
            RawCategorySeedRow row = existing.get();
            boolean nameChanged = !java.util.Objects.equals(row.sourceName(), sourceName);
            boolean shouldFillCategory = row.categoryId() == null && inferredCategoryId != null;
            if (!nameChanged && !shouldFillCategory) {
                return SeedResult.unchanged(row.id());
            }
            namedParameterJdbcTemplate.update(
                    """
                    update raw_category
                    set source_name = case when :nameChanged then :sourceName else source_name end,
                        category_id = case
                            when category_id is null and :inferredCategoryId is not null then :inferredCategoryId
                            else category_id
                        end,
                        updated_at = :updatedAt,
                        version = coalesce(version, 0) + 1
                    where id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", row.id())
                            .addValue("sourceName", sourceName)
                            .addValue("nameChanged", nameChanged)
                            .addValue("inferredCategoryId", inferredCategoryId)
                            .addValue("updatedAt", Timestamp.from(Instant.now())));
            return SeedResult.updated(row.id());
        }

        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into raw_category (
                    id, created_at, updated_at, version, data_source_id, source_code, source_name, category_id
                )
                values (:id, :now, :now, 0, :dataSourceId, :sourceCode, :sourceName, :categoryId)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("dataSourceId", dataSourceId)
                        .addValue("sourceCode", sourceCode)
                        .addValue("sourceName", sourceName)
                        .addValue("categoryId", inferredCategoryId));
        return SeedResult.inserted(id);
    }

    public SeedResult seedStandardCategory(String name, String slug) {
        Optional<UUID> existing = findIdByText("standard_category", "name", name);
        if (existing.isPresent()) {
            return SeedResult.unchanged(existing.get());
        }
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_category (id, created_at, updated_at, version, name, slug)
                values (:id, :now, :now, 0, :name, :slug)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", name)
                        .addValue("slug", slug));
        return SeedResult.inserted(id);
    }

    public SeedResult seedStandardGenre(String name) {
        Optional<UUID> existing = findIdByText("standard_genre", "name", name);
        if (existing.isPresent()) {
            return SeedResult.unchanged(existing.get());
        }
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_genre (id, created_at, updated_at, version, name, code)
                values (:id, :now, :now, 0, :name, :code)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", name)
                        .addValue("code", stableCode(null, name)));
        return SeedResult.inserted(id);
    }

    public SeedResult seedStandardArea(String code, String name, String region) {
        Optional<AreaSeedRow> existing = queryOptional(
                """
                select id, region
                from standard_areas
                where upper(code) = upper(:code) or lower(name) = lower(:name)
                order by case when upper(code) = upper(:code) then 0 else 1 end
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("name", name),
                (rs, rowNum) -> new AreaSeedRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("region")));
        if (existing.isPresent()) {
            AreaSeedRow row = existing.get();
            if (!StringUtils.hasText(row.region()) && StringUtils.hasText(region)) {
                namedParameterJdbcTemplate.update(
                        """
                        update standard_areas
                        set region = :region,
                            updated_at = :updatedAt,
                            version = coalesce(version, 0) + 1
                        where id = :id
                        """,
                        new MapSqlParameterSource()
                                .addValue("id", row.id())
                                .addValue("region", region)
                                .addValue("updatedAt", Timestamp.from(Instant.now())));
                return SeedResult.updated(row.id());
            }
            return SeedResult.unchanged(row.id());
        }

        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_areas (id, created_at, updated_at, version, name, code, region)
                values (:id, :now, :now, 0, :name, :code, :region)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", name)
                        .addValue("code", code)
                        .addValue("region", region));
        return SeedResult.inserted(id);
    }

    public SeedResult seedStandardLanguage(String code, String name, String englishName, String nativeName) {
        Optional<LanguageSeedRow> existing = queryOptional(
                """
                select id, code, english_name, native_name
                from standard_languages
                where lower(code) = lower(:code) or lower(name) = lower(:name)
                order by case when lower(code) = lower(:code) then 0 else 1 end
                limit 1
                """,
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("name", name),
                (rs, rowNum) -> new LanguageSeedRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("english_name"),
                        rs.getString("native_name")));
        if (existing.isPresent()) {
            LanguageSeedRow row = existing.get();
            boolean fillCode = !StringUtils.hasText(row.code()) && StringUtils.hasText(code);
            boolean fillEnglishName = !StringUtils.hasText(row.englishName()) && StringUtils.hasText(englishName);
            boolean fillNativeName = !StringUtils.hasText(row.nativeName()) && StringUtils.hasText(nativeName);
            if (fillCode || fillEnglishName || fillNativeName) {
                namedParameterJdbcTemplate.update(
                        """
                        update standard_languages
                        set code = case when code is null or code = '' then :code else code end,
                            english_name = case
                                when english_name is null or english_name = '' then :englishName
                                else english_name
                            end,
                            native_name = case
                                when native_name is null or native_name = '' then :nativeName
                                else native_name
                            end,
                            updated_at = :updatedAt,
                            version = coalesce(version, 0) + 1
                        where id = :id
                        """,
                        new MapSqlParameterSource()
                                .addValue("id", row.id())
                                .addValue("code", code)
                                .addValue("englishName", englishName)
                                .addValue("nativeName", nativeName)
                                .addValue("updatedAt", Timestamp.from(Instant.now())));
                return SeedResult.updated(row.id());
            }
            return SeedResult.unchanged(row.id());
        }

        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        namedParameterJdbcTemplate.update(
                """
                insert into standard_languages (
                    id, created_at, updated_at, version, name, code, english_name, native_name
                )
                values (:id, :now, :now, 0, :name, :code, :englishName, :nativeName)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("now", now)
                        .addValue("name", name)
                        .addValue("code", code)
                        .addValue("englishName", englishName)
                        .addValue("nativeName", nativeName));
        return SeedResult.inserted(id);
    }

    public void deleteById(String tableName, UUID id) {
        namedParameterJdbcTemplate.update(
                "delete from " + tableName + " where id = :id",
                new MapSqlParameterSource("id", id));
    }

    public void deleteFromJoinTable(String tableName, String idColumn, UUID id) {
        namedParameterJdbcTemplate.update(
                "delete from " + tableName + " where " + idColumn + " = :id",
                new MapSqlParameterSource("id", id));
    }

    private RowMapper<DataSourceRead> dataSourceReadMapper() {
        return (rs, rowNum) -> new DataSourceRead(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("base_url"),
                rs.getString("list_path"),
                rs.getString("list_params"),
                rs.getString("detail_path"),
                rs.getString("detail_params"),
                rs.getString("field_mapping"),
                rs.getString("transport_mode"),
                rs.getString("http_protocol"),
                rs.getString("ip_version_policy"),
                rs.getString("dns_resolver_type"),
                rs.getString("dns_resolver_endpoint"),
                (Integer) rs.getObject("connect_timeout_ms"),
                (Integer) rs.getObject("read_timeout_ms"),
                rs.getString("user_agent"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RowMapper<StandardCategoryRead> standardCategoryReadMapper() {
        return (rs, rowNum) -> new StandardCategoryRead(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RowMapper<StandardGenreRead> standardGenreReadMapper() {
        return (rs, rowNum) -> new StandardGenreRead(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("code"));
    }

    private RowMapper<StandardAreaRead> standardAreaReadMapper() {
        return (rs, rowNum) -> new StandardAreaRead(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("region"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RowMapper<StandardLanguageRead> standardLanguageReadMapper() {
        return (rs, rowNum) -> new StandardLanguageRead(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("english_name"),
                rs.getString("native_name"));
    }

    private RowMapper<RawCategoryRead> rawCategoryReadMapper() {
        return (rs, rowNum) -> new RawCategoryRead(
                rs.getObject("id", UUID.class),
                rs.getString("source_code"),
                rs.getString("source_name"),
                rs.getObject("data_source_id", UUID.class),
                rs.getString("data_source_name"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private String rawCategorySelect() {
        return """
                select rc.id,
                       rc.source_code,
                       rc.source_name,
                       rc.data_source_id,
                       ds.name as data_source_name,
                       rc.category_id,
                       sc.name as category_name,
                       rc.created_at,
                       rc.updated_at
                """;
    }

    private String rawCategoryFromWhere(List<String> predicates) {
        return fromWhere(
                """
                raw_category rc
                left join data_sources ds on rc.data_source_id = ds.id
                left join standard_category sc on rc.category_id = sc.id
                """,
                predicates);
    }

    private void addAssignment(
            List<String> assignments,
            MapSqlParameterSource params,
            String column,
            String paramName,
            Object value,
            boolean partial) {
        if (partial && value == null) {
            return;
        }
        assignments.add(column + " = :" + paramName);
        params.addValue(paramName, value);
    }

    private void addJsonAssignment(
            List<String> assignments,
            MapSqlParameterSource params,
            String column,
            String paramName,
            String value,
            boolean partial) {
        if (partial && value == null) {
            return;
        }
        assignments.add(column + " = cast(:" + paramName + " as jsonb)");
        params.addValue(paramName, value);
    }

    private void updateWithAssignments(
            String tableName,
            List<String> assignments,
            MapSqlParameterSource params) {
        namedParameterJdbcTemplate.update(
                "update " + tableName + " set " + String.join(", ", assignments) + " where id = :id",
                params);
    }

    private <T> CatalogPage<T> queryPage(
            String selectSql,
            String fromWhereSql,
            MapSqlParameterSource params,
            CatalogPageRequest pageRequest,
            Map<String, String> sortableColumns,
            String defaultSort,
            String tieBreaker,
            RowMapper<T> mapper) {
        long totalElements = Optional.ofNullable(namedParameterJdbcTemplate.queryForObject(
                        "select count(*) " + fromWhereSql,
                        params,
                        Long.class))
                .orElse(0L);

        params.addValue("limit", pageRequest.size());
        params.addValue("offset", pageRequest.offset());
        String sql = selectSql
                + " "
                + fromWhereSql
                + " order by "
                + resolveSort(pageRequest, sortableColumns, defaultSort, tieBreaker)
                + " limit :limit offset :offset";

        return CatalogPage.of(
                namedParameterJdbcTemplate.query(sql, params, mapper),
                pageRequest,
                totalElements);
    }

    private <T> Optional<T> queryOptional(
            String sql,
            MapSqlParameterSource params,
            RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(namedParameterJdbcTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Optional<UUID> findIdByText(String tableName, String columnName, String value) {
        return queryOptional(
                "select id from " + tableName + " where lower(" + columnName + ") = lower(:value)",
                new MapSqlParameterSource("value", value),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private String stableCode(String preferredCode, String name) {
        String source = StringUtils.hasText(preferredCode) ? preferredCode : name;
        if (!StringUtils.hasText(source)) {
            return "genre-" + UUID.randomUUID();
        }
        String normalized = source.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(normalized)
                ? normalized
                : "genre-" + Integer.toHexString(source.hashCode());
    }

    private String fromWhere(String tableExpression, List<String> predicates) {
        if (predicates.isEmpty()) {
            return "from " + tableExpression;
        }
        return "from " + tableExpression + " where " + String.join(" and ", predicates);
    }

    private void addLikePredicate(
            List<String> predicates,
            MapSqlParameterSource params,
            String column,
            String paramName,
            String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        predicates.add("lower(" + column + ") like :" + paramName);
        params.addValue(paramName, "%" + value.toLowerCase() + "%");
    }

    private void addLikeAnyPredicate(
            List<String> predicates,
            MapSqlParameterSource params,
            String paramName,
            String value,
            String... columns) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        List<String> likes = new ArrayList<>();
        for (String column : columns) {
            likes.add("lower(" + column + ") like :" + paramName);
        }
        predicates.add("(" + String.join(" or ", likes) + ")");
        params.addValue(paramName, "%" + value.toLowerCase() + "%");
    }

    private void addMappedPredicate(
            List<String> predicates,
            String column,
            Boolean isUnmapped,
            boolean falseMeansMapped) {
        if (Boolean.TRUE.equals(isUnmapped)) {
            predicates.add(column + " is null");
        } else if (Boolean.FALSE.equals(isUnmapped) && falseMeansMapped) {
            predicates.add(column + " is not null");
        }
    }

    private String resolveSort(
            CatalogPageRequest pageRequest,
            Map<String, String> sortableColumns,
            String defaultSort,
            String tieBreaker) {
        SortExpression sortExpression = firstSortExpression(pageRequest.sort());
        if (sortExpression == null) {
            return defaultSort;
        }
        String column = sortableColumns.get(sortExpression.property());
        if (column == null) {
            return defaultSort;
        }
        return column + " " + sortExpression.direction() + " nulls last, " + tieBreaker;
    }

    private SortExpression firstSortExpression(List<String> sort) {
        if (sort == null || sort.isEmpty()) {
            return null;
        }
        String property = null;
        String direction = "asc";
        for (String raw : sort) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String[] parts = raw.split(",");
            property = parts[0].trim();
            if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
                direction = "desc";
            } else if (parts.length == 1 && sort.size() > 1 && "desc".equalsIgnoreCase(sort.get(1))) {
                direction = "desc";
            }
            break;
        }
        if (!StringUtils.hasText(property)) {
            return null;
        }
        return new SortExpression(property, direction);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record SortExpression(String property, String direction) {}

    public record SeedResult(UUID id, boolean inserted, boolean updated) {

        static SeedResult inserted(UUID id) {
            return new SeedResult(id, true, false);
        }

        static SeedResult updated(UUID id) {
            return new SeedResult(id, false, true);
        }

        static SeedResult unchanged(UUID id) {
            return new SeedResult(id, false, false);
        }
    }

    private record LanguageSeedRow(UUID id, String code, String englishName, String nativeName) {}

    private record AreaSeedRow(UUID id, String region) {}

    private record RawCategorySeedRow(UUID id, String sourceName, UUID categoryId) {}

    private record DataSourceSeedRow(
            UUID id,
            String baseUrl,
            String listPath,
            String listParams,
            String detailPath,
            String detailParams,
            String fieldMapping) {}

    public record DataSourceSeedMappingRow(UUID id, String name, String fieldMapping) {}
}
