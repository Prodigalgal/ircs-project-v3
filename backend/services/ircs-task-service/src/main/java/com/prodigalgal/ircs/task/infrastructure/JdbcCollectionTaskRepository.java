package com.prodigalgal.ircs.task.infrastructure;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import com.prodigalgal.ircs.task.domain.MediaRequestBatchItemCandidate;
import com.prodigalgal.ircs.task.domain.ScheduledTaskDefinition;
import com.prodigalgal.ircs.task.domain.MediaRequestCandidate;
import com.prodigalgal.ircs.task.domain.TaskExecutionPlan;
import com.prodigalgal.ircs.task.domain.TaskRuntimeState;
import com.prodigalgal.ircs.task.domain.ValidatedCreateTask;
import com.prodigalgal.ircs.task.domain.ValidatedUpdateTask;
import com.prodigalgal.ircs.task.dto.MediaRequestBatchItemResponse;
import com.prodigalgal.ircs.task.dto.MediaRequestBatchResponse;
import com.prodigalgal.ircs.task.dto.TaskCardSummary;
import com.prodigalgal.ircs.task.dto.TaskDetailSummary;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcCollectionTaskRepository {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String MEDIA_REQUEST_BATCH_HEADER_KEY = "ircsMediaRequestBatch";

    private final JdbcTemplate jdbcTemplate;

    public boolean existsDataSource(UUID dataSourceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from collection_sources where id = ?)",
                Boolean.class,
                dataSourceId);
        return Boolean.TRUE.equals(exists);
    }

    public List<DataSourceSeedCandidate> findDefaultTaskSeedDataSources() {
        return jdbcTemplate.query(
                """
                select id, name
                from collection_sources
                where name is not null
                  and btrim(name) <> ''
                order by name asc, id asc
                """,
                (rs, rowNum) -> new DataSourceSeedCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("name")));
    }

    public List<DataSourceSeedCandidate> findCatalogDiscoveryDataSources(String requestKey, int limit, List<UUID> sourceIds) {
        String allowedIds = String.join(",", sourceIds.stream().map(UUID::toString).toList());
        return jdbcTemplate.query(
                """
                with policy as (select string_to_array(nullif(?, ''), ',')::uuid[] as source_ids)
                select ds.id, ds.name
                  from collection_sources ds
                  cross join policy
                  left join lateral (
                       select max(coalesce(tried.last_execution_time, tried.updated_at)) as last_attempt
                         from collection_plan_details tried
                        where tried.collection_source_id = ds.id
                          and tried.headers -> 'ircsCatalogDiscovery' ->> 'requestKey' = ?
                          and (tried.last_execution_time is not null or tried.status in ('COMPLETED', 'FAILED'))
                  ) history on true
                 where ds.name is not null and btrim(ds.name) <> ''
                   and ds.base_url ~* '^https?://'
                   and ds.list_path is not null and btrim(ds.list_path) <> ''
                   and ds.detail_path is not null and btrim(ds.detail_path) <> ''
                   and ds.field_mapping is not null and ds.field_mapping <> '{}'::jsonb
                   and coalesce(ds.adult_restricted, false) = false
                   and case when policy.source_ids is not null then ds.id = any(policy.source_ids) else exists (
                       select 1 from collection_plans configured
                        where configured.collection_source_id = ds.id and configured.enabled = true
                          and configured.archived_at is null
                          and not coalesce(jsonb_exists(configured.headers, 'ircsCatalogDiscovery'), false)
                   ) end
                   and not exists (
                       select 1 from collection_plan_details active
                        where active.collection_source_id = ds.id and active.status in ('QUEUED', 'RUNNING')
                          and coalesce(active.headers -> 'ircsCatalogDiscovery' ->> 'requestKey', '') <> ?
                   )
                   and not exists (
                       select 1 from collection_plan_details failed
                        where failed.collection_source_id = ds.id and failed.status = 'FAILED'
                          and failed.updated_at >= now() - interval '30 minutes'
                   )
                 order by history.last_attempt asc nulls first, exists (
                     select 1 from collection_plan_details successful
                      where successful.collection_source_id = ds.id and successful.status = 'COMPLETED'
                        and successful.stat_success > 0
                 ) desc, md5(ds.id::text || ?), ds.id
                 limit ?
                """,
                (rs, rowNum) -> new DataSourceSeedCandidate(rs.getObject("id", UUID.class), rs.getString("name")),
                allowedIds, requestKey, requestKey, requestKey, Math.max(1, Math.min(3, limit)));
    }

    public boolean existsTaskByName(String name) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from collection_plans where name = ? and archived_at is null)",
                Boolean.class,
                name);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<UUID> findTaskIdByName(String name) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select id from collection_plans where name = ? and archived_at is null",
                    UUID.class,
                    name));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<MediaRequestCandidate> claimPendingMediaRequests(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return jdbcTemplate.query(
                """
                with claimed as (
                    select id
                      from media_request_demands
                     where status = 'PENDING'
                     order by request_count desc, last_requested_at asc, id asc
                     limit ?
                     for update skip locked
                )
                update media_request_demands request
                   set status = 'SCHEDULING',
                       scheduled_at = now(),
                       updated_at = now(),
                       version = request.version + 1
                  from claimed
                 where request.id = claimed.id
                returning request.id, request.title, request.release_year, request.request_count
                """,
                (rs, rowNum) -> new MediaRequestCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        getInteger(rs, "release_year"),
                        rs.getInt("request_count")),
                safeLimit);
    }

    public UUID createMediaRequestBatch(List<MediaRequestCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("media request candidates are required");
        }
        UUID batchId = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into media_request_processing_batches (
                    id, created_at, updated_at, version, status, request_count
                ) values (?, ?, ?, 0, 'READY', ?)
                """,
                batchId,
                now,
                now,
                candidates.size());
        for (MediaRequestCandidate candidate : candidates) {
            jdbcTemplate.update(
                    """
                    insert into media_request_processing_batch_items (
                        id, batch_id, demand_id, created_at, updated_at, version,
                        title, release_year, request_count, status
                    ) values (?, ?, ?, ?, ?, 0, ?, ?, ?, 'READY')
                    """,
                    IrcsUuidGenerators.nextId(),
                    batchId,
                    candidate.id(),
                    now,
                    now,
                    candidate.title(),
                    candidate.releaseYear(),
                    candidate.requestCount());
        }
        jdbcTemplate.update(
                "update media_request_demands set status = 'BATCHED', current_batch_id = ?, updated_at = now(), "
                        + "version = version + 1, last_error_message = null where id in ("
                        + placeholders(candidates.size()) + ")",
                prepend(batchId, candidates.stream().map(MediaRequestCandidate::id).toList()).toArray());
        return batchId;
    }

    public void markMediaRequestsScheduled(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<UUID> safeIds = List.copyOf(ids);
        jdbcTemplate.update(
                "update media_request_demands set status = 'SCHEDULED', updated_at = now(), "
                        + "version = version + 1, last_error_message = null where id in (" + placeholders(safeIds.size()) + ")",
                safeIds.toArray());
    }

    public void markMediaRequestSkippedExisting(UUID mediaRequestId, UUID existingVideoId, String existingVideoSource) {
        jdbcTemplate.update(
                """
                update media_request_demands
                   set status = 'SKIPPED_EXISTS',
                       existing_video_id = ?,
                       existing_video_source = ?,
                       completed_at = now(),
                       current_batch_id = null,
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                """,
                existingVideoId,
                existingVideoSource,
                mediaRequestId);
    }

    public Page<MediaRequestBatchResponse> findMediaRequestBatches(Pageable pageable, String status) {
        Pageable safePageable = sanitize(pageable);
        List<Object> params = new ArrayList<>();
        String where = "";
        if (status != null && !status.isBlank()) {
            where = "where status = ?";
            params.add(status.trim().toUpperCase());
        }
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from media_request_processing_batches " + where,
                Long.class,
                params.toArray());

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageable.getPageSize());
        queryParams.add(safePageable.getOffset());
        List<MediaRequestBatchResponse> content = jdbcTemplate.query(
                """
                select id, status, request_count, scheduled_count, skipped_count, failed_count,
                       created_at, updated_at, started_at, cancelled_at, completed_at, last_error_message
                  from media_request_processing_batches
                %s
                 order by created_at desc, id desc
                 limit ? offset ?
                """.formatted(where),
                mediaRequestBatchMapper(false),
                queryParams.toArray());
        return new PageImpl<>(content, safePageable, total == null ? 0 : total);
    }

    public Optional<MediaRequestBatchResponse> findMediaRequestBatch(UUID batchId) {
        try {
            MediaRequestBatchResponse batch = jdbcTemplate.queryForObject(
                    """
                    select id, status, request_count, scheduled_count, skipped_count, failed_count,
                           created_at, updated_at, started_at, cancelled_at, completed_at, last_error_message
                      from media_request_processing_batches
                     where id = ?
                    """,
                    mediaRequestBatchMapper(true),
                    batchId);
            return Optional.ofNullable(batch);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public boolean mediaRequestBatchExists(UUID batchId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(select 1 from media_request_processing_batches where id = ?)",
                Boolean.class,
                batchId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean markMediaRequestBatchRunning(UUID batchId) {
        return jdbcTemplate.update(
                """
                update media_request_processing_batches
                   set status = 'RUNNING',
                       started_at = coalesce(started_at, now()),
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                   and status = 'READY'
                """,
                batchId) > 0;
    }

    public List<MediaRequestBatchItemCandidate> findReadyMediaRequestBatchItems(UUID batchId) {
        return jdbcTemplate.query(
                """
                select id, demand_id, title, release_year, request_count
                  from media_request_processing_batch_items
                 where batch_id = ?
                   and status = 'READY'
                 order by request_count desc, created_at asc, id asc
                """,
                (rs, rowNum) -> new MediaRequestBatchItemCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("demand_id", UUID.class),
                        rs.getString("title"),
                        getInteger(rs, "release_year"),
                        rs.getInt("request_count")),
                batchId);
    }

    public void markMediaRequestBatchItemSkipped(
            UUID itemId,
            UUID mediaRequestId,
            UUID existingVideoId,
            String existingVideoSource) {
        jdbcTemplate.update(
                """
                update media_request_processing_batch_items
                   set status = 'SKIPPED_EXISTS',
                       existing_video_id = ?,
                       existing_video_source = ?,
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                """,
                existingVideoId,
                existingVideoSource,
                itemId);
        jdbcTemplate.update(
                """
                update media_request_demands
                   set status = 'SKIPPED_EXISTS',
                       existing_video_id = ?,
                       existing_video_source = ?,
                       completed_at = now(),
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                """,
                existingVideoId,
                existingVideoSource,
                mediaRequestId);
    }

    public void markMediaRequestBatchItemScheduled(UUID itemId, UUID mediaRequestId, int scheduledTaskCount) {
        jdbcTemplate.update(
                """
                update media_request_processing_batch_items
                   set status = 'SCHEDULED',
                       scheduled_task_count = ?,
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                """,
                Math.max(0, scheduledTaskCount),
                itemId);
        jdbcTemplate.update(
                """
                update media_request_demands
                   set status = 'SCHEDULED',
                       scheduled_at = now(),
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = null
                 where id = ?
                """,
                mediaRequestId);
    }

    public void markMediaRequestBatchItemFailed(UUID itemId, UUID mediaRequestId, String reason) {
        jdbcTemplate.update(
                """
                update media_request_processing_batch_items
                   set status = 'FAILED',
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where id = ?
                """,
                truncate(reason),
                itemId);
        jdbcTemplate.update(
                """
                update media_request_demands
                   set status = 'FAILED',
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where id = ?
                """,
                truncate(reason),
                mediaRequestId);
    }

    public void finishMediaRequestBatch(UUID batchId, String status, String reason) {
        MediaRequestBatchCounters counters = mediaRequestBatchCounters(batchId);
        jdbcTemplate.update(
                """
                update media_request_processing_batches
                   set status = ?,
                       scheduled_count = ?,
                       skipped_count = ?,
                       failed_count = ?,
                       completed_at = case when ? in ('COMPLETED', 'FAILED', 'PARTIAL') then now() else completed_at end,
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where id = ?
                """,
                status,
                counters.scheduled(),
                counters.skipped(),
                counters.failed(),
                status,
                truncate(reason),
                batchId);
    }

    public boolean cancelMediaRequestBatch(UUID batchId, String reason) {
        int updated = jdbcTemplate.update(
                """
                update media_request_processing_batches
                   set status = 'CANCELLED',
                       cancelled_at = now(),
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where id = ?
                   and status = 'READY'
                """,
                truncate(reason),
                batchId);
        if (updated <= 0) {
            return false;
        }
        jdbcTemplate.update(
                """
                update media_request_processing_batch_items
                   set status = 'CANCELLED',
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where batch_id = ?
                   and status = 'READY'
                """,
                truncate(reason),
                batchId);
        jdbcTemplate.update(
                """
                update media_request_demands
                   set status = 'CANCELLED',
                       updated_at = now(),
                       version = version + 1,
                       last_error_message = ?
                 where current_batch_id = ?
                   and status in ('BATCHED', 'SCHEDULING')
                """,
                truncate(reason),
                batchId);
        jdbcTemplate.update(
                """
                update collection_plans
                   set enabled = false, updated_at = now()
                 where headers -> ? ->> 'batchId' = ? and current_run_id is null
                """,
                MEDIA_REQUEST_BATCH_HEADER_KEY,
                batchId.toString());
        return true;
    }

    public List<UUID> findMediaRequestCollectionTaskIds(UUID batchId) {
        return jdbcTemplate.query(
                """
                select id
                  from collection_plans
                 where archived_at is null and headers -> ? ->> 'batchId' = ?
                 order by created_at desc, id desc
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                MEDIA_REQUEST_BATCH_HEADER_KEY,
                batchId.toString());
    }

    public void markMediaRequestsPending(List<UUID> ids, String reason) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(reason);
        args.addAll(ids);
        jdbcTemplate.update(
                "update media_request_demands set status = 'PENDING', updated_at = now(), "
                        + "version = version + 1, last_error_message = ? where id in (" + placeholders(ids.size()) + ")",
                args.toArray());
    }

    public UUID create(ValidatedCreateTask task) {
        UUID id = IrcsUuidGenerators.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                insert into collection_plans (
                    id, created_at, updated_at, name, enabled, cron_expression, time_zone,
                    task_type, start_page, end_page, filter_type, filter_hours, filter_keywords,
                    request_delay_type, fixed_delay_ms, random_delay_min_ms, random_delay_max_ms, timeout_ms,
                    max_retries, user_agent, enable_random_ua, use_custom_proxy, proxy_type, proxy_host,
                    proxy_port, proxy_username, proxy_password, headers, collection_source_id
                ) values (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, cast(? as jsonb), ?
                )
                """,
                id,
                now,
                now,
                task.name(),
                task.enabled(),
                task.cronExpression(),
                task.timeZone(),
                task.taskType(),
                task.startPage(),
                task.endPage(),
                task.filterType(),
                task.filterHours(),
                task.filterKeywords(),
                task.requestDelayType(),
                task.fixedDelayMs(),
                task.randomDelayMinMs(),
                task.randomDelayMaxMs(),
                task.timeoutMs(),
                task.maxRetries(),
                task.userAgent(),
                task.enableRandomUa(),
                task.useCustomProxy(),
                task.proxyType(),
                task.proxyHost(),
                task.proxyPort(),
                task.proxyUsername(),
                task.proxyPassword(),
                task.headers(),
                task.dataSourceId());
        return id;
    }

    public boolean update(UUID id, ValidatedUpdateTask task) {
        Map<String, Object> updates = new LinkedHashMap<>();
        putIfPresent(updates, "name", task.name());
        putIfPresent(updates, "enabled", task.enabled());
        putIfPresent(updates, "cron_expression", task.cronExpression());
        putIfPresent(updates, "time_zone", task.timeZone());
        putIfPresent(updates, "collection_source_id", task.dataSourceId());
        putIfPresent(updates, "task_type", task.taskType());
        putIfPresent(updates, "start_page", task.startPage());
        putIfPresent(updates, "end_page", task.endPage());
        putIfPresent(updates, "filter_type", task.filterType());
        putIfPresent(updates, "filter_hours", task.filterHours());
        putIfPresent(updates, "filter_keywords", task.filterKeywords());
        putIfPresent(updates, "request_delay_type", task.requestDelayType());
        putIfPresent(updates, "fixed_delay_ms", task.fixedDelayMs());
        putIfPresent(updates, "random_delay_min_ms", task.randomDelayMinMs());
        putIfPresent(updates, "random_delay_max_ms", task.randomDelayMaxMs());
        putIfPresent(updates, "timeout_ms", task.timeoutMs());
        putIfPresent(updates, "max_retries", task.maxRetries());
        putIfPresent(updates, "user_agent", task.userAgent());
        putIfPresent(updates, "enable_random_ua", task.enableRandomUa());
        putIfPresent(updates, "use_custom_proxy", task.useCustomProxy());
        putIfPresent(updates, "proxy_type", task.proxyType());
        putIfPresent(updates, "proxy_host", task.proxyHost());
        putIfPresent(updates, "proxy_port", task.proxyPort());
        putIfPresent(updates, "proxy_username", task.proxyUsername());
        putIfPresent(updates, "proxy_password", task.proxyPassword());

        List<Object> params = new ArrayList<>();
        List<String> setClauses = new ArrayList<>();
        updates.forEach((column, value) -> {
            setClauses.add(column + " = ?");
            params.add(value);
        });
        if (task.headers() != null) {
            setClauses.add("headers = cast(? as jsonb)");
            params.add(task.headers());
        }
        setClauses.add("updated_at = ?");
        params.add(Timestamp.from(Instant.now()));
        params.add(id);

        int count = jdbcTemplate.update(
                "update collection_plans set " + String.join(", ", setClauses) + " where id = ? and archived_at is null",
                params.toArray());
        return count > 0;
    }

    public boolean lockPlan(UUID id) {
        return !jdbcTemplate.queryForList(
                "select id from collection_plans where id = ? and archived_at is null for update", UUID.class, id)
                .isEmpty();
    }

    public void archive(UUID id) {
        jdbcTemplate.update("""
                update collection_plans
                   set archived_at = now(), enabled = false, cron_expression = null,
                       updated_at = now(), headers = null, proxy_username = null, proxy_password = null
                 where id = ? and archived_at is null
                """, id);
    }

    public Optional<UUID> currentRunId(UUID planId) {
        return jdbcTemplate.query("""
                select current_run_id
                  from collection_plans
                 where id = ? and archived_at is null and current_run_id is not null
                """, (rs, row) -> rs.getObject("current_run_id", UUID.class), planId).stream().findFirst();
    }

    public Optional<TaskRuntimeState> findRuntimeState(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select id, collection_source_id, status, enabled, start_page, current_page, current_run_id
                    from collection_plan_details
                    where id = ?
                    """,
                    (rs, rowNum) -> new TaskRuntimeState(
                            rs.getObject("id", UUID.class),
                            rs.getObject("collection_source_id", UUID.class),
                            rs.getString("status"),
                            rs.getBoolean("enabled"),
                            getInteger(rs, "start_page"),
                            getInteger(rs, "current_page"),
                            rs.getObject("current_run_id", UUID.class)),
                    id));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public Optional<TaskExecutionPlan> findExecutionPlan(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select r.id,
                           options.name,
                           r.collection_source_id,
                           r.status,
                           p.enabled,
                           r.start_page,
                           options.end_page,
                           r.checkpoint_page as current_page,
                           options.filter_type,
                           options.filter_hours,
                           options.filter_keywords,
                           options.fixed_delay_ms,
                           options.user_agent,
                           options.enable_random_ua,
                           options.use_custom_proxy,
                           options.proxy_type,
                           options.proxy_host,
                           options.proxy_port,
                           p.proxy_username,
                           p.proxy_password,
                           p.headers::text
                    from collection_runs r
                    join collection_plans p on p.current_run_id = r.id and p.id = r.collection_plan_id
                    cross join lateral jsonb_populate_record(null::collection_plans, r.execution_parameters) options
                    where r.id = ? and p.archived_at is null
                    """,
                    (rs, rowNum) -> new TaskExecutionPlan(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getObject("collection_source_id", UUID.class),
                            rs.getString("status"),
                            rs.getBoolean("enabled"),
                            getInteger(rs, "start_page"),
                            getInteger(rs, "end_page"),
                            getInteger(rs, "current_page"),
                            rs.getString("filter_type"),
                            getInteger(rs, "filter_hours"),
                            rs.getString("filter_keywords"),
                            getInteger(rs, "fixed_delay_ms"),
                            rs.getString("user_agent"),
                            rs.getBoolean("enable_random_ua"),
                            rs.getBoolean("use_custom_proxy"),
                            rs.getString("proxy_type"),
                            rs.getString("proxy_host"),
                            getInteger(rs, "proxy_port"),
                            rs.getString("proxy_username"),
                            rs.getString("proxy_password"),
                            rs.getString("headers")),
                    id));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<UUID> findActiveTaskIds(int limit) {
        int safeLimit = Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
        return jdbcTemplate.query(
                """
                select id
                from collection_runs
                where status in ('QUEUED', 'RUNNING')
                order by updated_at asc, id asc
                limit ?
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                safeLimit);
    }

    public Optional<ScheduledTaskDefinition> findScheduledTask(UUID id) {
        return findScheduledTasks().stream()
                .filter(task -> task.id().equals(id))
                .findFirst();
    }

    public List<ScheduledTaskDefinition> findScheduledTasks() {
        return jdbcTemplate.query(
                """
                select id, name, enabled, cron_expression, time_zone
                from collection_plans
                where enabled = true
                  and archived_at is null
                  and cron_expression is not null
                  and btrim(cron_expression) <> ''
                order by name asc, id asc
                """,
                (rs, rowNum) -> new ScheduledTaskDefinition(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getBoolean("enabled"),
                        rs.getString("cron_expression"),
                        rs.getString("time_zone")));
    }

    public String statusOf(UUID id) {
        try {
            return jdbcTemplate.queryForObject(
                    "select status from collection_runs where id = ?",
                    String.class,
                    id);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    public boolean existsActiveTaskForDataSource(UUID dataSourceId, UUID excludedPlanId) {
        return existsSourceConflict(dataSourceId, excludedPlanId, true);
    }

    public boolean existsCatalogSourceConflict(UUID dataSourceId, UUID excludedPlanId) {
        return existsSourceConflict(dataSourceId, excludedPlanId, true);
    }

    private boolean existsSourceConflict(UUID sourceId, UUID planId, boolean exclusiveSource) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from collection_runs
                    where collection_source_id = ?
                      and collection_plan_id <> ?
                      and status in ('QUEUED', 'RUNNING')
                      and (? or exclusive_source)
                )
                """, Boolean.class, sourceId, planId, exclusiveSource));
    }

    @Transactional
    public Optional<UUID> queueRun(TaskRuntimeState expected, boolean resume, boolean exclusiveSource) {
        // Serialize exclusive and shared starts for the same source without blocking source FK reads.
        if (jdbcTemplate.queryForList(
                "select id from collection_sources where id = ? for no key update",
                UUID.class, expected.dataSourceId()).isEmpty() || !lockPlan(expected.id())) {
            return Optional.empty();
        }
        TaskRuntimeState current = findRuntimeState(expected.id()).orElseThrow();
        if (!Boolean.TRUE.equals(current.enabled()) || !Objects.equals(current.dataSourceId(), expected.dataSourceId())
                || !Objects.equals(current.currentRunId(), expected.currentRunId())
                || "QUEUED".equals(current.status()) || "RUNNING".equals(current.status())
                || existsSourceConflict(current.dataSourceId(), current.id(), exclusiveSource)) {
            return Optional.empty();
        }
        UUID runId = IrcsUuidGenerators.nextId();
        int startPage = resume
                ? effectiveCurrentPage(current.currentPage(), current.startPage())
                : effectiveStartPage(current.startPage());

        // Keep credentials and request headers on the plan instead of multiplying them across runs.
        jdbcTemplate.update("""
                insert into collection_runs (
                    id, collection_plan_id, collection_source_id, resumed_from_run_id, exclusive_source,
                    execution_parameters, status, start_page, checkpoint_page
                )
                select ?, p.id, p.collection_source_id, ?, ?,
                       jsonb_build_object(
                           'name', p.name, 'task_type', p.task_type, 'end_page', p.end_page,
                           'filter_type', p.filter_type, 'filter_hours', p.filter_hours,
                           'filter_keywords', p.filter_keywords, 'user_agent', p.user_agent,
                           'enable_random_ua', p.enable_random_ua, 'use_custom_proxy', p.use_custom_proxy,
                           'proxy_type', p.proxy_type, 'proxy_host', p.proxy_host, 'proxy_port', p.proxy_port,
                           'request_delay_type', p.request_delay_type, 'fixed_delay_ms', p.fixed_delay_ms,
                           'random_delay_min_ms', p.random_delay_min_ms, 'random_delay_max_ms', p.random_delay_max_ms,
                           'timeout_ms', p.timeout_ms, 'max_retries', p.max_retries
                       ), 'QUEUED', ?, ?
                  from collection_plans p where p.id = ?
                """, runId, resume ? current.currentRunId() : null, exclusiveSource, startPage, startPage, current.id());
        jdbcTemplate.update("update collection_plans set current_run_id = ? where id = ?", runId, current.id());
        return Optional.of(runId);
    }

    public boolean markRunning(UUID runId) {
        return jdbcTemplate.update("""
                update collection_runs
                set status = 'RUNNING', started_at = now(), updated_at = now()
                where id = ? and status = 'QUEUED'
                """, runId) > 0;
    }

    public void complete(UUID runId, long processed, long success, long failed) {
        jdbcTemplate.update("""
                update collection_runs
                set status = 'COMPLETED', finished_at = now(), updated_at = now(),
                    processed_count = ?, success_count = ?, failure_count = ?
                where id = ? and status = 'RUNNING'
                """, processed, success, failed, runId);
    }

    public void flushRuntimeLedger(TaskDbRuntimeSnapshot snapshot) {
        jdbcTemplate.update("""
                update collection_runs
                set status = case when status = 'RUNNING' and ? = 'QUEUED' then 'RUNNING' else ? end,
                    checkpoint_page = greatest(checkpoint_page, ?),
                    total_found = greatest(total_found, ?),
                    processed_count = greatest(processed_count, ?),
                    success_count = greatest(success_count, ?),
                    failure_count = greatest(failure_count, ?),
                    started_at = coalesce(started_at, ?),
                    finished_at = ?,
                    last_error_message = ?,
                    updated_at = greatest(updated_at, ?)
                where id = ? and status in ('QUEUED', 'RUNNING')
                  and ? >= updated_at
                """,
                snapshot.status(), snapshot.status(), snapshot.currentPage(), snapshot.totalFound(),
                snapshot.processed(), snapshot.success(), snapshot.failed(), toTimestamp(snapshot.startedAt()),
                toTimestamp(snapshot.endedAt()), truncate(snapshot.lastError()), toTimestamp(snapshot.flushedAt()),
                snapshot.masterTaskId(), toTimestamp(snapshot.flushedAt()));
    }

    public void fail(UUID runId, String errorMessage) {
        finishActiveRun(runId, "FAILED", errorMessage);
    }

    public void markPaused(UUID runId, String message) {
        finishActiveRun(runId, "PAUSED", message);
    }

    private void finishActiveRun(UUID runId, String status, String message) {
        jdbcTemplate.update("""
                update collection_runs
                set status = ?, finished_at = now(), updated_at = now(), last_error_message = ?
                where id = ? and status in ('QUEUED', 'RUNNING')
                """, status, truncate(message), runId);
    }

    public int recoverInterruptedTasks(String message) {
        return jdbcTemplate.update("""
                update collection_runs
                set status = 'PAUSED', finished_at = now(), updated_at = now(), last_error_message = ?
                where status in ('QUEUED', 'RUNNING') and updated_at < now() - interval '30 minutes'
                """, message);
    }

    public int failStaleActiveTasks(Instant threshold, String message) {
        return jdbcTemplate.update("""
                update collection_runs
                set status = 'FAILED', finished_at = now(), updated_at = now(), last_error_message = ?
                where status in ('QUEUED', 'RUNNING') and updated_at < ?
                """, message, Timestamp.from(threshold));
    }

    public Optional<UUID> pause(UUID planId) {
        return holdCurrentRun(planId, "PAUSED", "Task paused by operator.");
    }

    public Optional<UUID> stop(UUID planId) {
        return holdCurrentRun(planId, "STOPPED", "Task stopped by operator.");
    }

    private Optional<UUID> holdCurrentRun(UUID planId, String status, String reason) {
        if (!lockPlan(planId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                update collection_runs r
                set status = ?, finished_at = now(), updated_at = now(), last_error_message = ?
                from collection_plans p
                where p.id = ? and r.id = p.current_run_id and r.status in ('QUEUED', 'RUNNING')
                returning r.id
                """, (rs, row) -> rs.getObject("id", UUID.class), status, reason, planId).stream().findFirst();
    }

    public Page<TaskCardSummary> findAll(
            Pageable pageable,
            String name,
            String status,
            UUID dataSourceId,
            Boolean enabled
    ) {
        return findAll(pageable, name, status, dataSourceId, enabled, null);
    }

    public Page<TaskCardSummary> findAll(
            Pageable pageable,
            String name,
            String status,
            UUID dataSourceId,
            Boolean enabled,
            UUID mediaRequestBatchId
    ) {
        Pageable safePageable = sanitize(pageable);
        List<Object> whereParams = new ArrayList<>();
        String where = buildWhere(name, status, dataSourceId, enabled, mediaRequestBatchId, whereParams);

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from collection_plan_details t " + where,
                Long.class,
                whereParams.toArray());

        List<Object> queryParams = new ArrayList<>(whereParams);
        queryParams.add(safePageable.getPageSize());
        queryParams.add(safePageable.getOffset());

        List<TaskCardSummary> content = jdbcTemplate.query(
                """
                select t.id,
                       t.name,
                       t.status,
                       t.enabled,
                       ds.name as data_source_name,
                       t.cron_expression,
                       t.time_zone,
                       t.filter_type,
                       t.filter_hours,
                       t.filter_keywords,
                       t.start_page,
                       t.end_page,
                       t.current_page,
                       t.last_execution_time,
                       t.stat_processed,
                       t.stat_total_found,
                       t.stat_success,
                       t.stat_failed,
                       t.stat_inserted,
                       t.stat_updated,
                       t.stat_ignored,
                       t.stat_start_time,
                       t.stat_end_time
                from collection_plan_details t
                left join collection_sources ds on t.collection_source_id = ds.id
                %s
                order by t.created_at desc nulls last, t.name asc, t.id asc
                limit ? offset ?
                """.formatted(where),
                cardMapper(),
                queryParams.toArray());

        return new PageImpl<>(content, safePageable, total == null ? 0 : total);
    }

    public Optional<TaskDetailSummary> findById(UUID id) {
        return findDetail("t.id", id);
    }

    public Optional<TaskDetailSummary> findCurrentRunDetail(UUID runId) {
        return findDetail("t.current_run_id", runId);
    }

    private Optional<TaskDetailSummary> findDetail(String lookupColumn, UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    select t.id,
                           t.name,
                           t.status,
                           t.enabled,
                           t.cron_expression,
                           t.time_zone,
                           t.collection_source_id,
                           ds.name as data_source_name,
                           t.task_type,
                           t.start_page,
                           t.end_page,
                           t.current_page,
                           t.filter_type,
                           t.filter_hours,
                           t.filter_keywords,
                           t.request_delay_type,
                           t.fixed_delay_ms,
                           t.random_delay_min_ms,
                           t.random_delay_max_ms,
                           t.timeout_ms,
                           t.max_retries,
                           t.user_agent,
                           t.enable_random_ua,
                           t.use_custom_proxy,
                           t.proxy_type,
                           t.proxy_host,
                           t.proxy_port,
                           t.proxy_username,
                           t.headers,
                           t.last_execution_time,
                           t.created_at,
                           t.updated_at,
                           t.last_error_message,
                           t.stat_total_found,
                           t.stat_processed,
                           t.stat_success,
                           t.stat_failed,
                           t.stat_inserted,
                           t.stat_updated,
                           t.stat_ignored,
                           t.stat_start_time,
                           t.stat_end_time
                    from collection_plan_details t
                    left join collection_sources ds on t.collection_source_id = ds.id
                    where %s = ?
                    """.formatted(lookupColumn),
                    detailMapper(),
                    id));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private String buildWhere(
            String name,
            String status,
            UUID dataSourceId,
            Boolean enabled,
            UUID mediaRequestBatchId,
            List<Object> params
    ) {
        List<String> predicates = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            predicates.add("lower(t.name) like ?");
            params.add("%" + name.trim().toLowerCase() + "%");
        }
        if (status != null && !status.isBlank()) {
            predicates.add("t.status = ?");
            params.add(status.trim().toUpperCase());
        }
        if (dataSourceId != null) {
            predicates.add("t.collection_source_id = ?");
            params.add(dataSourceId);
        }
        if (enabled != null) {
            predicates.add("t.enabled = ?");
            params.add(enabled);
        }
        if (mediaRequestBatchId != null) {
            predicates.add("t.headers -> ? ->> 'batchId' = ?");
            params.add(MEDIA_REQUEST_BATCH_HEADER_KEY);
            params.add(mediaRequestBatchId.toString());
        }
        if (predicates.isEmpty()) {
            return "";
        }
        return "where " + String.join(" and ", predicates);
    }

    private Pageable sanitize(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int requestedSize = pageable.isPaged() ? pageable.getPageSize() : 20;
        int size = Math.min(Math.max(1, requestedSize), MAX_PAGE_SIZE);
        return PageRequest.of(page, size);
    }

    private RowMapper<TaskCardSummary> cardMapper() {
        return (rs, rowNum) -> new TaskCardSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("status"),
                rs.getBoolean("enabled"),
                rs.getString("data_source_name"),
                rs.getString("cron_expression"),
                rs.getString("time_zone"),
                rs.getString("filter_type"),
                getInteger(rs, "filter_hours"),
                rs.getString("filter_keywords"),
                getInteger(rs, "start_page"),
                getInteger(rs, "end_page"),
                getInteger(rs, "current_page"),
                toInstant(rs.getTimestamp("last_execution_time")),
                getLong(rs, "stat_processed"),
                getLong(rs, "stat_total_found"),
                getLong(rs, "stat_success"),
                getLong(rs, "stat_failed"),
                getLong(rs, "stat_inserted"),
                getLong(rs, "stat_updated"),
                getLong(rs, "stat_ignored"),
                toInstant(rs.getTimestamp("stat_start_time")),
                toInstant(rs.getTimestamp("stat_end_time")));
    }

    private RowMapper<TaskDetailSummary> detailMapper() {
        return (rs, rowNum) -> new TaskDetailSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("status"),
                rs.getBoolean("enabled"),
                rs.getString("cron_expression"),
                rs.getString("time_zone"),
                rs.getObject("collection_source_id", UUID.class),
                rs.getString("data_source_name"),
                rs.getString("task_type"),
                getInteger(rs, "start_page"),
                getInteger(rs, "end_page"),
                getInteger(rs, "current_page"),
                rs.getString("filter_type"),
                getInteger(rs, "filter_hours"),
                rs.getString("filter_keywords"),
                rs.getString("request_delay_type"),
                getInteger(rs, "fixed_delay_ms"),
                getInteger(rs, "random_delay_min_ms"),
                getInteger(rs, "random_delay_max_ms"),
                getInteger(rs, "timeout_ms"),
                getInteger(rs, "max_retries"),
                rs.getString("user_agent"),
                rs.getBoolean("enable_random_ua"),
                rs.getBoolean("use_custom_proxy"),
                rs.getString("proxy_type"),
                rs.getString("proxy_host"),
                getInteger(rs, "proxy_port"),
                rs.getString("proxy_username"),
                null,
                rs.getString("headers"),
                toInstant(rs.getTimestamp("last_execution_time")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getString("last_error_message"),
                getLong(rs, "stat_total_found"),
                getLong(rs, "stat_processed"),
                getLong(rs, "stat_success"),
                getLong(rs, "stat_failed"),
                getLong(rs, "stat_inserted"),
                getLong(rs, "stat_updated"),
                getLong(rs, "stat_ignored"),
                toInstant(rs.getTimestamp("stat_start_time")),
                toInstant(rs.getTimestamp("stat_end_time")));
    }

    private RowMapper<MediaRequestBatchResponse> mediaRequestBatchMapper(boolean includeItems) {
        return (rs, rowNum) -> {
            UUID batchId = rs.getObject("id", UUID.class);
            return new MediaRequestBatchResponse(
                    batchId,
                    rs.getString("status"),
                    rs.getInt("request_count"),
                    rs.getInt("scheduled_count"),
                    rs.getInt("skipped_count"),
                    rs.getInt("failed_count"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")),
                    toInstant(rs.getTimestamp("started_at")),
                    toInstant(rs.getTimestamp("cancelled_at")),
                    toInstant(rs.getTimestamp("completed_at")),
                    rs.getString("last_error_message"),
                    includeItems ? findMediaRequestBatchItems(batchId) : List.of());
        };
    }

    private List<MediaRequestBatchItemResponse> findMediaRequestBatchItems(UUID batchId) {
        return jdbcTemplate.query(
                """
                select id, demand_id, title, release_year, request_count, status,
                       existing_video_id, existing_video_source, scheduled_task_count, last_error_message
                  from media_request_processing_batch_items
                 where batch_id = ?
                 order by request_count desc, created_at asc, id asc
                """,
                (rs, rowNum) -> new MediaRequestBatchItemResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("demand_id", UUID.class),
                        rs.getString("title"),
                        getInteger(rs, "release_year"),
                        rs.getInt("request_count"),
                        rs.getString("status"),
                        rs.getObject("existing_video_id", UUID.class),
                        rs.getString("existing_video_source"),
                        rs.getInt("scheduled_task_count"),
                        rs.getString("last_error_message")),
                batchId);
    }

    private MediaRequestBatchCounters mediaRequestBatchCounters(UUID batchId) {
        return jdbcTemplate.queryForObject(
                """
                select count(*) filter (where status = 'SCHEDULED') as scheduled,
                       count(*) filter (where status = 'SKIPPED_EXISTS') as skipped,
                       count(*) filter (where status = 'FAILED') as failed
                  from media_request_processing_batch_items
                 where batch_id = ?
                """,
                (rs, rowNum) -> new MediaRequestBatchCounters(
                        rs.getInt("scheduled"),
                        rs.getInt("skipped"),
                        rs.getInt("failed")),
                batchId);
    }

    private Integer getInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long getLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private void putIfPresent(Map<String, Object> updates, String column, Object value) {
        if (value != null) {
            updates.put(column, value);
        }
    }

    private int effectiveStartPage(Integer startPage) {
        return startPage != null && startPage > 0 ? startPage : 1;
    }

    private int effectiveCurrentPage(Integer currentPage, Integer startPage) {
        return currentPage != null && currentPage > 0 ? currentPage : effectiveStartPage(startPage);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(Math.max(1, size), "?"));
    }

    private List<Object> prepend(Object first, List<?> rest) {
        List<Object> result = new ArrayList<>();
        result.add(first);
        if (rest != null) {
            result.addAll(rest);
        }
        return result;
    }

    private record MediaRequestBatchCounters(int scheduled, int skipped, int failed) {
    }
}
