package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NotificationMailSendHistoryChangelogTest {

    private static final String MAIL_HISTORY_CHANGELOG =
            "db/changelog/2026/07/add-notification-mail-send-history.sql";
    private static final String AUDIT_GOVERNANCE_CHANGELOG =
            "db/changelog/2026/07/add-audit-archive-retention-governance.sql";
    private static final String SEARCH_OUTBOX_REPAIR_CHANGELOG =
            "db/changelog/2026/07/repair-search-outbox-schema.sql";

    @Test
    void masterChangelogIncludesNotificationMailSendHistory() throws IOException {
        String master = readClasspath("db/changelog/db.changelog-master.yaml");

        assertThat(master).contains("file: " + MAIL_HISTORY_CHANGELOG);
        assertThat(master).contains("file: " + AUDIT_GOVERNANCE_CHANGELOG);
        assertThat(master).contains("file: " + SEARCH_OUTBOX_REPAIR_CHANGELOG);
    }

    @Test
    void mailHistoryChangelogKeepsOriginalAppliedSchema() throws IOException {
        String migrator = normalize(readClasspath(MAIL_HISTORY_CHANGELOG));

        assertThat(migrator).contains("liquibase formatted sql");
        assertThat(migrator).contains("changeset prodigalgal:20260609-add-notification-mail-send-history");
        assertThat(migrator).contains("CREATE TABLE IF NOT EXISTS notification_mail_send_history");
        assertThat(migrator).contains("idx_notification_mail_send_history_created");
        assertThat(migrator).contains("idx_notification_mail_send_history_status_mode_created");
        assertThat(migrator).contains("idx_notification_mail_send_history_template_created");
        assertThat(migrator).contains("idx_notification_mail_send_history_correlation");
        assertThat(migrator).doesNotContain("audit_class");
        assertThat(migrator).doesNotContain("audit_es_replication_outbox");
        assertThat(migrator).doesNotContain("audit_archive_entries");
    }

    @Test
    void auditGovernanceChangelogUpgradesMailHistoryContract() throws IOException {
        String governance = normalize(readClasspath(AUDIT_GOVERNANCE_CHANGELOG));

        assertThat(governance).contains("ALTER TABLE notification_mail_send_history");
        assertThat(governance).contains("ADD COLUMN IF NOT EXISTS audit_class VARCHAR(32)");
        assertThat(governance).contains("idx_notification_mail_send_history_audit_class_created");
        assertThat(governance).contains("CREATE TABLE IF NOT EXISTS audit_es_replication_outbox");
        assertThat(governance).contains("CREATE TABLE IF NOT EXISTS audit_archive_entries");
    }

    @Test
    void searchOutboxRepairChangelogIsIdempotentAndRuntimeReady() throws IOException {
        String repair = normalize(readClasspath(SEARCH_OUTBOX_REPAIR_CHANGELOG));

        assertThat(repair).contains("liquibase formatted sql");
        assertThat(repair).contains("CREATE TABLE IF NOT EXISTS search_sync_tasks");
        assertThat(repair).contains("ADD COLUMN IF NOT EXISTS updated_at");
        assertThat(repair).contains("ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128)");
        assertThat(repair).contains("ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITHOUT TIME ZONE");
        assertThat(repair).contains("ADD COLUMN IF NOT EXISTS last_error TEXT");
        assertThat(repair).contains("CREATE INDEX IF NOT EXISTS idx_search_sync_poll");
        assertThat(repair).contains("CREATE INDEX IF NOT EXISTS idx_search_sync_processing_lease");
        assertThat(repair).contains("CREATE INDEX IF NOT EXISTS idx_search_sync_entity_status");
    }

    private static String readClasspath(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        assertThat(resource.exists()).as("classpath resource %s exists", path).isTrue();
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
