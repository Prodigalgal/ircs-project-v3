package com.prodigalgal.ircs.storage.image;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CoverImageDeletionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int deleteById(UUID id) {
        return jdbcTemplate.update("delete from cover_images where id = :id", Map.of("id", id));
    }
}
