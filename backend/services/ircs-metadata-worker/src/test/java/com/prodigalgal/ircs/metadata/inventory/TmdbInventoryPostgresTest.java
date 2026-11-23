package com.prodigalgal.ircs.metadata.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.metadata.inventory.TmdbInventoryRepository.LeaseLost;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventorySnapshotReader.Entry;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class TmdbInventoryPostgresTest {
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 11, 22);
    private CatalogTestDatabase database;
    private JdbcTemplate jdbc;
    private TmdbInventoryRepository repository;

    @BeforeEach
    void setup() throws Exception {
        database = CatalogTestDatabase.open();
        jdbc = new JdbcTemplate(database.dataSource());
        repository = new TmdbInventoryRepository(new NamedParameterJdbcTemplate(database.dataSource()));
    }

    @AfterEach
    void cleanup() {
        if (database != null) database.close();
    }

    @Test
    void visibilitySwitchesOnlyAfterAnEntireTypedSnapshotCompletes() {
        var old = repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE).orElseThrow();
        assertThat(repository.clearInactiveBatch(old)).isZero();
        jdbc.update("""
                insert into provider_catalog_snapshot_items (media_type, slot, tmdb_id, title, popularity)
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
        assertThat(jdbc.queryForObject("select title from active_provider_catalog_items where tmdb_id=100000", String.class))
                .isEqualTo("Published series");
    }

    @Test
    void expiredLeaseCannotWriteOrClearASlotAfterReclaim() {
        var stale = repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE).orElseThrow();
        var row = new Entry(42, "Film", 4.0, true);
        repository.append(stale, List.of(row));
        assertThat(repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE)).isEmpty();
        assertThat(repository.claim(TmdbInventoryMediaType.TV, FIRST_DATE)).isPresent();
        jdbc.update("update provider_catalog_snapshot_states set lease_until=now()-interval '1 second' where media_type='movie'");
        var current = repository.claim(TmdbInventoryMediaType.MOVIE, FIRST_DATE).orElseThrow();
        assertThat(current.slot()).isEqualTo(stale.slot());
        assertThat(repository.clearInactiveBatch(current)).isEqualTo(1);
        repository.append(current, List.of(row));
        assertThatThrownBy(() -> repository.append(stale, List.of(row))).isInstanceOf(LeaseLost.class);
        assertThatThrownBy(() -> repository.clearInactiveBatch(stale)).isInstanceOf(LeaseLost.class);
        assertThat(repository.publish(stale, 500_000)).isFalse();
        assertThat(visibleCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from provider_catalog_snapshot_items", Integer.class)).isEqualTo(1);
    }

    private int visibleCount() {
        return jdbc.queryForObject("select count(*) from active_provider_catalog_items", Integer.class);
    }
}
