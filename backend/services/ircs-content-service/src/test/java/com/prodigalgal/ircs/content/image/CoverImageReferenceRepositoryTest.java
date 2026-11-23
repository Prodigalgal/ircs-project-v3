package com.prodigalgal.ircs.content.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CoverImageReferenceRepositoryTest {

    @Test
    void findsSourceRecordIdsByCoverImageId() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        UUID rawId = UUID.randomUUID();
        when(jdbcTemplate.query(
                org.mockito.Mockito.eq("select id from source_records where media_image_source_id = ?"),
                org.mockito.Mockito.<RowMapper<UUID>>any(),
                org.mockito.Mockito.eq(id)))
                .thenReturn(List.of(rawId));

        List<UUID> ids = new CoverImageReferenceRepository(jdbcTemplate).findSourceRecordIds(id);

        assertEquals(List.of(rawId), ids);
        verify(jdbcTemplate).query(
                org.mockito.Mockito.eq("select id from source_records where media_image_source_id = ?"),
                org.mockito.Mockito.<RowMapper<UUID>>any(),
                org.mockito.Mockito.eq(id));
    }

    @Test
    void findsCatalogEntryIdsByCoverImageId() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        UUID unifiedId = UUID.randomUUID();
        when(jdbcTemplate.query(
                org.mockito.Mockito.eq("select id from catalog_entries where media_image_source_id = ?"),
                org.mockito.Mockito.<RowMapper<UUID>>any(),
                org.mockito.Mockito.eq(id)))
                .thenReturn(List.of(unifiedId));

        List<UUID> ids = new CoverImageReferenceRepository(jdbcTemplate).findCatalogEntryIds(id);

        assertEquals(List.of(unifiedId), ids);
        verify(jdbcTemplate).query(
                org.mockito.Mockito.eq("select id from catalog_entries where media_image_source_id = ?"),
                org.mockito.Mockito.<RowMapper<UUID>>any(),
                org.mockito.Mockito.eq(id));
    }

    @Test
    void unlinksSourceRecordsByCoverImageId() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update("update source_records set media_image_source_id = null where media_image_source_id = ?", id))
                .thenReturn(3);

        int count = new CoverImageReferenceRepository(jdbcTemplate).unlinkSourceRecords(id);

        assertEquals(3, count);
        verify(jdbcTemplate).update("update source_records set media_image_source_id = null where media_image_source_id = ?", id);
    }

    @Test
    void unlinksCatalogEntriesByCoverImageId() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update("update catalog_entries set media_image_source_id = null where media_image_source_id = ?", id))
                .thenReturn(1);

        int count = new CoverImageReferenceRepository(jdbcTemplate).unlinkCatalogEntries(id);

        assertEquals(1, count);
        verify(jdbcTemplate).update("update catalog_entries set media_image_source_id = null where media_image_source_id = ?", id);
    }
}
