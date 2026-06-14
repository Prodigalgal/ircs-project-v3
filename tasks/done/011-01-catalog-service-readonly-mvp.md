# 011-01 catalog-service 只读字典 MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/migrations/011-catalog-service-readonly-extraction.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 背景

V1 的 standard/raw category、genre、area、language 与 data-source 读取能力仍在单体内。MVP 服务整体接入需要先把稳定、低写入风险的 catalog 查询边界切出，供后续 content、normalization、portal 等服务通过内部 API 读取字典。

## 目标

- 接入 `ircs-catalog-service` 只读 API。
- 保留 V1 兼容路径，同时提供 `/api/v1/catalog/*` 新路径。
- 部署 dev manifest，保持 ClusterIP only。
- 使用临时 catalog 数据做 smoke 并清理。

## 非目标

- 不迁移字典写入 API。
- 不迁移 AI/LLM 归一化清洗逻辑。
- 不做物理数据库拆分。
- 不新增 HTTPRoute。

## 输出

- `services/ircs-catalog-service` JDBC 只读实现。
- `deploy/k8s/dev/catalog-service-dev.yaml`。
- 单元测试覆盖 standard category、raw category、data source。

## 验证

```text
.\gradlew.bat :services:ircs-catalog-service:test
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\catalog-service-dev.yaml
  PASS

.\gradlew.bat build
  PASS

.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-catalog-service' -ImageName ircs-catalog-service -Tag dev -Architecture arm64 -Os linux
  PASS

kubectl rollout status deployment/ircs-catalog-service -n ircs-dev --timeout=240s
  PASS

catalog endpoint smoke
  PASS

temporary rows cleanup
  remaining_temp_rows=0
```

## 验收结果

`ircs-catalog-service` 已在 `ircs-dev` Ready 1/1。当前业务服务接入数从 6/18 提升到 7/18，剩余 11/18。

## 遗留问题

- catalog 写入、管理端校验和审计后置。
- raw 到 standard 的自动归一化和 LLM 清洗后置。
- 后续 content-service 读字典应切换为内部 API，而不是直接跨域读表。

