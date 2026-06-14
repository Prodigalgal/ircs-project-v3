# 028-06 real provider live parity

状态：完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

归档全文：

- `docs/reports/archive/2026-06-13-doc-compaction/028-06-real-provider-live-parity-full-log.md`

## 范围

本任务补齐 metadata provider 真实链路的受控 live parity：

- TMDB provider credential gate、direct diagnostic 和 queue success smoke。
- Douban provider implementation、credential catalog、只读外网 smoke 和 full queue parity。
- Rotten Tomatoes provider first slice、credential catalog、只读外网 smoke 和 full queue parity。
- magnet YTS_BZ runner contract 的最小真实 provider 边界与失败分类。

## 完成结论

- provider enable gate 已保留，`DEV_PROVIDER_GATE_DISABLED` 仍作为 dev 安全阀。
- TMDB、Douban、RT 都已有成功或明确 skip/failure 分类证据。
- full queue parity 覆盖 provider result、raw metadata、search sync、queue/DLQ drain 和 cleanup。
- RT live rollout 已发布包含 provider 代码的 ARM64 metadata-worker 镜像，完成单条 queue smoke 后恢复 gate/env。
- 任务范围内无剩余阻塞；后续扩大公网 provider 样本、RT credential/cookie 策略或 magnet 公网 runner 时另拆任务。

## 关键证据

- TMDB live queue：`MARK 1/6` 到 `MARK 6/6`，raw metadata 更新为 `SUCCESS`，ES 文档刷新，provider/result/search queues 与 DLQ 清零。
- Douban live queue：`MARK 1/6` 到 `MARK 6/6`，外网请求数 `1`，gate 恢复，DB smoke residue `0`。
- RT readonly smoke：Rotten Tomatoes HTTP 200，`The Matrix -> matrix`。
- RT full queue：metadata-worker digest `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，`MARK 6/6`，queue/DLQ messages `0`，DB smoke residue `0`。

## 当前边界

- 不默认开放真实公网 provider 大样本抓取。
- 不默认启用 RT/Douban/TMDB provider gate；需要测试时显式开启并恢复。
- 后续验证遵循 030 主线规则：只允许连接 `ircs-dev` K8S 和真实外部链路，不再使用本地 smoke/fake 数据作为完成证据。
