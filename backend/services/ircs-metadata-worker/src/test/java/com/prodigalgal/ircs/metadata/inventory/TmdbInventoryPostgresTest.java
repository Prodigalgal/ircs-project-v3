package com.prodigalgal.ircs.metadata.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.metadata.inventory.TmdbInventoryRepository.LeaseLost;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class TmdbInventoryPostgresTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 11, 22);
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private TmdbInventoryRepository repository;
    private String schema;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "tmdb_inventory_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(2);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        jdbc.execute(Files.readString(migration()));
        repository = new TmdbInventoryRepository(new NamedParameterJdbcTemplate(pool));
    }

    @AfterEach
    void cleanup() {
        if (pool != null) {
            pool.close();
        }
        if (admin != null && schema != null) {
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void visibilitySwitchesOnlyAfterAnEntireTypedSnapshotCompletes() {
        var old = repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE).orElseThrow();
        assertThat(repository.clearInactiveBatch(old)).isZero();
        jdbc.update("""
                insert into authority_tmdb_inventory_entries (media_type, slot, tmdb_id, title, popularity)
                select 'tv', ?, id, 'Published', 0 from generate_series(1, 99999) id
                """, old.slot());
        repository.append(old, List.of(new Entry(100_000, "Published series", 5.5, false)));
        assertThat(visibleCount()).isZero();
        assertThat(repository.publish(old, 100_000)).isTrue();
        assertThat(visibleCount()).isEqualTo(100_000);

        var next = repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE.plusDays(1)).orElseThrow();
        repository.append(next, List.of(new Entry(100_000, "Incomplete series", 6.3, false)));
        assertThat(repository.publish(next, 1)).isFalse();
        repository.fail(next, "SNAPSHOT_INCOMPLETE");
        assertThat(repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE.plusDays(1))).isEmpty();
        assertThat(visibleCount()).isEqualTo(100_000);
        assertThat(jdbc.queryForObject("select title from authority_tmdb_inventory where tmdb_id=100000", String.class))
                .isEqualTo("Published series");
    }

    @Test
    void expiredLeaseCannotWriteOrClearASlotAfterReclaim() {
        var stale = repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE).orElseThrow();
        var row = new Entry(42, "Film", 4.0, true);
        repository.append(stale, List.of(row));
        assertThat(repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE)).isEmpty();
        assertThat(repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE)).isPresent();
        jdbc.update("update authority_tmdb_inventory_state set lease_until=now()-interval '1 second' where media_type='movie'");
        var current = repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE).orElseThrow();
        assertThat(current.slot()).isEqualTo(stale.slot());
        assertThat(repository.clearInactiveBatch(current)).isEqualTo(1);
        repository.append(current, List.of(row));
        assertThatThrownBy(() -> repository.append(stale, List.of(row))).isInstanceOf(LeaseLost.class);
        assertThatThrownBy(() -> repository.clearInactiveBatch(stale)).isInstanceOf(LeaseLost.class);
        assertThat(repository.publish(stale, 500_000)).isFalse();
        assertThat(visibleCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from authority_tmdb_inventory_entries", Integer.class)).isEqualTo(1);
    }

    private int visibleCount() {
        return jdbc.queryForObject("select count(*) from authority_tmdb_inventory", Integer.class);
    }

    private Path migration() {
        Path relative = Path.of("backend/platform/ircs-migrator/src/main/resources/db/changelog/2026/11/23-02-tmdb-authority-inventory.sql");
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(relative))) {
                return root.resolve(relative);
            }
        }
        throw new IllegalStateException("TMDB inventory migration missing");
    }
}
