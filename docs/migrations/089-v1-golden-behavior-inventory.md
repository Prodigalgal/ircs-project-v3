# 089 V1 golden behavior inventory 迁移记录

状态：已拆分为导航页

本文件保留为 089 migration 的稳定入口。原始迁移记录已按记录、MARK 和未完成项拆分。

## 原始摘要


状态：进行中

日期：2026-06-08

## 上游来源

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

## 目标

建立 V1 已验证功能到 V3 微服务的行为级总账，覆盖接口以外的初始化、依赖、清洗、
聚合、出站 HTTP、缓存、搜索补偿、存储安全、scraper 编码、LLM、邮件、任务和系统
治理细节。

2026-06-09 最小账本摘要：已追加 A6/A7 done first slice、C2-S15 no-write live matrix smoke harness、C2-S16 live Rabbit safe dry-run、C3 admin/portal-bff `BFF_PROXY` target validation 和 `028-07-F` explicit V1 bucket smoke preflight/no-write guard 的全局集成记录；本轮再追加 `028-10-E` notification send history schema / ops query done with build evidence、`028-16-C3` notification MAIL credential INTERNAL_SERVICE implemented with targeted evidence but full suite pending、`028-16-A8` title season/subtitle done first slice、`028-07-G` real R2 explicit V1 bucket write smoke done、`028-06-E` Douban provider first slice MARK 6/6、`028-10-F` SMTP staging live preflight blocked、`028-16-C2-S17/C2-S19/C2-S20` real Rabbit publish/deliver/ack 与 content DB fallback read probe done 且无 residue。剩余风险口径收敛为 Rabbit outage TTL、circuit breaker、storage strict adapter、internal callers、SMTP staging schema deploy/send、Douban queue full parity/credential 场景/RT provider 与 cover/avatar R2 full chain。

2026-06-09 文档集成补记：本次只做最小账本同步，不扩展父任务。已把 done / done first slice / implemented with targeted evidence / blocked evidence 四种状态分别落入 089 总账，供后续主线程按真实复验继续移动任务状态。

2026-06-10 C1-S2c 补记：readiness/initialization 剩余 first closure 已完成实现但 live smoke 后置。catalog-service 复用 V1 `countries.json` / `languages.json` 全量 preset（251 countries、187 languages），normalization-worker SystemConfig cache 增加 30s TTL missed-event fallback，resolver/真实资源站 live smoke prep 脚本默认 dry-run 不发 HTTP。


## 拆分入口

- [迁移记录明细](089-v1-golden-behavior-inventory-records.md)
- [MARK 进度明细](089-v1-golden-behavior-inventory-marks.md)
- [未完成项与风险](089-v1-golden-behavior-inventory-remaining.md)
