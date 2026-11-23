--liquibase formatted sql

--changeset codex:1795470000013-source-origin-ownership
ALTER TABLE source_origins
    ADD COLUMN scheme varchar(5) GENERATED ALWAYS AS (lower(substring(domain_value FROM '(?i)^(https?)://'))) STORED,
    ADD COLUMN host varchar(253) GENERATED ALWAYS AS (
        lower(substring(domain_value FROM '(?i)^https?://(\[[0-9a-f:.]+\]|[a-z0-9._-]+)(?::[0-9]{1,5})?$'))) STORED,
    ADD COLUMN port integer GENERATED ALWAYS AS (
        CASE WHEN domain_value ~* '^https?://' THEN coalesce(
            substring(domain_value FROM '\]?:([0-9]{1,5})$')::integer,
            CASE WHEN domain_value ~* '^https://' THEN 443 ELSE 80 END)
        END) STORED;
ALTER TABLE source_origins ADD CONSTRAINT source_origin_network_shape CHECK (scheme IS NULL OR
    (host IS NOT NULL AND port BETWEEN 1 AND 65535));
CREATE UNIQUE INDEX source_origin_authority_unique ON source_origins(scheme,host,port) WHERE scheme IS NOT NULL;

INSERT INTO collection_source_origins(collection_source_id,source_origin_id,first_seen_at)
SELECT collection_source_id,id,created_at AT TIME ZONE 'UTC' FROM source_origins WHERE collection_source_id IS NOT NULL
ON CONFLICT DO NOTHING;
ALTER TABLE source_origins DROP COLUMN collection_source_id;

--changeset codex:1795470000014-catalog-taxonomy splitStatements:false
CREATE FUNCTION normalize_source_origin() RETURNS trigger LANGUAGE plpgsql AS $body$
DECLARE
    parts text[];
    effective_port integer;
    default_port integer;
BEGIN
    parts := regexp_match(btrim(NEW.domain_value),
        '^(https?)://(\[[0-9a-f:.]+\]|[a-z0-9._-]+)(?::([0-9]{1,5}))?$', 'i');
    IF parts IS NULL THEN RETURN NEW; END IF;
    default_port := CASE WHEN lower(parts[1])='https' THEN 443 ELSE 80 END;
    effective_port := coalesce(parts[3]::integer,default_port);
    NEW.domain_value := lower(parts[1]) || '://' || lower(parts[2]) ||
        CASE WHEN effective_port=default_port THEN '' ELSE ':' || effective_port::text END;
    NEW.domain_hash := encode(sha256(convert_to(NEW.domain_value,'UTF8')),'hex');
    RETURN NEW;
END;
$body$;

CREATE TRIGGER source_origin_normalize
BEFORE INSERT OR UPDATE OF domain_value ON source_origins
FOR EACH ROW EXECUTE FUNCTION normalize_source_origin();

CREATE FUNCTION refresh_catalog_work_taxonomy(target_work uuid) RETURNS void
LANGUAGE plpgsql AS $body$
BEGIN
    PERFORM 1 FROM catalog_works WHERE id=target_work FOR NO KEY UPDATE;
    IF NOT FOUND THEN RETURN; END IF;

    DELETE FROM catalog_work_genres WHERE work_id=target_work;
    INSERT INTO catalog_work_genres(work_id,genre_id)
    SELECT DISTINCT target_work, genre.id FROM catalog_entries entry
    CROSS JOIN LATERAL jsonb_array_elements_text(entry.genre_codes) code(value)
    JOIN catalog_genres genre ON lower(genre.code)=lower(code.value)
    WHERE entry.work_id=target_work;

    DELETE FROM catalog_work_regions WHERE work_id=target_work;
    INSERT INTO catalog_work_regions(work_id,region_id)
    SELECT DISTINCT target_work,region.id FROM catalog_entries entry
    CROSS JOIN LATERAL jsonb_array_elements_text(entry.area_codes) code(value)
    JOIN catalog_regions region ON lower(region.code)=lower(code.value)
    WHERE entry.work_id=target_work;

    DELETE FROM catalog_work_languages WHERE work_id=target_work;
    INSERT INTO catalog_work_languages(work_id,language_id)
    SELECT DISTINCT target_work,language.id FROM catalog_entries entry
    CROSS JOIN LATERAL jsonb_array_elements_text(entry.language_codes) code(value)
    JOIN catalog_languages language ON lower(language.code)=lower(code.value)
    WHERE entry.work_id=target_work;
END;
$body$;

CREATE FUNCTION refresh_entry_work_facts() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    IF TG_OP='DELETE' THEN
        PERFORM refresh_catalog_work_taxonomy(OLD.work_id);
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' AND OLD.work_id<>NEW.work_id THEN
        PERFORM refresh_catalog_work_taxonomy(OLD.work_id);
    END IF;
    PERFORM refresh_catalog_work_taxonomy(NEW.work_id);
    RETURN NEW;
END;
$body$;

CREATE TRIGGER catalog_entry_write_work_taxonomy
AFTER INSERT OR DELETE OR UPDATE OF work_id,genre_codes,area_codes,language_codes ON catalog_entries
FOR EACH ROW EXECUTE FUNCTION refresh_entry_work_facts();
