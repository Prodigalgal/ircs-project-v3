package com.prodigalgal.ircs.content.people.infrastructure;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;

import com.prodigalgal.ircs.content.people.domain.PeopleResource;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonResponse;
import com.prodigalgal.ircs.content.video.api.ContentApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class PeopleAdminRepository {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id",
            "name", "name",
            "createdAt", "created_at",
            "updatedAt", "updated_at");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<PersonResponse> findAll(PeopleResource resource, Pageable pageable, String name) {
        Pageable safe = sanitize(pageable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safe.getPageSize())
                .addValue("offset", safe.getOffset());
        String where = whereByName(params, name);
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from " + resource.tableName() + " p" + where,
                params,
                Long.class);
        List<PersonResponse> content = jdbcTemplate.query(
                "select p.id, p.name, p.created_at, p.updated_at from " + resource.tableName() + " p"
                        + where + orderBy(safe) + " limit :limit offset :offset",
                params,
                this::mapPerson);
        return new PageImpl<>(content, safe, total == null ? 0 : total);
    }

    public List<PersonResponse> findAllSortedByName(PeopleResource resource) {
        return jdbcTemplate.query(
                "select p.id, p.name, p.created_at, p.updated_at from " + resource.tableName()
                        + " p order by p.name asc",
                new MapSqlParameterSource(),
                this::mapPerson);
    }

    public Optional<PersonResponse> findOne(PeopleResource resource, UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select p.id, p.name, p.created_at, p.updated_at from " + resource.tableName()
                            + " p where p.id = :id",
                    new MapSqlParameterSource("id", id),
                    this::mapPerson));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public PersonResponse create(PeopleResource resource, String name) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into " + resource.tableName()
                        + " (id, name, created_at, updated_at, version) values (:id, :name, :now, :now, 0)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", name)
                        .addValue("now", now));
        return requireExisting(resource, id);
    }

    public PersonResponse update(PeopleResource resource, UUID id, String name) {
        int rows = jdbcTemplate.update(
                "update " + resource.tableName()
                        + " set name = :name, updated_at = :now, version = coalesce(version, 0) + 1 where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("name", name)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (rows == 0) {
            throw notFound(resource, id);
        }
        return requireExisting(resource, id);
    }

    public PersonResponse patch(PeopleResource resource, UUID id, String name) {
        if (name == null) {
            return requireExisting(resource, id);
        }
        return update(resource, id, name);
    }

    public void deleteUnreferenced(PeopleResource resource, UUID id) {
        if (!exists(resource, id)) {
            throw notFound(resource, id);
        }
        long referenceCount = referenceCount(resource, id);
        if (referenceCount > 0) {
            throw new ContentApiException(
                    HttpStatus.CONFLICT,
                    "Cannot delete " + resource.entityName() + " with linked videos");
        }
        jdbcTemplate.update(
                "delete from " + resource.tableName() + " where id = :id",
                new MapSqlParameterSource("id", id));
    }

    private String whereByName(MapSqlParameterSource params, String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        params.addValue("name", "%" + name.trim() + "%");
        return " where lower(p.name) like lower(:name)";
    }

    private String orderBy(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return " order by p.created_at desc";
        }
        List<String> orders = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column != null) {
                orders.add("p." + column + " " + order.getDirection().name());
            }
        });
        if (orders.isEmpty()) {
            return " order by p.created_at desc";
        }
        return " order by " + String.join(", ", orders);
    }

    private boolean exists(PeopleResource resource, UUID id) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from " + resource.tableName() + " where id = :id)",
                new MapSqlParameterSource("id", id),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private long referenceCount(PeopleResource resource, UUID id) {
        Long count = jdbcTemplate.queryForObject(
                """
                select
                    (select count(*)
                       from raw_videos rv
                       join %s p on p.id = :id
                      where exists (
                          select 1
                            from jsonb_array_elements_text(coalesce(rv.%s, '[]'::jsonb)) n(name)
                           where n.name = p.name
                      )) +
                    (select count(*)
                       from unified_videos uv
                       join %s p on p.id = :id
                      where exists (
                          select 1
                            from jsonb_array_elements_text(coalesce(uv.%s, '[]'::jsonb)) n(name)
                           where n.name = p.name
                      ))
                """.formatted(
                        resource.tableName(),
                        resource.flatColumnName(),
                        resource.tableName(),
                        resource.flatColumnName()),
                new MapSqlParameterSource("id", id),
                Long.class);
        return count == null ? 0 : count;
    }

    private PersonResponse requireExisting(PeopleResource resource, UUID id) {
        return findOne(resource, id).orElseThrow(() -> notFound(resource, id));
    }

    private ContentApiException notFound(PeopleResource resource, UUID id) {
        return new ContentApiException(HttpStatus.NOT_FOUND, resource.entityName() + " not found: " + id);
    }

    private Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE);
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE);
        return PageRequest.of(page, size, pageable.getSort());
    }

    private PersonResponse mapPerson(ResultSet rs, int rowNum) throws SQLException {
        return new PersonResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
