package com.prodigalgal.ircs.scraper;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SourceRecordRefetchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    Optional<SourceRecordRefetchTarget> findTarget(UUID sourceRecordId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select rv.id, rv.source_vid, rv.collection_source_id as collection_source_id
                    from source_records rv
                    where rv.id = :id
                    """,
                    new MapSqlParameterSource("id", sourceRecordId),
                    (rs, rowNum) -> new SourceRecordRefetchTarget(
                            rs.getObject("id", UUID.class),
                            rs.getString("source_vid"),
                            rs.getObject("collection_source_id", UUID.class))));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    record SourceRecordRefetchTarget(UUID id, String sourceVid, UUID dataSourceId) {
    }
}
