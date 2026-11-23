package com.prodigalgal.ircs.content.video.infrastructure;

import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TrendSnapshotRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String UPSERT_EVENT = """
            insert into catalog_release_events(catalog_entry_id, provider, external_id, season,
                episode, event_date, event_type, observed_at, source_url, region)
            values (:id, :provider, :externalId, :season, coalesce(:episode, 0),
                :eventDate, :eventType, :at, :url, :region)
            on conflict (catalog_entry_id, provider, season, episode, event_date, event_type) do update
                set observed_at = greatest(catalog_release_events.observed_at, excluded.observed_at),
                    source_url = excluded.source_url
            """;

    private static final String UPSERT_OBSERVATION = """
            insert into provider_ranking_observations(provider, board, media_type, source_window, external_id,
                season, catalog_entry_id, position, popularity, vote_count, observed_at,
                event_date, event_type, episode, region, source_url)
            values (:provider, :board, :type, :window, :externalId, :season, :id, :position, :popularity,
                :votes, :at, :eventDate, :eventType, :episode, :region, :url)
            on conflict (provider, board, media_type, source_window, external_id, season) do update
                set previous_position = case when excluded.observed_at > provider_ranking_observations.observed_at
                        then provider_ranking_observations.position else provider_ranking_observations.previous_position end,
                    catalog_entry_id = excluded.catalog_entry_id, position = excluded.position,
                    popularity = excluded.popularity, vote_count = excluded.vote_count,
                    observed_at = excluded.observed_at, event_date = excluded.event_date,
                    event_type = excluded.event_type, episode = excluded.episode,
                    region = excluded.region, source_url = excluded.source_url
            """;

    @Transactional(timeout = 15)
    public boolean save(TrendBoardPayload board, List<Observation> observations) {
        // timestamptz has microsecond precision. Compare the same value that is sent to PostgreSQL.
        Instant sampledAt = board.collectedAt().truncatedTo(ChronoUnit.MICROS);
        MapSqlParameterSource p = boardParams(board, sampledAt);
        jdbc.update("""
                insert into provider_ranking_boards(provider, board, media_type, source_window, last_attempt_at, status)
                values (:provider, :board, :type, :window, :at, :status)
                on conflict do nothing
                """, p);
        BoardState state = jdbc.queryForObject("""
                select last_attempt_at, last_success_at from provider_ranking_boards
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                 for update
                """, p, (rs, n) -> new BoardState(rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()));
        if (state != null && (state.lastAttempt().isAfter(sampledAt)
                || sampledAt.equals(state.lastSuccess()))) return false;
        if (!"SUCCESS".equals(board.status())) {
            jdbc.update("""
                    update provider_ranking_boards set last_attempt_at = :at, status = :status, error_code = :error
                     where provider = :provider and board = :board and media_type = :type and source_window = :window
                    """, p);
            return true;
        }
        saveObservations(board, sampledAt, observations);
        jdbc.update("""
                delete from provider_ranking_observations
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                   and observed_at <> :at
                """, p);
        jdbc.update("""
                update provider_ranking_boards set last_attempt_at = :at, last_success_at = :at,
                    status = 'SUCCESS', error_code = :error, item_count = :count
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                """, p.addValue("count", observations.size()));
        return true;
    }

    private void saveObservations(TrendBoardPayload board, Instant sampledAt, List<Observation> observations) {
        if (observations.isEmpty()) return;
        var ranked = new MapSqlParameterSource[observations.size()];
        var events = new ArrayList<MapSqlParameterSource>();
        for (int index = 0; index < observations.size(); index++) {
            Observation observation = observations.get(index);
            var candidate = observation.candidate();
            var signal = candidate.item().signal();
            MapSqlParameterSource values = boardParams(board, sampledAt).addValue("externalId", candidate.externalId())
                    .addValue("season", candidate.season()).addValue("id", observation.id())
                    .addValue("position", signal.position()).addValue("popularity", signal.popularity())
                    .addValue("votes", signal.voteCount())
                    .addValue("eventDate", signal.eventDate() == null ? null : Date.valueOf(signal.eventDate()))
                    .addValue("eventType", signal.eventType()).addValue("episode", signal.episode())
                    .addValue("region", signal.region()).addValue("url", signal.sourceUrl());
            ranked[index] = values;
            if (signal.eventDate() != null && signal.eventType() != null
                    && eventHasOccurred(signal.eventDate(), sampledAt)) {
                events.add(values);
            }
        }
        if (!events.isEmpty()) jdbc.batchUpdate(UPSERT_EVENT, events.toArray(MapSqlParameterSource[]::new));
        jdbc.batchUpdate(UPSERT_OBSERVATION, ranked);
    }

    private MapSqlParameterSource boardParams(TrendBoardPayload board, Instant sampledAt) {
        return new MapSqlParameterSource("provider", board.provider()).addValue("board", board.board())
                .addValue("type", board.mediaType()).addValue("window", board.window())
                .addValue("at", Timestamp.from(sampledAt)).addValue("status", board.status())
                .addValue("error", board.errorCode() != null && board.errorCode().matches("[A-Z0-9_:-]{1,120}")
                        ? board.errorCode() : null);
    }

    public record Observation(TrendCatalogCandidate candidate, UUID id) { }
    private record BoardState(Instant lastAttempt, Instant lastSuccess) { }

    static boolean eventHasOccurred(java.time.LocalDate eventDate, Instant sampledAt) {
        return !eventDate.isAfter(sampledAt.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate());
    }
}
