package com.prodigalgal.ircs.ops;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CredentialLabelRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<String> findLabel(UUID id) {
        String label = DataAccessUtils.singleResult(jdbcTemplate.query(
                """
                select provider, name
                  from sys_credentials
                 where id = :id
                """,
                Map.of("id", id),
                (rs, rowNum) -> {
                    String provider = rs.getString("provider");
                    String name = rs.getString("name");
                    if (name == null || name.isBlank()) {
                        return "[" + provider + "]";
                    }
                    return "[" + provider + "] " + name;
                }));
        return Optional.ofNullable(label);
    }
}

