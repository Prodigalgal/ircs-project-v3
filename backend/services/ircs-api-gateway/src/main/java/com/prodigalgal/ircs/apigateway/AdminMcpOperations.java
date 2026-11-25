package com.prodigalgal.ircs.apigateway;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;

final class AdminMcpOperations {

    static final List<Operation> ALL = registeredOperations();

    private AdminMcpOperations() {}

    private static List<Operation> registeredOperations() {
        var operations = new ArrayList<Operation>();
        addCrud(operations, "source", "/data-sources", "资源站配置");
        addCrud(operations, "collection_task", "/collection-tasks", "采集计划");
        addCrud(operations, "raw_video", "/raw-videos", "来源视频");
        addCrud(operations, "unified_video", "/unified-videos", "聚合视频");
        addCrud(operations, "playlist", "/playlists", "播放列表");
        addCrud(operations, "resolver", "/resolvers", "播放解析器");
        addCrud(operations, "category", "/standard-categories", "标准分类");
        addCrud(operations, "genre", "/standard-genres", "标准类型");
        addCrud(operations, "credential", "/credentials", "供应商凭据配置");
        operations.addAll(List.of(
                get("collection_task_runtime", "/collection-tasks/{id}/runtime", "查看采集任务的运行进度"),
                get("collection_task_logs", "/collection-tasks/{id}/logs", "查看采集任务日志"),
                post("collection_task_start", "/collection-tasks/{id}/start", "启动已启用的采集任务", false),
                post("collection_task_pause", "/collection-tasks/{id}/pause", "暂停采集任务", false),
                post("collection_task_resume", "/collection-tasks/{id}/resume", "继续采集任务", false),
                post("collection_task_stop", "/collection-tasks/{id}/stop", "停止采集任务", false),
                get("media_request_list", "/media-request-batches", "查询求片处理批次"),
                get("media_request_get", "/media-request-batches/{id}", "查询求片处理批次详情"),
                post("media_request_start", "/media-request-batches/{id}/start", "启动求片处理批次", false),
                post("media_request_cancel", "/media-request-batches/{id}/cancel", "取消求片处理批次", false),
                post("raw_video_refetch", "/raw-videos/{id}/refetch", "让采集服务重新拉取该来源视频", false),
                post("raw_video_renormalize", "/raw-videos/{id}/renormalize", "重新清洗该来源视频", false),
                post("raw_video_reenrich", "/raw-videos/{id}/reenrich", "为该来源视频重新查询和回填权威资料", false),
                post("unified_video_recalculate", "/unified-videos/{id}/recalculate", "重算指定聚合视频的展示资料", false),
                post("unified_video_merge", "/aggregation/unified-videos/merge", "按后台接口合并指定聚合视频", true),
                get("source_domain_list", "/source-domains", "查看资源站的线路域名"),
                get("source_domain_get", "/source-domains/{id}", "查看单个线路域名"),
                change("source_domain_update", HttpMethod.PUT, "/source-domains/{id}", "更新线路域名配置", true),
                get("area_list", "/standard-areas/page", "分页查询标准地区"),
                post("area_create", "/standard-areas", "建立标准地区", true),
                get("language_list", "/standard-languages", "查询标准语言"),
                post("language_create", "/standard-languages", "建立标准语言", true),
                get("member_list", "/members", "分页查询会员"),
                change("member_update", HttpMethod.PUT, "/members/{id}", "更新会员资料", true),
                change("member_set_status", HttpMethod.PUT, "/members/{id}/status", "更新会员状态", true),
                change("member_delete", HttpMethod.DELETE, "/members/{id}", "删除指定会员", false),
                get("message_list", "/messages", "查询留言"),
                post("message_reply", "/messages/{id}/reply", "回复指定留言", true),
                change("message_set_visibility", HttpMethod.PUT, "/messages/{id}/visibility", "更新留言可见性", true),
                change("message_delete", HttpMethod.DELETE, "/messages/{id}", "删除指定留言", false),
                get("config_list", "/configs", "分页查看运行设置"),
                get("config_get", "/configs/{key}", "查看单项运行设置"),
                post("config_create", "/configs", "新建运行设置", true),
                change("config_update", HttpMethod.PUT, "/configs/{key}", "更新运行设置", true),
                change("config_delete", HttpMethod.DELETE, "/configs/{key}", "删除运行设置", false),
                post("credential_refresh", "/credentials/refresh", "刷新供应商凭据池", false),
                get("magnet_provider_list", "/magnet-providers", "查询磁链提供方"),
                post("magnet_provider_create", "/magnet-providers", "配置磁链提供方", true),
                change("magnet_provider_update", HttpMethod.PUT, "/magnet-providers/{id}", "更新磁链提供方", true),
                get("magnet_list", "/magnets/unified/{id}/links", "查看指定聚合视频的磁链"),
                post("magnet_search", "/magnets/search/unified/{id}", "为指定聚合视频发起磁链搜索", false),
                change("magnet_set_status", HttpMethod.PUT, "/magnets/unified/{id}/links/{linkId}/status",
                        "审批或拒绝指定磁链", true),
                change("magnet_delete", HttpMethod.DELETE, "/magnets/unified/{id}/links/{linkId}", "删除指定磁链", false),
                get("cover_list", "/cover-images", "查询封面状态"),
                get("cover_get", "/cover-images/{id}", "查看单张封面状态"),
                post("cover_retry", "/cover-images/{id}/retry", "重试指定封面的处理", false),
                post("cover_sync_r2", "/cover-images/{id}/sync-r2", "将指定封面同步到对象存储", false),
                change("cover_delete", HttpMethod.DELETE, "/cover-images/{id}", "删除指定封面", false),
                get("dashboard", "/dashboard/statistics", "查看站点运行看板"),
                get("traffic", "/ops/traffic/status", "查看当前流量情况"),
                get("worker_audit_summary", "/ops/worker-job-audit/summary", "查看后台任务执行统计"),
                get("incidents", "/ops-alert/incidents", "查看告警事件"),
                get("runtime_dlq_list", "/ops/runtime-dlq", "查看后台任务死信"),
                new Operation("runtime_dlq_requeue", "重投指定类型的死信任务，query 需提供 taskType",
                        HttpMethod.POST, "/ops/runtime-dlq/requeue", false, true),
                get("search_sync_stats", "/search/ops/search-sync-tasks/stats", "查看搜索索引同步积压"),
                get("maintenance_active", "/ops/maintenance/active", "查询正在运行的维护任务"),
                get("service_restart_capabilities", "/ops/service-restarts/capabilities", "查看后台可重启的服务"),
                post("service_restart", "/ops/service-restarts", "通过已有维护接口重启指定服务", true)
        ));
        return List.copyOf(operations);
    }

    private static void addCrud(List<Operation> operations, String name, String path, String label) {
        operations.add(get(name + "_list", path, "查询" + label));
        operations.add(get(name + "_get", path + "/{id}", "查看指定" + label));
        operations.add(post(name + "_create", path, "新建" + label, true));
        operations.add(change(name + "_update", HttpMethod.PUT, path + "/{id}", "更新指定" + label, true));
        operations.add(change(name + "_delete", HttpMethod.DELETE, path + "/{id}", "删除指定" + label, false));
    }

    private static Operation get(String name, String path, String description) {
        return new Operation(name, description, HttpMethod.GET, path, false, true);
    }

    private static Operation post(String name, String path, String description, boolean body) {
        return change(name, HttpMethod.POST, path, description, body);
    }

    private static Operation change(String name, HttpMethod method, String path, String description, boolean body) {
        return new Operation(name, description, method, path, body, false);
    }

    record Operation(String name, String description, HttpMethod method, String path, boolean body, boolean query) {

        String toolName() {
            return "ircs_" + name;
        }

        McpSchema.Tool tool() {
            var properties = new LinkedHashMap<String, Object>();
            var required = new ArrayList<String>();
            for (String parameter : pathParameters()) {
                var schema = parameter.equals("key")
                        ? Map.of("type", "string", "description", "运行设置键")
                        : Map.of("type", "string", "format", "uuid", "description", "已有记录的 UUID");
                properties.put(parameter, schema);
                required.add(parameter);
            }
            if (query) {
                properties.put("query", Map.of("type", "object", "description", "此后台 API 的查询参数"));
            }
            if (body) {
                properties.put("body", Map.of("type", "object", "description", "此后台 API 的请求字段"));
                required.add("body");
            }
            boolean read = HttpMethod.GET.equals(method);
            return McpSchema.Tool.builder()
                    .name(toolName())
                    .description(description + "。对应 " + method + " /api/v1" + path)
                    .inputSchema(McpSchema.JsonSchema.builder()
                            .type("object").properties(properties).required(required).additionalProperties(false).build())
                    .annotations(McpSchema.ToolAnnotations.builder()
                            .readOnlyHint(read)
                            .destructiveHint(!read)
                            .openWorldHint(!read)
                            .build())
                    .build();
        }

        List<String> pathParameters() {
            return List.of("id", "linkId", "key").stream()
                    .filter(name -> path.contains("{" + name + "}"))
                    .toList();
        }
    }
}
