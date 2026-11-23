package com.prodigalgal.ircs.content.video.infrastructure;


import com.prodigalgal.ircs.content.video.domain.VideoAdminText;
import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
class VideoAdminCoverImageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    Optional<UUID> getOrCreateCoverImage(String coverImageUrl) {
        if (!StringUtils.hasText(coverImageUrl)) {
            return Optional.empty();
        }
        String trimmed = coverImageUrl.trim();
        String domain = VideoAdminText.domainOf(trimmed);
        UUID sourceDomainId = getOrCreateSourceDomain(domain);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    insert into media_image_sources (
                        id, created_at, updated_at, version, storage_type, original_url, storage_path,
                        file_hash, file_size, mime_type, source_origin_id, status, retry_count,
                        next_retry_time, last_error
                    ) values (
                        :id, now(), now(), 0, 'EXTERNAL', :url, null,
                        null, null, null, :sourceDomainId, 'UNPROCESSED', 0,
                        now(), null
                    )
                    on conflict (original_url, source_origin_id) do update
                    set updated_at = media_image_sources.updated_at
                    returning id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", IrcsUuidGenerators.nextId())
                            .addValue("url", trimmed)
                            .addValue("sourceDomainId", sourceDomainId),
                    UUID.class));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private UUID getOrCreateSourceDomain(String domain) {
        String hash = VideoAdminText.sha256(domain);
        return jdbcTemplate.queryForObject(
                """
                insert into source_origins (id, created_at, updated_at, version, domain_hash, domain_value)
                values (:id, now(), now(), 0, :hash, :domain)
                on conflict (domain_hash) do update
                set updated_at = source_origins.updated_at
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("id", IrcsUuidGenerators.nextId())
                        .addValue("hash", hash)
                        .addValue("domain", domain),
                UUID.class);
    }
}
