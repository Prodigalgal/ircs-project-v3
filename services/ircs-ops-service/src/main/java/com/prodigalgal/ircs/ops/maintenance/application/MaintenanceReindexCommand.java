package com.prodigalgal.ircs.ops.maintenance.application;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.ops.config.OpsConfigValues;
import com.prodigalgal.ircs.ops.maintenance.domain.MaintenanceRunResult;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceReindexCommand {

    public static final String TASK_UNIFIED_REINDEX = "search-reindex-unified";
    public static final String TASK_RAW_REINDEX = "search-reindex-raw";

    static final int DEFAULT_DEV_LIMIT = OpsConfigValues.DEFAULT_REINDEX_DEV_LIMIT;
    static final int MAX_DEV_LIMIT = OpsConfigValues.MAX_REINDEX_DEV_LIMIT;
    private static final String SELECT_RAW_IDS = """
            select id
              from raw_videos
             order by updated_at desc nulls last, id
             limit :limit
            """;
    private static final String SELECT_UNIFIED_IDS = """
            select id
              from unified_videos
             order by updated_at desc nulls last, id
             limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MaintenanceSearchSyncClient searchSyncClient;
    private final OpsConfigValues configValues;

    public MaintenanceReindexCommand(
            NamedParameterJdbcTemplate jdbcTemplate,
            MaintenanceSearchSyncClient searchSyncClient,
            OpsConfigValues configValues) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchSyncClient = searchSyncClient;
        this.configValues = configValues;
    }

    public MaintenanceRunResult enqueueUnifiedIndex() {
        return enqueueUnifiedIndex(null);
    }

    public MaintenanceRunResult enqueueUnifiedIndex(String correlationId) {
        return enqueueIndex(TASK_UNIFIED_REINDEX, SELECT_UNIFIED_IDS, SearchEntityType.UNIFIED_VIDEO, correlationId);
    }

    public MaintenanceRunResult enqueueRawIndex() {
        return enqueueRawIndex(null);
    }

    public MaintenanceRunResult enqueueRawIndex(String correlationId) {
        return enqueueIndex(TASK_RAW_REINDEX, SELECT_RAW_IDS, SearchEntityType.RAW_VIDEO, correlationId);
    }

    public MaintenanceRunResult hardResetUnifiedIndex(String correlationId) {
        int changed = searchSyncClient.hardResetIndex(SearchEntityType.UNIFIED_VIDEO, correlationId);
        return new MaintenanceRunResult("aggregation-reset.search-hard-reset", 1, changed, List.of());
    }

    private MaintenanceRunResult enqueueIndex(
            String taskName,
            String selectSql,
            SearchEntityType entityType,
            String correlationId) {
        int devLimit = configValues.reindexDevLimit();
        if (devLimit == 0) {
            return new MaintenanceRunResult(taskName, 0, 0, List.of());
        }

        List<UUID> ids = jdbcTemplate.query(
                selectSql,
                new MapSqlParameterSource("limit", devLimit),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return new MaintenanceRunResult(taskName, 0, 0, List.of());
        }

        int accepted = searchSyncClient.enqueueIndex(ids, entityType, correlationId);

        return new MaintenanceRunResult(taskName, ids.size(), accepted, ids);
    }
}
