# IRCS 管理 MCP 与权威资料回填

3.13.0 在 3.x 的后台网关上提供 MCP 管理入口。使用 Streamable HTTP，客户端请求可由任意一个网关副本接收。工具与现有后台 API 一一对应，写入仍由各业务服务执行。权威资料补充继续由 metadata worker 处理。

## 管理 MCP

- 生产入口为 `https://admin.mnnu.eu.org/api/v1/mcp`。使用后台创建的 API Token，通过 `Authorization: Bearer <token>` 提交。已有管理员 JWT 也可用于调用。客户端应协商 MCP 版本，并通过 `tools/list` 读取当前工具和参数。
- 工具覆盖源站、采集计划、求片、影片、线路、配置、会员、留言、凭据配置、磁链、封面、死信和运营。查询参数放在 `query` 对象中，后台写入字段放在 `body` 对象中。单条记录通过 UUID 或设置键定位。
- 每次请求校验管理员身份。执行前核对对应后台操作的权限。调用通过原网关记录身份、路径和结果。凭据工具返回供应商、启用状态和用量等管理字段，从工具响应去除凭据载荷。
- `APP_GATEWAY_MCP_ENABLED=false` 可以关闭入口。带 `Origin` 的请求须匹配 `APP_GATEWAY_MCP_ALLOWED_ORIGIN`，生产默认是后台域名。MCP 使用 JSON 请求，网关对请求和响应设置大小上限。

## 权威资料

- 对作品身份可信且要补核心信息的来源，优先尝试用已有 TMDB ID 获取详情。补充前验证返回身份及正确季集。未命中和临时请求失败分别进入现有状态与重试路径。
- 保留已完成采集版本的幂等性。多来源任务的最后一个回执结束后，之前已取得的新资料也会触发原聚合条目重算。关联记录的 ID 继续沿用。
- 通过 MCP 的 `ircs_raw_video_reenrich` 可以重新处理单条来源视频。生产切换仍沿用既有定时采集设置。

## 验收

GitHub Actions 验证网关和 metadata worker，使用 Native 镜像检查 MCP 握手、认证、令牌撤销及工具调用。权威资料使用既有 provider 和 PostgreSQL 回归夹具验证可信 ID、季集、回填和重试。

[MCP Streamable HTTP 规范](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
