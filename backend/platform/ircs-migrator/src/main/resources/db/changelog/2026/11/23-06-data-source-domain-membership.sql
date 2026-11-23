--liquibase formatted sql
--changeset prodigalgal:1795380100002-data-source-domain-membership

CREATE TABLE data_source_domains (
    data_source_id uuid NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
    source_domain_id uuid NOT NULL REFERENCES source_domains(id) ON DELETE CASCADE,
    first_seen_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (data_source_id, source_domain_id)
);

CREATE INDEX idx_data_source_domains_domain_source
    ON data_source_domains (source_domain_id, data_source_id);

INSERT INTO data_source_domains (data_source_id, source_domain_id, first_seen_at)
SELECT observations.data_source_id, observations.source_domain_id, min(observations.observed_at)
  FROM (
        SELECT rv.data_source_id, ci.source_domain_id, rv.created_at AS observed_at
          FROM raw_videos rv
          JOIN cover_images ci ON ci.id = rv.cover_image_id
         WHERE rv.data_source_id IS NOT NULL
           AND ci.source_domain_id IS NOT NULL
        UNION ALL
        SELECT rv.data_source_id, ep.source_domain_id, ep.created_at AS observed_at
          FROM raw_videos rv
          JOIN playlists pl ON pl.video_id = rv.id
          JOIN episodes ep ON ep.playlist_id = pl.id
         WHERE rv.data_source_id IS NOT NULL
           AND ep.source_domain_id IS NOT NULL
       ) observations
 GROUP BY observations.data_source_id, observations.source_domain_id
ON CONFLICT (data_source_id, source_domain_id) DO NOTHING;

-- rollback DROP TABLE data_source_domains;
