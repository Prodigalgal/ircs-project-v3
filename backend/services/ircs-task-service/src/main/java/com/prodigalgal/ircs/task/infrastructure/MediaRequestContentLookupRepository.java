package com.prodigalgal.ircs.task.infrastructure;

import com.prodigalgal.ircs.task.domain.MediaRequestExistingVideo;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class MediaRequestContentLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.catalog.authority-first.enabled:false}")
    private boolean authorityFirstEnabled;

    public Optional<MediaRequestExistingVideo> findExistingVideo(String title, Integer releaseYear) {
        String normalizedTitle = normalizeTitle(title);
        if (!StringUtils.hasText(normalizedTitle)) {
            return Optional.empty();
        }
        if (authorityFirstEnabled) {
            return findPlayableVideo(title, normalizedTitle, releaseYear);
        }
        return findUnifiedVideo(normalizedTitle, releaseYear)
                .or(() -> findRawVideo(normalizedTitle, releaseYear));
    }

    private Optional<MediaRequestExistingVideo> findPlayableVideo(String title, String normalizedTitle, Integer releaseYear) {
        // Match the keys stored by the authority catalog importer.
        String titleKey = Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
        if (titleKey.isBlank()) {
            return Optional.empty();
        }
        Integer year = releaseYear == null || releaseYear <= 0 ? null : releaseYear;
        return jdbcTemplate.query("""
                with requested as (
                    select cast(? as varchar) as authority_key, cast(? as varchar) as source_title,
                           cast(? as integer) as release_year
                ), matching_works as materialized (
                    select work.id
                      from authority_catalog_works work cross join requested r
                     where (work.title_key = r.authority_key or work.original_title_key = r.authority_key)
                       and (r.release_year is null or work.release_year = r.release_year)
                     limit 2
                ), authority_match as (
                    select uv.id
                      from matching_works work
                      join authority_catalog_projections p on p.work_id = work.id
                      join unified_videos uv on uv.id = p.unified_video_id
                     where (select count(*) from matching_works) = 1
                       and p.season_number <> 0
                       and uv.content_visibility = 'PUBLIC'
                       and catalog_has_target_resources(uv.id, greatest(p.season_number, 0), 0)
                     order by p.season_number
                     limit 1
                ), source_matches as materialized (
                    select uv.id, uv.season
                      from unified_videos uv cross join requested r
                     where not exists (select 1 from matching_works)
                       and uv.content_visibility = 'PUBLIC'
                       and not coalesce(uv.adult_restricted, false)
                       and uv.category_code in ('short-drama', 'sports', 'news', 'education', 'music')
                       and (lower(btrim(uv.title)) = r.source_title or lower(btrim(uv.alias_title)) = r.source_title)
                       and (r.release_year is null or uv.year = cast(r.release_year as varchar))
                       and not exists (select 1 from authority_catalog_projections p where p.unified_video_id = uv.id)
                     limit 2
                )
                select id from authority_match
                union all
                select s.id from source_matches s
                 where (select count(*) from source_matches) = 1
                   and catalog_has_target_resources(s.id, greatest(s.season, 0), 0)
                """, (rs, row) -> new MediaRequestExistingVideo(rs.getObject("id", UUID.class), "UNIFIED"),
                titleKey, normalizedTitle, year).stream().findFirst();
    }

    private Optional<MediaRequestExistingVideo> findUnifiedVideo(String title, Integer releaseYear) {
        return find("""
                select id
                  from unified_videos
                 where lower(btrim(title)) = ?
                   and (cast(? as varchar) is null or year = ?)
                 order by updated_at desc nulls last, id
                 limit 1
                """, "UNIFIED", title, releaseYear);
    }

    private Optional<MediaRequestExistingVideo> findRawVideo(String title, Integer releaseYear) {
        return find("""
                select id
                  from raw_videos
                 where lower(btrim(title)) = ?
                   and (cast(? as varchar) is null or year = ?)
                 order by updated_at desc nulls last, id
                 limit 1
                """, "RAW", title, releaseYear);
    }

    private Optional<MediaRequestExistingVideo> find(
            String sql,
            String source,
            String title,
            Integer releaseYear) {
        String year = releaseYear == null || releaseYear <= 0 ? null : releaseYear.toString();
        try {
            UUID id = jdbcTemplate.queryForObject(sql, UUID.class, title, year, year);
            return id == null ? Optional.empty() : Optional.of(new MediaRequestExistingVideo(id, source));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
