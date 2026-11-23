-- liquibase formatted sql

-- changeset codex:1795380000006-authority-binding-integrity
DELETE FROM authority_resource_bindings AS evidence
 WHERE NOT EXISTS (
    SELECT 1 FROM raw_video_unified_video AS link
     WHERE link.raw_video_id = evidence.raw_video_id
       AND link.unified_video_id = evidence.unified_video_id
 );

ALTER TABLE authority_resource_bindings
    ADD CONSTRAINT fk_authority_evidence_current_binding
    FOREIGN KEY (raw_video_id, unified_video_id)
    REFERENCES raw_video_unified_video (raw_video_id, unified_video_id)
    ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

-- rollback ALTER TABLE authority_resource_bindings DROP CONSTRAINT fk_authority_evidence_current_binding;
