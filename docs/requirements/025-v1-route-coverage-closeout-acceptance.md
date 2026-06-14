# 025 V1 route coverage closeout - acceptance

来源：`025-v1-route-coverage-closeout.md` 拆分。

## 验收标准

- `docs/reports/025-v1-v3-route-coverage-audit.md` 记录当前 V1/V3 路由覆盖差距。
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md` 承接本需求。
- 每个缺口子任务必须归属 `025` 父任务。
- 第一批 search 子任务已完成本地测试、文档回写、ARM64 rollout 和 ClusterIP smoke。
- 第二批 interaction 门户写接口已完成本地测试、文档回写、ARM64 rollout 和
  ClusterIP smoke。
- 第三批 portal explore/detail 已完成本地测试、文档回写、ARM64 rollout 和
  ClusterIP smoke。
- 第四批 task lifecycle/log API 已完成本地测试、文档回写、ARM64 rollout 和
  ClusterIP smoke。
- 第五批 content raw/unified admin API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `21/21` 接口复现标记。
- 第六批 storage cover image admin API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `7/7` 接口复现标记。
- 第七批 ops observability read API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `4/4` 接口复现标记。
- 第八批 interaction admin message API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `4/4` 接口复现标记。
- 第九批 task runner/scheduler runtime contract 已完成本地测试、文档回写、ARM64
  rollout、ClusterIP smoke 和 `1/1` 运行时契约复现标记。
- 第十批 identity admin auth API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `3/3` 接口复现标记。
- 第十一批 identity member admin API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `6/6` 接口复现标记。
- 第十二批 content people dictionary admin API 已完成本地测试、文档回写、ARM64
  rollout、ClusterIP smoke 和 `14/14` 接口复现标记。
- 第十三批 catalog admin read contract 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `21/21` 接口复现标记。
- 第十四批 config common timezones API 已完成本地测试、文档回写、ARM64 rollout、
  ClusterIP smoke 和 `1/1` 接口复现标记。
- 第十五批 content auxiliary admin API 已完成本地测试、文档回写、ARM64 rollout、
  admin-bff smoke 和 `14/14` 接口复现标记。
- 第十六批 catalog admin write contract 已完成本地测试、文档回写、ARM64 rollout、
  admin-bff smoke 和 `25/25` 接口复现标记。
- 第十七批 config/credential admin write contract 已完成本地测试、文档回写、ARM64
  rollout、admin-bff smoke 和 `9/9` 接口复现标记。
- 第十八批 magnet admin provider/search contract 已完成本地测试、文档回写、ARM64
  rollout、admin-bff smoke 和 `3/3` 接口复现标记。
- 第十九批 identity portal avatar upload contract 已完成本地测试、文档回写、ARM64
  rollout、portal-bff smoke 和 `1/1` 接口复现标记。
- 第二十批 ops guarded operations/debug/stream contract 已完成本地测试、文档回写、
  ARM64 rollout、admin-bff smoke 和 `19/19` 接口复现标记。
- 若进入 K8S rollout，仍需使用 ARM64 镜像、ClusterIP smoke、无 HTTPRoute 检查和资源配额检查。
