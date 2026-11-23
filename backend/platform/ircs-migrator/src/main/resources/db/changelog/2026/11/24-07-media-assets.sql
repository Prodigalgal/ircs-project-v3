--liquibase formatted sql

--changeset codex:1795470000011-media-asset-ownership
CREATE TABLE media_assets (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    asset_kind varchar(16) NOT NULL CHECK (asset_kind IN ('COVER')),
    content_hash varchar(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    byte_size bigint NOT NULL CHECK (byte_size > 0),
    mime_type varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    reservation_until timestamptz NOT NULL,
    retired_at timestamptz
);
CREATE UNIQUE INDEX media_asset_active_content ON media_assets(asset_kind,content_hash) WHERE retired_at IS NULL;

CREATE TABLE media_asset_locations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    media_asset_id uuid NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    storage_backend varchar(8) NOT NULL CHECK (storage_backend IN ('LOCAL', 'R2')),
    storage_bucket varchar(255) NOT NULL,
    object_key text NOT NULL CHECK (btrim(object_key) <> ''),
    first_reserved_at timestamptz NOT NULL DEFAULT now(),
    stored_at timestamptz,
    UNIQUE (storage_backend,storage_bucket,object_key),
    CHECK ((storage_backend='LOCAL') = (storage_bucket=''))
);
CREATE INDEX media_asset_location_owner ON media_asset_locations(media_asset_id);
ALTER TABLE media_image_sources ADD COLUMN media_asset_id uuid REFERENCES media_assets(id);
CREATE INDEX media_image_source_asset_lookup ON media_image_sources(media_asset_id) WHERE media_asset_id IS NOT NULL;

CREATE TABLE media_asset_cleanup_jobs (
    media_asset_id uuid PRIMARY KEY REFERENCES media_assets(id) ON DELETE CASCADE,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_id uuid,
    lease_until timestamptz,
    last_error varchar(120),
    CHECK ((lease_id IS NULL) = (lease_until IS NULL))
);
CREATE INDEX media_asset_cleanup_due ON media_asset_cleanup_jobs(next_attempt_at, media_asset_id);

--changeset codex:1795470000012-media-asset-reservation-functions splitStatements:false
CREATE FUNCTION reserve_cover_asset(hash text, size_bytes bigint, mime text, suggested_path text)
RETURNS text LANGUAGE plpgsql AS $body$
DECLARE
    asset_id uuid;
    object_path text;
BEGIN
    INSERT INTO media_assets(asset_kind,content_hash,byte_size,mime_type,reservation_until)
    VALUES ('COVER',hash,size_bytes,mime,now()+interval '1 hour')
    ON CONFLICT (asset_kind,content_hash) WHERE retired_at IS NULL DO UPDATE
        SET reservation_until=excluded.reservation_until
    RETURNING id INTO asset_id;

    SELECT location.object_key INTO object_path FROM media_asset_locations location
     WHERE location.media_asset_id=asset_id AND location.storage_backend='LOCAL';
    IF NOT FOUND THEN
        -- A newly reserved generation gets a different key even when the earlier object has the same bytes.
        object_path := regexp_replace(suggested_path,'[^/]+$','') || asset_id::text || '-' ||
            regexp_replace(suggested_path,'.*/','');
        INSERT INTO media_asset_locations(media_asset_id,storage_backend,storage_bucket,object_key)
        VALUES (asset_id,'LOCAL','',object_path);
    END IF;
    INSERT INTO media_asset_cleanup_jobs(media_asset_id,next_attempt_at)
    VALUES(asset_id,now()+interval '1 hour') ON CONFLICT (media_asset_id) DO NOTHING;
    RETURN object_path;
END;
$body$;

CREATE FUNCTION reserve_remote_cover_location(image_id uuid, bucket_name text, object_path text)
RETURNS void LANGUAGE plpgsql AS $body$
DECLARE
    asset_id uuid;
BEGIN
    SELECT asset.id INTO STRICT asset_id
    FROM media_image_sources image JOIN media_assets asset ON asset.id=image.media_asset_id
    WHERE image.id=image_id AND image.storage_path=object_path AND image.status='UPLOADING'
      AND asset.retired_at IS NULL
    FOR UPDATE OF asset;
    UPDATE media_assets SET reservation_until=now()+interval '1 hour' WHERE id=asset_id;
    INSERT INTO media_asset_locations(media_asset_id,storage_backend,storage_bucket,object_key)
    VALUES(asset_id,'R2',bucket_name,object_path) ON CONFLICT DO NOTHING;
END;
$body$;

CREATE FUNCTION attach_media_image_asset() RETURNS trigger LANGUAGE plpgsql AS $body$
DECLARE
    location_id uuid;
BEGIN
    IF NEW.storage_type IN ('LOCAL','R2') AND NEW.storage_path IS NOT NULL AND NEW.file_hash IS NOT NULL THEN
        SELECT asset.id, location.id INTO STRICT NEW.media_asset_id, location_id
        FROM media_asset_locations location JOIN media_assets asset ON asset.id=location.media_asset_id
        WHERE location.object_key=NEW.storage_path AND location.storage_backend=NEW.storage_type
          AND asset.content_hash=NEW.file_hash AND asset.retired_at IS NULL
        ORDER BY location.first_reserved_at DESC,location.id LIMIT 1
        FOR UPDATE OF asset;
        UPDATE media_asset_locations SET stored_at=coalesce(stored_at,now()) WHERE id=location_id;
    ELSE
        NEW.media_asset_id := NULL;
    END IF;
    RETURN NEW;
END;
$body$;

CREATE TRIGGER media_image_source_attach
BEFORE INSERT OR UPDATE OF storage_type,storage_path,file_hash ON media_image_sources
FOR EACH ROW EXECUTE FUNCTION attach_media_image_asset();

CREATE FUNCTION schedule_detached_media_asset() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    IF OLD.media_asset_id IS NOT NULL AND (TG_OP='DELETE' OR OLD.media_asset_id IS DISTINCT FROM NEW.media_asset_id) THEN
        INSERT INTO media_asset_cleanup_jobs(media_asset_id) VALUES(OLD.media_asset_id)
        ON CONFLICT (media_asset_id) DO UPDATE SET next_attempt_at=least(media_asset_cleanup_jobs.next_attempt_at,now())
        WHERE media_asset_cleanup_jobs.lease_id IS NULL;
    END IF;
    RETURN NULL;
END;
$body$;

CREATE TRIGGER media_image_source_schedule_cleanup
AFTER DELETE OR UPDATE OF storage_type,storage_path,file_hash ON media_image_sources
FOR EACH ROW EXECUTE FUNCTION schedule_detached_media_asset();

CREATE FUNCTION claim_media_asset_cleanup(claim_id uuid) RETURNS uuid LANGUAGE plpgsql AS $body$
DECLARE
    asset_id uuid;
BEGIN
    SELECT asset.id INTO asset_id FROM media_asset_cleanup_jobs job
    JOIN media_assets asset ON asset.id=job.media_asset_id
    WHERE job.next_attempt_at<=now() AND (job.lease_until IS NULL OR job.lease_until<=now())
      AND asset.reservation_until<=now()
      AND NOT EXISTS(SELECT FROM media_image_sources image WHERE image.media_asset_id=asset.id)
    ORDER BY job.next_attempt_at,job.media_asset_id LIMIT 1 FOR UPDATE OF asset SKIP LOCKED;
    IF NOT FOUND THEN RETURN NULL; END IF;
    -- Recheck after acquiring exclusive ownership so a concurrent attach cannot be lost at the read snapshot.
    IF EXISTS(SELECT FROM media_image_sources image WHERE image.media_asset_id=asset_id) THEN RETURN NULL; END IF;
    UPDATE media_asset_cleanup_jobs SET lease_id=claim_id,lease_until=now()+interval '5 minutes',attempts=attempts+1
    WHERE media_asset_id=asset_id AND (lease_until IS NULL OR lease_until<=now());
    IF NOT FOUND THEN RETURN NULL; END IF;
    UPDATE media_assets SET retired_at=coalesce(retired_at,now()) WHERE id=asset_id;
    RETURN asset_id;
END;
$body$;
