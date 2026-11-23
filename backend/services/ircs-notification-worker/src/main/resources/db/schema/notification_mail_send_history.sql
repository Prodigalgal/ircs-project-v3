CREATE TABLE IF NOT EXISTS mail_delivery_attempts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    audit_class VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    correlation_id VARCHAR(128),
    recipient VARCHAR(320),
    subject VARCHAR(512),
    template_code VARCHAR(256),
    delivery_mode VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    credential_id UUID,
    failure_code VARCHAR(256),
    failure_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_notification_mail_send_history_created
    ON mail_delivery_attempts (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_mail_send_history_status_mode_created
    ON mail_delivery_attempts (status, delivery_mode, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_mail_send_history_template_created
    ON mail_delivery_attempts (template_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_mail_send_history_correlation
    ON mail_delivery_attempts (correlation_id);

CREATE INDEX IF NOT EXISTS idx_notification_mail_send_history_audit_class_created
    ON mail_delivery_attempts (audit_class, created_at DESC);

CREATE TABLE IF NOT EXISTS archived_audit_events (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    audit_class VARCHAR(32) NOT NULL,
    archive_type VARCHAR(64) NOT NULL,
    source_table VARCHAR(128) NOT NULL,
    source_id UUID NOT NULL,
    source_created_at TIMESTAMPTZ,
    retention_days INTEGER,
    reason VARCHAR(256),
    payload TEXT
);
