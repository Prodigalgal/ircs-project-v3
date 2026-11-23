-- liquibase formatted sql

--changeset codex:1795470000009-member-relationships splitStatements:false
ALTER TABLE media_request_demands
    DROP CONSTRAINT uk_portal_media_requests_title_year,
    ALTER COLUMN release_year DROP NOT NULL,
    ALTER COLUMN release_year DROP DEFAULT;

UPDATE media_request_demands SET release_year = NULL WHERE release_year = 0;

ALTER TABLE media_request_demands
    ADD CONSTRAINT media_request_demands_title_year_key UNIQUE NULLS NOT DISTINCT (normalized_title, release_year),
    ADD CONSTRAINT media_request_demands_release_year_check CHECK (release_year IS NULL OR release_year >= 1888);

ALTER TABLE media_request_processing_batch_items
    ALTER COLUMN release_year DROP NOT NULL,
    ALTER COLUMN release_year DROP DEFAULT;

UPDATE media_request_processing_batch_items SET release_year = NULL WHERE release_year = 0;

CREATE TABLE media_request_submissions (
    id uuid PRIMARY KEY,
    demand_id uuid NOT NULL REFERENCES media_request_demands(id) ON DELETE RESTRICT,
    member_id uuid NOT NULL REFERENCES member_accounts(id) ON DELETE CASCADE,
    submitted_title varchar(255) NOT NULL,
    extra_info text,
    spent_points integer NOT NULL DEFAULT 0 CHECK (spent_points >= 0),
    client_request_id uuid,
    created_at timestamptz NOT NULL,
    UNIQUE (member_id, id),
    UNIQUE (member_id, client_request_id)
);
CREATE INDEX media_request_submissions_member_time_idx ON media_request_submissions (member_id, created_at DESC, id);
CREATE INDEX media_request_submissions_demand_idx ON media_request_submissions (demand_id);

INSERT INTO media_request_submissions (id, demand_id, member_id, submitted_title, extra_info, created_at)
SELECT id, id, member_id, title, extra_info, created_at
  FROM media_request_demands;

ALTER TABLE media_request_demands DROP COLUMN member_id, DROP COLUMN extra_info;

ALTER TABLE member_feedback_posts
    ADD COLUMN deleted_at timestamptz,
    ADD CONSTRAINT member_feedback_posts_owner_key UNIQUE (member_id, id),
    DROP CONSTRAINT fk_usermsg_on_member,
    ADD CONSTRAINT member_feedback_posts_owner_fk FOREIGN KEY (member_id) REFERENCES member_accounts(id) ON DELETE CASCADE;

CREATE TABLE member_feedback_replies (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    feedback_post_id uuid NOT NULL REFERENCES member_feedback_posts(id) ON DELETE CASCADE,
    content text NOT NULL CHECK (length(btrim(content)) > 0),
    created_at timestamptz NOT NULL
);
CREATE INDEX member_feedback_replies_post_time_idx ON member_feedback_replies (feedback_post_id, created_at DESC, id DESC);
CREATE INDEX member_feedback_posts_wall_idx ON member_feedback_posts (created_at DESC, id) WHERE is_public AND deleted_at IS NULL;

INSERT INTO member_feedback_replies (feedback_post_id, content, created_at)
SELECT id, reply, updated_at FROM member_feedback_posts WHERE nullif(btrim(reply), '') IS NOT NULL;

ALTER TABLE member_feedback_posts DROP COLUMN reply, DROP COLUMN admin_id;

CREATE TABLE member_point_balances (
    member_id uuid PRIMARY KEY REFERENCES member_accounts(id) ON DELETE CASCADE,
    balance integer NOT NULL DEFAULT 0 CHECK (balance >= 0),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE member_point_transactions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    member_id uuid NOT NULL REFERENCES member_point_balances(member_id) ON DELETE CASCADE,
    operation_key text NOT NULL CHECK (length(btrim(operation_key)) > 0),
    reason text NOT NULL CHECK (reason IN ('ACCOUNT_OPENING', 'DAILY_CHECK_IN', 'ADMIN_ADJUSTMENT', 'MEDIA_REQUEST', 'FEEDBACK_POST')),
    delta integer NOT NULL CHECK (delta <> 0),
    balance_after integer NOT NULL CHECK (balance_after >= 0),
    media_request_submission_id uuid,
    feedback_post_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (member_id, operation_key),
    UNIQUE (media_request_submission_id),
    UNIQUE (feedback_post_id),
    FOREIGN KEY (member_id, media_request_submission_id) REFERENCES media_request_submissions(member_id, id) ON DELETE CASCADE,
    FOREIGN KEY (member_id, feedback_post_id) REFERENCES member_feedback_posts(member_id, id) ON DELETE CASCADE,
    CHECK ((reason = 'MEDIA_REQUEST') = (media_request_submission_id IS NOT NULL)),
    CHECK ((reason = 'FEEDBACK_POST') = (feedback_post_id IS NOT NULL)),
    CHECK (reason NOT IN ('MEDIA_REQUEST', 'FEEDBACK_POST') OR delta < 0),
    CHECK (reason NOT IN ('ACCOUNT_OPENING', 'DAILY_CHECK_IN') OR delta > 0)
);

INSERT INTO member_point_balances (member_id, balance)
SELECT id, points FROM member_accounts;

INSERT INTO member_point_transactions (member_id, operation_key, reason, delta, balance_after)
SELECT member_id, 'account-opening', 'ACCOUNT_OPENING', balance, balance
  FROM member_point_balances WHERE balance > 0;

ALTER TABLE member_accounts DROP COLUMN points;

CREATE FUNCTION initialize_member_point_balance() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO member_point_balances (member_id) VALUES (NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER member_accounts_initialize_point_balance
AFTER INSERT ON member_accounts
FOR EACH ROW EXECUTE FUNCTION initialize_member_point_balance();

CREATE FUNCTION apply_member_point_transaction(
    target_member_id uuid,
    transaction_key text,
    transaction_reason text,
    amount integer,
    set_balance boolean,
    subject_id uuid
) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    current_balance integer;
    next_balance integer;
BEGIN
    SELECT balance INTO STRICT current_balance
      FROM member_point_balances
     WHERE member_id = target_member_id
       FOR UPDATE;

    IF EXISTS (SELECT 1 FROM member_point_transactions
                WHERE member_id = target_member_id AND operation_key = transaction_key) THEN
        RETURN current_balance;
    END IF;

    next_balance := CASE WHEN set_balance THEN amount ELSE current_balance + amount END;
    IF next_balance < 0 THEN
        RETURN NULL;
    END IF;
    IF next_balance = current_balance THEN
        RETURN current_balance;
    END IF;

    INSERT INTO member_point_transactions (
        member_id, operation_key, reason, delta, balance_after, media_request_submission_id, feedback_post_id
    ) VALUES (
        target_member_id, transaction_key, transaction_reason, next_balance - current_balance, next_balance,
        CASE WHEN transaction_reason = 'MEDIA_REQUEST' THEN subject_id END,
        CASE WHEN transaction_reason = 'FEEDBACK_POST' THEN subject_id END
    );

    UPDATE member_point_balances SET balance = next_balance, updated_at = now() WHERE member_id = target_member_id;
    RETURN next_balance;
END;
$$;

ALTER TABLE member_playback_progress RENAME COLUMN last_video_id TO last_source_record_id;
ALTER TABLE member_playback_progress RENAME COLUMN last_episode_id TO last_source_playback_item_id;

ALTER TABLE member_playback_progress ADD COLUMN work_id uuid, ADD COLUMN season_id uuid, ADD COLUMN catalog_episode_id uuid;

UPDATE member_playback_progress progress
   SET work_id = entry.work_id, season_id = entry.season_id
  FROM catalog_entries entry WHERE entry.id = progress.catalog_entry_id;

ALTER TABLE member_playback_progress
    ALTER COLUMN work_id SET NOT NULL,
    DROP CONSTRAINT fk_memhist_on_member,
    ADD CONSTRAINT member_playback_progress_owner_fk FOREIGN KEY (member_id) REFERENCES member_accounts(id) ON DELETE CASCADE;

ALTER TABLE member_catalog_favorites
    DROP CONSTRAINT fk_memfav_on_member,
    ADD CONSTRAINT member_catalog_favorites_owner_fk FOREIGN KEY (member_id) REFERENCES member_accounts(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX IF NOT EXISTS member_progress_entry_work_key ON catalog_entries (work_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS member_progress_season_work_key ON catalog_seasons (work_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS member_progress_episode_work_key ON catalog_episodes (work_id, id);

ALTER TABLE member_playback_progress
    DROP CONSTRAINT fk_memhist_on_unified,
    ADD CONSTRAINT member_playback_progress_entry_work_fk FOREIGN KEY (work_id, catalog_entry_id)
        REFERENCES catalog_entries(work_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT member_playback_progress_season_work_fk FOREIGN KEY (work_id, season_id)
        REFERENCES catalog_seasons(work_id, id) ON DELETE SET NULL (season_id),
    ADD CONSTRAINT member_playback_progress_episode_work_fk FOREIGN KEY (work_id, catalog_episode_id)
        REFERENCES catalog_episodes(work_id, id) ON DELETE SET NULL (catalog_episode_id);

CREATE FUNCTION enforce_member_playback_progress_scope() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.catalog_episode_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM catalog_episodes
         WHERE id = NEW.catalog_episode_id AND work_id = NEW.work_id AND season_id IS NOT DISTINCT FROM NEW.season_id
    ) THEN
        RAISE EXCEPTION 'Playback progress episode must belong to its selected work and season' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM catalog_entries
         WHERE id = NEW.catalog_entry_id AND season_id IS NOT NULL AND season_id IS DISTINCT FROM NEW.season_id
    ) THEN
        RAISE EXCEPTION 'Playback progress must belong to the publication season' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER member_playback_progress_scope
BEFORE INSERT OR UPDATE OF catalog_entry_id, work_id, season_id, catalog_episode_id ON member_playback_progress
FOR EACH ROW EXECUTE FUNCTION enforce_member_playback_progress_scope();
