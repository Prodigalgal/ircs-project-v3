package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CoverImageUploadStateTest {

    private final UUID imageId = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private CoverImageAdminRepository repository;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        repository = new CoverImageAdminRepository(new NamedParameterJdbcTemplate(source), mock(CoverImageUrlResolver.class));
        jdbc.execute("""
                create table cover_images (
                  id uuid primary key, storage_type varchar(20), storage_path varchar(100),
                  status varchar(30), retry_count int, next_retry_time timestamp,
                  last_error varchar(500), updated_at timestamp
                )
                """);
        jdbc.update("insert into cover_images(id,storage_type,storage_path,status) values (?, 'LOCAL', 'covers/a.png', 'LOCAL_STORED')", imageId);
    }

    @Test
    void pendingDeletionPreventsStartingAnUpload() {
        repository.markPendingDelete(imageId);

        assertThat(repository.markUploading(imageId)).isFalse();
        assertThat(status()).isEqualTo("PENDING_DELETE");
    }

    @Test
    void uploadFinishingDuringDeletionPreservesDeleteIntentAndTracksRemoteObject() {
        assertThat(repository.markUploading(imageId)).isTrue();
        repository.markPendingDelete(imageId);

        assertThat(repository.finalizeUpload(imageId, "covers/a.png")).isTrue();

        assertThat(status()).isEqualTo("PENDING_DELETE");
        assertThat(jdbc.queryForObject("select storage_type from cover_images where id = ?", String.class, imageId)).isEqualTo("R2");
        assertThat(jdbc.queryForObject("select next_retry_time from cover_images where id = ?", java.sql.Timestamp.class, imageId)).isNotNull();
    }

    @Test
    void staleUploadCannotOverwriteAResetStoragePath() {
        repository.markUploading(imageId);
        jdbc.update("update cover_images set storage_path='covers/new.png' where id=?", imageId);

        assertThat(repository.finalizeUpload(imageId, "covers/a.png")).isFalse();
        assertThat(jdbc.queryForObject("select storage_path from cover_images where id = ?", String.class, imageId)).isEqualTo("covers/new.png");
    }

    private String status() {
        return jdbc.queryForObject("select status from cover_images where id = ?", String.class, imageId);
    }
}
