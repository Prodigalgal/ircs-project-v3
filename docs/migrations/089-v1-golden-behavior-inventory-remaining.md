# 089 V1 golden behavior inventory migration remaining items

来源：`089-v1-golden-behavior-inventory.md` 拆分。

## 未完成项

- `028-16-C` 已拆出 C1/C2/C3，仍需要继续实现。
- `028-16-C2` 已完成默认 seed、运行时 env-first 优先级、config API effective source
  可见化、config change event publisher 第一刀、identity/interaction 本地 cache evict、
  `IdentityConfigService.updateValue()` direct DB 写事件发布、config-service runtime source guard
  和 P0 消费者 YAML-default guard，并已由 `028-16-C2-S4` 完成全量 V1
  `SystemConfigKey` catalog diff；`028-16-C2-S5` 已完成 search-service cover URL config
  消费者第一刀；`028-16-C2-S6` 已完成 interaction-service 收藏/历史 cover URL config
  消费者第一刀；`028-16-C2-S7` 已完成 portal-service 首页/分类/探索/详情 cover URL
  config 消费者第一刀；`028-16-C2-S8` 已完成 storage-service 媒体 URL/头像同步相关
  config 消费者第一刀；`028-16-C2-S9` 已完成 normalization-worker retry/backoff/watchdog
  batch/pending timeout config 消费者第一刀；`028-16-C2-S10` 已完成 aggregation-worker
  scheduler batch size config 消费者第一刀；`028-16-C2-S11` 已完成 notification-worker
  MAIL enable/from/SMTP connect options config 消费者第一刀；`028-16-C2-S12` 已完成
  metadata-worker provider enable/pending TTL config 消费者第一刀；`028-16-C2-S13` 已完成
  ops-service reindex dev-limit config 消费者第一刀；`028-16-C2-S14` 已完成 content-service
  internal scraper base URL 调用期 config 消费者第一刀；content-service 其余 audit/seed/media URL
  配置仍按启动绑定或后续独立切片记录；剩余重点为 full readiness orchestrator、metadata provider
  HTTP 参数、provider live smoke、aggregation adaptive min/max、MAIL rate-limit、live Rabbit
  smoke 和 cache TTL/evict 矩阵。
- `028-16-C3` 已完成 caller inventory、shared `PUBLIC_FETCH` contract、catalog/magnet/scraper/
  metadata TMDB adapter、C3-S4a metadata TMDB credential `INTERNAL_SERVICE` adapter、shared
  circuit breaker first slice、content/task/identity internal caller adoption、storage strict adapter、
  admin/portal `BFF_PROXY` target validation、C3-S9 BFF shared streaming transport adoption 和
  C3-S10 caller-specific circuit ENV matrix implementation。剩余重点转为统一测试批次：
  dev-safe BFF smoke、caller-specific K8S override/open-circuit matrix、
  identity/storage/BFF/shared outbound 组合回归，以及 C3-S6/S7/S8/S9/S10 live/full tests。
- `028-16-C1` 已完成只读矩阵、gate 设计、catalog 字典 owner seed、data source
  preset/default_mapping、remote category sync 第一刀、notification-worker listener gate
  、task-service default collection task seed、content-service resolver preset seed、
  credential-service runtime initializer、credential summary redaction、data-source 写操作后
  远程分类同步触发、full readiness / auto-start gate first closure，以及 C1-S2c 的
  countries/languages 全量 V1 preset 和 cache warmup/TTL/evict first closure；
  仍需实现 ops 跨服务手动分类同步 runner、全服务 worker/listener 自动 start gate 和更多
  scheduler guard；resolver/credential 真实链路和真实资源站公网同步仍需后续 smoke/矩阵验证。
- `028-16-A` 的 A1/A2/A3/A4 已完成第一刀，raw 字典策略、
  完整 title handler pipeline、location NLP、genre 策略和 live smoke 仍需继续拆分。
- `improved replacement` 证据尚未逐项引用。
- P0 聚合 B1、B2、B3、B4、B5、B6、B7、B8、B9、B10、B11、B12、B13、B14 已完成第一刀，B15 已完成 dev K8S runtime live smoke；duplicate unified merge 的 root/victim 收敛、
  victim delete、search DELETE side effect、victim unified relation union、victim scalar merge、
  raw-source category 投票、relation rebuild、scalar pipeline、cover image 选择规则和
  same-batch JGraphT raw/raw cluster、raw/unified context mixed graph、category bucket graph
  guard、metadata scoring/penalty、ES-backed context recall 和 handler order/failure isolation
  runtime 已有本地测试固定。cover R2 promote side effect 已恢复为 storage command /
  due upload scheduling；B15 smoke 已证明 multi-raw scheduler bind、V1 pipeline scalar/category、
  metadata relation union、cover promote gated、ES refresh、队列/DLQ cleanup 和 quota/HTTPRoute
  后置检查。`028-16-F2/B15` 已补本地 dirty/reconciliation closure：结构化 search sync、
  malformed payload reject、pipeline failure raw PENDING/unified DIRTY、recalculate 全成功才
  SYNCED；真实 R2 upload、多 context victim merge production-like smoke、持久 search
  outbox/stats 和 residue 检查仍待统一测试/后续任务。
- `028-16-A2/A4` 已完成第一刀，但完整 V1 title handler pipeline、CharTable 差异决策、
  raw dictionary policy、area/genre 策略和 live smoke 仍未完成。
- `028-16-D1` storage Tika MIME、`028-16-D2` scraper list charset/streaming 第一刀和
  `028-16-D4/F3` scraper detail charset / pagination / Big5 / Shift_JIS fixture implementation
  closure 均已完成；真实 provider 低资源 live charset smoke、完整 `UTB-02816-P1P2-01`
  和后续如发现真实 PDF/Office/text extraction 调用时的 parser package 接回仍待后续。

## 风险

- V1 类数量较多，当前库存是第一轮模块级，不是最终方法级 completion matrix。
- 部分 V3 行为可能已经更安全或更完善，但没有证据前不能标记完成。
- 大依赖接回必须按服务最小化，避免所有镜像膨胀。
- 出站 HTTP 不能一刀切：public fetch、image download、internal service 和 BFF proxy
  的 SSRF/redirect/target 校验边界不同，后续实现必须按 policy 分层，避免误伤 cluster DNS
  或放宽 storage 已有安全边界。
- listener env gate 只能证明“可禁用默认消费”，不能证明“初始化完成后自动启动消费”。
