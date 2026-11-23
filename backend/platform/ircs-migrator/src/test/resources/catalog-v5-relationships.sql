DO $test$
DECLARE
    work uuid := uuidv7();
    season_one uuid := uuidv7();
    season_two uuid := uuidv7();
    episode_one uuid := uuidv7();
    entry uuid := uuidv7();
    provider uuid := uuidv7();
    source_record uuid := uuidv7();
    observation uuid := uuidv7();
    playlist uuid := uuidv7();
    correct_item uuid := uuidv7();
    wrong_item uuid := uuidv7();
    first_member uuid := uuidv7();
    second_member uuid := uuidv7();
    demand uuid := uuidv7();
    first_submission uuid := uuidv7();
    second_submission uuid := uuidv7();
    origin uuid := uuidv7();
    cover_one uuid := uuidv7();
    cover_two uuid := uuidv7();
    cover_hash text := repeat('b',64);
    object_key text;
    shared_asset uuid;
BEGIN
    INSERT INTO catalog_works(id,media_type,tmdb_id,title,title_key,last_verified_at)
    VALUES (work,'tv',990001,'Same title','same title',now()),
           (uuidv7(),'tv',990002,'Same title','same title',now());
    INSERT INTO catalog_seasons(id,work_id,season_number,tmdb_season_id,verified_at)
    VALUES (season_one, work, 1, 991001, now()), (season_two, work, 2, 991002, now());
    INSERT INTO catalog_episodes(id,work_id,season_id,season_number,episode_number,tmdb_episode_id)
    VALUES (episode_one, work, season_one, 1, 1, 992001);
    INSERT INTO catalog_entries(id,work_id,season_id,title,created_at,updated_at)
    VALUES (entry, work, season_one, 'Same title',now(),now());

    INSERT INTO collection_sources(id,created_at,updated_at,name,base_url,list_path,detail_path)
    VALUES (provider,now(),now(),'Fixture source','https://example.invalid','/list','/detail');
    INSERT INTO source_records(id,created_at,updated_at,collection_source_id,source_vid,source_hash,title,season,
        enrichment_status,normalization_status)
    VALUES (source_record,now(),now(),provider,'fixture',repeat('c',64),'Same title',1,'PENDING','PENDING');
    INSERT INTO source_record_versions(id,source_record_id,data_hash,payload,observed_at)
    VALUES(observation,source_record,repeat('d',64),'{}',now());
    UPDATE source_records SET current_version_id=observation,playback_version_id=observation,
        data_hash=repeat('d',64),current_observed_at=now() WHERE id=source_record;
    INSERT INTO source_playlists(id,created_at,updated_at,name,source_record_id,source_record_version_id)
    VALUES(playlist,now(),now(),'Main',source_record,observation);
    INSERT INTO source_playback_items(id,created_at,updated_at,name,url,playlist_id,source_record_id)
    VALUES(correct_item,now(),now(),'S01E01','https://example.invalid/01',playlist,source_record),
          (wrong_item,now(),now(),'S02E01','https://example.invalid/02',playlist,source_record);
    INSERT INTO catalog_source_bindings(source_record_id,catalog_entry_id) VALUES(source_record,entry);
    IF EXISTS (SELECT FROM catalog_list_playable_episodes(entry)) THEN
        RAISE EXCEPTION 'An unverified provider record was published';
    END IF;
    PERFORM affirm_catalog_source_match(source_record,entry,'MANUAL_REVIEW');
    IF (SELECT array_agg(episode_id) FROM catalog_list_playable_episodes(entry)) IS DISTINCT FROM ARRAY[correct_item]
        OR (SELECT catalog_episode_id FROM catalog_list_playable_episodes(entry)) IS DISTINCT FROM episode_one THEN
        RAISE EXCEPTION 'The verified playback graph admitted the wrong season or lost its canonical episode';
    END IF;
    BEGIN
        UPDATE catalog_playback_bindings SET season_id=season_two WHERE source_playback_item_id=correct_item;
        RAISE EXCEPTION 'A binding across seasons was allowed';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        UPDATE source_record_versions SET payload='{"modified":true}' WHERE id=observation;
        RAISE EXCEPTION 'An immutable source version was modified';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO member_accounts(id,created_at,updated_at,email,password_hash,role,status)
    VALUES(first_member,now(),now(),'first@example.invalid','x','MEMBER','ACTIVE'),
          (second_member,now(),now(),'second@example.invalid','x','MEMBER','ACTIVE');
    PERFORM apply_member_point_transaction(first_member,'open','ACCOUNT_OPENING',20,false,null);
    PERFORM apply_member_point_transaction(second_member,'open','ACCOUNT_OPENING',20,false,null);
    INSERT INTO media_request_demands(id,created_at,updated_at,title,normalized_title,release_year,last_requested_at)
    VALUES(demand,now(),now(),'Same title','same title',null,now());
    INSERT INTO media_request_submissions(id,demand_id,member_id,submitted_title,spent_points,created_at)
    VALUES(first_submission,demand,first_member,'Same title',3,now()),
          (second_submission,demand,second_member,'Same title',3,now());
    PERFORM apply_member_point_transaction(first_member,'submit','MEDIA_REQUEST',-3,false,first_submission);
    PERFORM apply_member_point_transaction(second_member,'submit','MEDIA_REQUEST',-3,false,second_submission);
    PERFORM apply_member_point_transaction(first_member,'submit','MEDIA_REQUEST',-3,false,first_submission);
    IF (SELECT sum(balance) FROM member_point_balances WHERE member_id IN (first_member,second_member))<>34
        OR (SELECT count(*) FROM member_point_transactions WHERE reason='MEDIA_REQUEST')<>2 THEN
        RAISE EXCEPTION 'Media request participants did not receive independent exactly-once charges';
    END IF;

    INSERT INTO source_origins(id,created_at,updated_at,domain_hash,domain_value)
    VALUES(origin,now(),now(),repeat('e',64),'https://example.invalid');
    object_key := reserve_cover_asset(cover_hash, 100, 'image/png', 'covers/test.png');
    IF reserve_cover_asset(cover_hash,100,'image/png','covers/test.png')<>object_key THEN
        RAISE EXCEPTION 'Equal cover contents did not share the active asset';
    END IF;
    INSERT INTO media_image_sources(id,created_at,updated_at,storage_type,original_url,storage_path,file_hash,source_origin_id,status)
    VALUES(cover_one,now(),now(),'LOCAL','/one',object_key,cover_hash,origin,'LOCAL_STORED'),
          (cover_two,now(),now(),'LOCAL','/two',object_key,cover_hash,origin,'LOCAL_STORED');
    SELECT media_asset_id INTO STRICT shared_asset FROM media_image_sources WHERE id=cover_two;
    DELETE FROM media_image_sources WHERE id=cover_one;
    UPDATE media_assets SET reservation_until=now()-interval '1 minute' WHERE id=shared_asset;
    IF claim_media_asset_cleanup(uuidv7()) IS NOT NULL THEN
        RAISE EXCEPTION 'A referenced physical asset was claimed for cleanup';
    END IF;
    DELETE FROM media_image_sources WHERE id=cover_two;
    IF claim_media_asset_cleanup(uuidv7()) IS DISTINCT FROM shared_asset THEN
        RAISE EXCEPTION 'An unreferenced asset was not retired';
    END IF;
    IF reserve_cover_asset(cover_hash,100,'image/png','covers/test.png')=object_key THEN
        RAISE EXCEPTION 'A new asset generation reused a retired key';
    END IF;
END;
$test$;
