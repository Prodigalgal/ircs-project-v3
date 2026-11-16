package com.prodigalgal.ircs.metadata.result.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prodigalgal.ircs.metadata.result.domain.RawVideoMetadataRecord;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class RawVideoMetadataProjectionIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private RawVideoMetadataRepository repository;
    private TransactionTemplate transaction;
    private String schema;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        admin = new JdbcTemplate(source);
        schema = "metadata_projection_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        var scoped = new DriverManagerDataSource(source.getUrl(), source.getUsername(), source.getPassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        scoped.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(scoped);
        repository = new RawVideoMetadataRepository(jdbc, mapper);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(scoped));
        jdbc.execute("""
                create table raw_videos(
                    id uuid primary key,douban_id text,tmdb_id text,imdb_id text,rotten_tomatoes_id text,
                    description text,score numeric,year text,alias_title text,raw_metadata jsonb,
                    locked_fields jsonb default '[]',enrichment_status text default 'PENDING',
                    enrichment_retry_count integer default 0,aggregation_status text default 'BOUND',
                    data_hash text,actor_names jsonb default '[]',director_names jsonb default '[]',
                    area_codes jsonb default '[]',language_codes jsonb default '[]',genre_codes jsonb default '[]',
                    updated_at timestamptz default now());
                create table standard_areas(code text primary key,name text);
                create table standard_languages(code text primary key,name text,english_name text,native_name text);
                create table standard_genre(code text primary key,name text);
                insert into standard_areas values('CN','中国大陆'),('US','美国');
                insert into standard_languages values('zh-CN','国语','Mandarin','普通话'),('en','英语','English','English');
                insert into standard_genre values('action','动作'),('sci-fi','科幻');
                """);
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            // This randomly named schema belongs exclusively to this test.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void readsExistingProjectionAndAtomicallyProjectsRichMetadataWithoutChangingSourceEvidence() {
        String raw = """
                {"actorNames":["罗予彤＆孙乾钊","罗予彤","Robert Downey Jr.","R&D","未知"],
                 "directorNames":"张三&amp;李四/Christopher Nolan", "area":"中国大陆,美国/unknown",
                 "language":"国语/English/unknown", "genreNames":["动作","科幻","unknown"],
                 "doubanId":0,"sourcePayload":{"vod_douban_id":0,"name":"原题"},"sourceNote":"keep"}
                """;
        UUID id = insert(raw);
        jdbc.update("update raw_videos set actor_names='[\"既有演员\"]',director_names='[\"Existing Director\"]',area_codes='[\"HK\"]',language_codes='[\"ja\"]',genre_codes='[\"drama\"]' where id=?", id);
        RawVideoMetadataRecord record = repository.findById(id).orElseThrow();
        assertThat(record.getActorNames()).containsExactly("既有演员");
        assertThat(record.getDirectorNames()).containsExactly("Existing Director");
        assertThat(record.getAreaCodes()).containsExactly("HK");
        assertThat(record.getLanguageCodes()).containsExactly("ja");
        assertThat(record.getGenreCodes()).containsExactly("drama");
        record.setTmdbId("101");

        assertThat(transaction.execute(status -> repository.save(record))).isEqualTo(1);

        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(stored.getTmdbId()).isEqualTo("101");
        assertThat(stored.getActorNames()).containsExactlyInAnyOrder("既有演员", "罗予彤", "孙乾钊", "Robert Downey Jr.", "R&D");
        assertThat(stored.getDirectorNames()).containsExactlyInAnyOrder("Existing Director", "张三", "李四", "Christopher Nolan");
        assertThat(stored.getAreaCodes()).containsExactlyInAnyOrder("HK", "CN", "US");
        assertThat(stored.getLanguageCodes()).containsExactlyInAnyOrder("ja", "zh-CN", "en");
        assertThat(stored.getGenreCodes()).containsExactlyInAnyOrder("drama", "action", "sci-fi");
        assertThat(mapper.readTree(stored.getRawMetadata())).isEqualTo(mapper.readTree(raw));
        assertThat(mapper.readTree(stored.getRawMetadata()).path("sourcePayload").path("vod_douban_id").isInt()).isTrue();
    }

    @Test
    void blankPeopleAndUnknownDictionaryValuesDoNotCreateBogusProjection() {
        UUID id = insert("""
                {"actorNames":["", "　", "未知", "N/A", "null"], "directors":["unknown","暂无"],
                 "area":"unknown", "language":"unknown", "genres":["unknown"]}
                """);
        RawVideoMetadataRecord record = repository.findById(id).orElseThrow();
        assertThat(transaction.execute(status -> repository.save(record))).isEqualTo(1);
        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(stored.getActorNames()).isEmpty();
        assertThat(stored.getDirectorNames()).isEmpty();
        assertThat(stored.getAreaCodes()).isEmpty();
        assertThat(stored.getLanguageCodes()).isEmpty();
        assertThat(stored.getGenreCodes()).isEmpty();
        assertThat(mapper.readTree(stored.getRawMetadata()).path("actorNames").size()).isEqualTo(5);
    }

    @Test
    void databaseFieldLocksProtectExistingProjectionEvenIfCallerSuppliesDifferentSets() {
        UUID id = insert("{\"actorNames\":[\"来源演员\"],\"directorNames\":[\"来源导演\"],\"area\":\"美国\",\"language\":\"英语\",\"genres\":[\"科幻\"]}");
        jdbc.update("""
                update raw_videos set locked_fields='["actors","directors","area","language","genres"]',
                    actor_names='["人工演员"]',director_names='["人工导演"]',area_codes='["CN"]',
                    language_codes='["zh-CN"]',genre_codes='["action"]' where id=?
                """, id);
        RawVideoMetadataRecord record = repository.findById(id).orElseThrow();
        record.setActorNames(Set.of("绕过演员"));
        record.setDirectorNames(Set.of("绕过导演"));
        record.setAreaCodes(Set.of("US"));
        record.setLanguageCodes(Set.of("en"));
        record.setGenreCodes(Set.of("sci-fi"));
        assertThat(transaction.execute(status -> repository.save(record))).isEqualTo(1);
        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(stored.getActorNames()).containsExactly("人工演员");
        assertThat(stored.getDirectorNames()).containsExactly("人工导演");
        assertThat(stored.getAreaCodes()).containsExactly("CN");
        assertThat(stored.getLanguageCodes()).containsExactly("zh-CN");
        assertThat(stored.getGenreCodes()).containsExactly("action");
    }

    @Test
    void scalarFieldLocksCannotBeBypassedByMutatingLoadedRecord() {
        UUID id = insert("{}");
        jdbc.update("""
                update raw_videos set locked_fields='["doubanId","tmdbId","imdbId","rottenTomatoesId","description","score","year","aliasTitle"]',
                    douban_id='123',tmdb_id='456',imdb_id='tt1234567',rotten_tomatoes_id='manual_slug',
                    description='人工简介',score=8.5,year='2025',alias_title='人工别名' where id=?
                """, id);
        RawVideoMetadataRecord record = repository.findById(id).orElseThrow();
        record.setDoubanId("spoof");
        record.setTmdbId("spoof");
        record.setImdbId("spoof");
        record.setRottenTomatoesId("spoof");
        record.setDescription("spoof");
        record.setScore(java.math.BigDecimal.ONE);
        record.setYear("2030");
        record.setAliasTitle("spoof");

        assertThat(transaction.execute(status -> repository.save(record))).isEqualTo(1);

        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(stored.getDoubanId()).isEqualTo("123");
        assertThat(stored.getTmdbId()).isEqualTo("456");
        assertThat(stored.getImdbId()).isEqualTo("tt1234567");
        assertThat(stored.getRottenTomatoesId()).isEqualTo("manual_slug");
        assertThat(stored.getDescription()).isEqualTo("人工简介");
        assertThat(stored.getScore()).isEqualByComparingTo("8.5");
        assertThat(stored.getYear()).isEqualTo("2025");
        assertThat(stored.getAliasTitle()).isEqualTo("人工别名");
    }

    @Test
    void changedDataHashRejectsBothJsonAndProjectionFromOldProviderResult() {
        UUID id = insert("{\"actorNames\":[\"旧演员\"]}");
        RawVideoMetadataRecord stale = repository.findById(id).orElseThrow();
        stale.setRawMetadata("{\"actorNames\":[\"旧provider演员\"]}");
        stale.setTmdbId("101");
        jdbc.update("update raw_videos set data_hash='hash-v2',raw_metadata='{\"actorNames\":[\"新采演员\"]}',actor_names='[\"新采演员\"]' where id=?", id);

        assertThat(transaction.execute(status -> repository.save(stale))).isZero();

        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(stored.getDataHash()).isEqualTo("hash-v2");
        assertThat(stored.getTmdbId()).isNull();
        assertThat(stored.getActorNames()).containsExactly("新采演员");
        assertThat(mapper.readTree(stored.getRawMetadata()).path("actorNames").get(0).asText()).isEqualTo("新采演员");
    }

    @Test
    void immediateDispatchStatusUpdateUsesDataHashCompareAndSet() {
        UUID id = insert("{}");
        var dispatchRepository = new com.prodigalgal.ircs.metadata.dispatch.infrastructure.RawVideoEnrichmentRepository(jdbc);
        jdbc.update("update raw_videos set data_hash='hash-v2' where id=?", id);
        assertThat(dispatchRepository.updateStatus(id, "hash-v1", "SUCCESS", 0)).isZero();
        assertThat(repository.findById(id).orElseThrow().getEnrichmentStatus()).isEqualTo("PENDING");
        assertThat(dispatchRepository.updateStatus(id, "hash-v2", "PERMANENT_FAILURE", 0)).isEqualTo(1);
        assertThat(repository.findById(id).orElseThrow().getEnrichmentStatus()).isEqualTo("PERMANENT_FAILURE");
    }

    @Test
    void rollbackRestoresJsonAndStructuredProjectionTogether() {
        UUID id = insert("{\"actorNames\":[\"原始演员\"],\"sourcePayload\":{\"id\":0}}");
        RawVideoMetadataRecord record = repository.findById(id).orElseThrow();
        JsonNode before = mapper.readTree(record.getRawMetadata());
        record.setRawMetadata("{\"actorNames\":[\"provider演员\"]}");
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            assertThat(repository.save(record)).isEqualTo(1);
            throw new IllegalStateException("force transaction rollback");
        }));
        RawVideoMetadataRecord stored = repository.findById(id).orElseThrow();
        assertThat(mapper.readTree(stored.getRawMetadata())).isEqualTo(before);
        assertThat(stored.getActorNames()).isEmpty();
    }

    private UUID insert(String metadata) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into raw_videos(id,data_hash,raw_metadata) values (?,'hash-v1',cast(? as jsonb))", id, metadata);
        return id;
    }
}
