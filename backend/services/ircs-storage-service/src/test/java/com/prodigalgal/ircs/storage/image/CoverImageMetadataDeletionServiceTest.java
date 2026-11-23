package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CoverImageMetadataDeletionServiceTest {

    private static final String SHARED_KEY = "covers/shared.webp";

    @TempDir
    Path storageRoot;

    private JdbcTemplate jdbc;
    private ImageDeletionConsumer consumer;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        var repository = new CoverImageDeletionRepository(new NamedParameterJdbcTemplate(source));
        consumer = new ImageDeletionConsumer(
                new StorageUuidMessageParser(), new CoverImageMetadataDeletionService(repository));
        jdbc.execute("""
                create table cover_images (
                    id uuid primary key, storage_type varchar(20), storage_path varchar(100)
                )
                """);
        jdbc.execute("create table raw_videos (cover_image_id uuid references cover_images(id))");
        jdbc.execute("create table unified_videos (cover_image_id uuid references cover_images(id))");
    }

    @Test
    void repeatedUnlinkPreservesTheSharedLocalCopyAndOtherCover() {
        UUID remoteCover = insertCover("R2");
        UUID localCover = insertCover("LOCAL");
        jdbc.update("insert into raw_videos (cover_image_id) values (?)", localCover);
        var localStorage = new LocalObjectStorage(storageRoot.toString());
        localStorage.store(new byte[] {1}, SHARED_KEY, "image/webp");

        Message unlink = unlinkedEvent(remoteCover);
        consumer.onMessage(unlink);
        consumer.onMessage(unlink);

        assertThat(jdbc.queryForList("select id from cover_images", UUID.class)).containsExactly(localCover);
        assertThat(localStorage.exists(SHARED_KEY)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw_videos", "unified_videos"})
    void liveReferenceBlocksMetadataDeletion(String referencedFrom) {
        UUID cover = insertCover("LOCAL");
        jdbc.update("insert into " + referencedFrom + " (cover_image_id) values (?)", cover);

        Message unlink = unlinkedEvent(cover);

        assertThatThrownBy(() -> consumer.onMessage(unlink)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("select id from cover_images", UUID.class)).containsExactly(cover);
    }

    private UUID insertCover(String storageType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into cover_images (id, storage_type, storage_path) values (?, ?, ?)",
                id, storageType, SHARED_KEY);
        return id;
    }

    private Message unlinkedEvent(UUID imageId) {
        return new Message(imageId.toString().getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
