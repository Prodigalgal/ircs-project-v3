package com.prodigalgal.ircs.content.image;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CoverImageReferenceRepository {

    private static final String SELECT_SOURCE_RECORD_IDS =
            "select id from source_records where media_image_source_id = ?";
    private static final String SELECT_CATALOG_ENTRY_IDS =
            "select id from catalog_entries where media_image_source_id = ?";
    private static final String UNLINK_SOURCE_RECORD =
            "update source_records set media_image_source_id = null where media_image_source_id = ?";
    private static final String UNLINK_CATALOG_ENTRY =
            "update catalog_entries set media_image_source_id = null where media_image_source_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public List<UUID> findSourceRecordIds(UUID imageId) {
        return jdbcTemplate.query(SELECT_SOURCE_RECORD_IDS, (rs, rowNum) -> (UUID) rs.getObject("id"), imageId);
    }

    public List<UUID> findCatalogEntryIds(UUID imageId) {
        return jdbcTemplate.query(SELECT_CATALOG_ENTRY_IDS, (rs, rowNum) -> (UUID) rs.getObject("id"), imageId);
    }

    public int unlinkSourceRecords(UUID imageId) {
        return jdbcTemplate.update(UNLINK_SOURCE_RECORD, imageId);
    }

    public int unlinkCatalogEntries(UUID imageId) {
        return jdbcTemplate.update(UNLINK_CATALOG_ENTRY, imageId);
    }
}
