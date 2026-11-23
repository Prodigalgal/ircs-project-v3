--liquibase formatted sql

--changeset codex:1795470000003-source-record-versions
ALTER TABLE source_records
    ADD CONSTRAINT uq_source_records_upstream UNIQUE (collection_source_id, source_vid),
    ADD COLUMN current_observed_at timestamptz,
    ADD COLUMN current_version_id uuid,
    ADD COLUMN playback_version_id uuid;

CREATE TABLE source_record_versions (
    id uuid PRIMARY KEY,
    source_record_id uuid NOT NULL REFERENCES source_records(id) ON DELETE CASCADE,
    data_hash varchar(64) NOT NULL CHECK (btrim(data_hash) <> ''),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    observed_at timestamptz NOT NULL,
    CONSTRAINT uq_source_record_versions_content UNIQUE (source_record_id, data_hash),
    CONSTRAINT uq_source_record_versions_owner UNIQUE (source_record_id, id),
    CONSTRAINT uq_source_record_versions_current UNIQUE (source_record_id, id, data_hash)
);

ALTER TABLE source_records
    ADD CONSTRAINT fk_source_records_current_version FOREIGN KEY (id, current_version_id, data_hash)
        REFERENCES source_record_versions(source_record_id, id, data_hash) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_source_records_playback_version FOREIGN KEY (id, playback_version_id)
        REFERENCES source_record_versions(source_record_id, id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT ck_source_records_current_observation CHECK (
        current_version_id IS NULL OR (current_observed_at IS NOT NULL AND data_hash IS NOT NULL)
    );

ALTER TABLE source_playlists RENAME COLUMN video_id TO source_record_id;
ALTER TABLE source_playlists
    ADD COLUMN source_record_version_id uuid,
    ADD COLUMN managed_manually boolean NOT NULL DEFAULT true,
    ADD CONSTRAINT uq_source_playlists_owner UNIQUE (source_record_id, id),
    ADD CONSTRAINT fk_source_playlists_version FOREIGN KEY (source_record_id, source_record_version_id)
        REFERENCES source_record_versions(source_record_id, id),
    ADD CONSTRAINT ck_source_playlists_observation CHECK (managed_manually OR source_record_version_id IS NOT NULL);

ALTER TABLE source_playback_items ADD COLUMN source_record_id uuid;
UPDATE source_playback_items item
   SET source_record_id = playlist.source_record_id
  FROM source_playlists playlist
 WHERE playlist.id = item.playlist_id;
ALTER TABLE source_playback_items
    ALTER COLUMN source_record_id SET NOT NULL,
    DROP CONSTRAINT IF EXISTS fk_episodes_on_playlist,
    ADD CONSTRAINT uq_source_playback_items_owner UNIQUE (source_record_id, id),
    ADD CONSTRAINT fk_source_playback_items_playlist FOREIGN KEY (source_record_id, playlist_id)
        REFERENCES source_playlists(source_record_id, id) ON DELETE CASCADE;
CREATE INDEX idx_source_playback_items_playlist_owner ON source_playback_items(source_record_id, playlist_id);

--changeset codex:1795470000004-protect-source-record-versions splitStatements:false
CREATE FUNCTION protect_source_record_version() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Source-record observations are immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER protect_source_record_version_update
BEFORE UPDATE ON source_record_versions
FOR EACH ROW EXECUTE FUNCTION protect_source_record_version();
