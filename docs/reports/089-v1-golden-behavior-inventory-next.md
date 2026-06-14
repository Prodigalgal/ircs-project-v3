# 089 V1 golden behavior inventory - next steps

来源：`089-v1-golden-behavior-inventory.md` 拆分。

## 下一步

1. `028-16-A` 已完成 canonical rawMetadata、文本标准化、metadata result 深层合并、
   description NER/HanLP prewarm 和 A5 area/language composite alias 第一刀；下一步继续拆
   完整 title handler pipeline、raw dictionary policy、location NLP、genre synonym/suffix/
   protected-term policy 和 live smoke。
2. 将 `028-16-C` 拆细为初始化编排、SystemConfig cache/event、shared outbound HTTP、
   cache TTL/evict、UUID/async logging 等独立子任务，避免一个任务过大。
   C1 cache warmup/TTL/evict first closure 和 countries/languages 全量 preset 已由
   `028-16-C1-S2c` 完成实现；下一刀优先补 resolver/真实资源站 live smoke，并按需独立评估
   `RabbitListenerEndpointRegistry.start()` 自动放行；
   metadata provider HTTP 参数与 live provider smoke、aggregation adaptive min/max
   继续按独立任务推进；content-service 的
   internal scraper base URL 已完成，剩余仅保留 audit/seed/media URL 等启动绑定或独立切片。
3. `028-16-C3` 已完成 content internal caller、shared circuit breaker first slice、
   task-service JSON POST internal caller、identity multipart avatar storage caller、storage strict
   adapter，以及 admin/portal BFF shared streaming transport；下一步转入
   `UTB-02816-P1P2-02` 统一测试，覆盖 BFF dev-safe smoke、caller-specific circuit ENV/K8S
   matrix 和 identity/storage/BFF/shared outbound 组合回归。
4. `028-07` 与 `028-10` 后续分别收敛 R2 cover/avatar explicit V1 bucket smoke 边界、
   notification MAIL credential lease/schema、send history ops 查询/retention、staging-only SMTP
   smoke 与高风险 runner gate。
5. `028-16-F2/B15` 已补 aggregation local closure：结构化 search sync、malformed payload
   no-requeue reject、pipeline failure raw PENDING/unified DIRTY、recalculate 全成功才 SYNCED。
   下一步在 `UTB-02816-P1P2-01` 跑 aggregation/search reconciliation、真实 R2 upload、
   多 context victim merge 和队列/DLQ/ES/R2 residue。
6. 为 Tika、LLM、Mail、Search reconciliation 等 P1 项补 fake-first 或 fixture-first 验证。

