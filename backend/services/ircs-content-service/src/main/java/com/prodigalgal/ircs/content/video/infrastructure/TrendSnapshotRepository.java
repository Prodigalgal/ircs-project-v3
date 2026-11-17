package com.prodigalgal.ircs.content.video.infrastructure;

import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
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

    @Transactional(timeout = 15)
    public boolean save(TrendBoardPayload board, List<Observation> observations) {
        MapSqlParameterSource p = boardParams(board);
        jdbc.update("""
                insert into external_trend_boards(provider, board, media_type, source_window, last_attempt_at, status)
                values (:provider, :board, :type, :window, :at, :status)
                on conflict do nothing
                """, p);
        BoardState state = jdbc.queryForObject("""
                select last_attempt_at, last_success_at from external_trend_boards
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                 for update
                """, p, (rs, n) -> new BoardState(rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()));
        if (state != null && (state.lastAttempt().isAfter(board.collectedAt())
                || board.collectedAt().equals(state.lastSuccess()))) return false;
        if (!"SUCCESS".equals(board.status())) {
            jdbc.update("""
                    update external_trend_boards set last_attempt_at = :at, status = :status, error_code = :error
                     where provider = :provider and board = :board and media_type = :type and source_window = :window
                    """, p);
            return true;
        }
        for (Observation observation : observations) {
            var candidate = observation.candidate();
            var signal = candidate.item().signal();
            MapSqlParameterSource values = boardParams(board).addValue("externalId", candidate.externalId())
                    .addValue("season", candidate.season()).addValue("id", observation.id())
                    .addValue("position", signal.position()).addValue("popularity", signal.popularity())
                    .addValue("votes", signal.voteCount())
                    .addValue("eventDate", signal.eventDate() == null ? null : Date.valueOf(signal.eventDate()))
                    .addValue("eventType", signal.eventType()).addValue("episode", signal.episode())
                    .addValue("region", signal.region()).addValue("url", signal.sourceUrl());
            if (signal.eventDate() != null && signal.eventType() != null
                    && eventHasOccurred(signal.eventDate(), board.collectedAt())) {
                jdbc.update("""
                        insert into external_catalog_events(unified_video_id, provider, external_id, season,
                            episode, event_date, event_type, observed_at, source_url, region)
                        values (:id, :provider, :externalId, :season, coalesce(:episode, 0),
                            :eventDate, :eventType, :at, :url, :region)
                        on conflict (unified_video_id, provider, season, episode, event_date, event_type) do update
                            set observed_at = greatest(external_catalog_events.observed_at, excluded.observed_at),
                                source_url = excluded.source_url
                        """, values);
            }
            jdbc.update("""
                    insert into external_trend_observations(provider, board, media_type, source_window, external_id,
                        season, unified_video_id, position, popularity, vote_count, observed_at,
                        event_date, event_type, episode, region, source_url)
                    values (:provider, :board, :type, :window, :externalId, :season, :id, :position, :popularity,
                        :votes, :at, :eventDate, :eventType, :episode, :region, :url)
                    on conflict (provider, board, media_type, source_window, external_id, season) do update
                        set previous_position = case when excluded.observed_at > external_trend_observations.observed_at
                                then external_trend_observations.position else external_trend_observations.previous_position end,
                            unified_video_id = excluded.unified_video_id, position = excluded.position,
                            popularity = excluded.popularity, vote_count = excluded.vote_count,
                            observed_at = excluded.observed_at, event_date = excluded.event_date,
                            event_type = excluded.event_type, episode = excluded.episode,
                            region = excluded.region, source_url = excluded.source_url
                    """, values);
        }
        jdbc.update("""
                delete from external_trend_observations
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                   and observed_at <> :at
                """, p);
        jdbc.update("""
                update external_trend_boards set last_attempt_at = :at, last_success_at = :at,
                    status = 'SUCCESS', error_code = :error, item_count = :count
                 where provider = :provider and board = :board and media_type = :type and source_window = :window
                """, p.addValue("count", observations.size()));
        return true;
    }

    private MapSqlParameterSource boardParams(TrendBoardPayload board) {
        return new MapSqlParameterSource("provider", board.provider()).addValue("board", board.board())
                .addValue("type", board.mediaType()).addValue("window", board.window())
                .addValue("at", Timestamp.from(board.collectedAt())).addValue("status", board.status())
                .addValue("error", board.errorCode() != null && board.errorCode().matches("[A-Z0-9_:-]{1,120}")
                        ? board.errorCode() : null);
    }

    public record Observation(TrendCatalogCandidate candidate, UUID id) { }
    private record BoardState(Instant lastAttempt, Instant lastSuccess) { }

    static boolean eventHasOccurred(java.time.LocalDate eventDate, Instant sampledAt) {
        return !eventDate.isAfter(sampledAt.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate());
    }
}
