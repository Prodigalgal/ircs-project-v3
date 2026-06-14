# 125 ircs-prod clean cutover

日期：2026-06-14

## 结论

`ircs-prod` 已作为 V3 唯一开发、测试和生产候选 namespace 启用。旧 `ircs-dev`
已删除。旧 `ircs-system` 不再承接 `huawai` / `ircs` 业务入口，只保留为短期回滚参考和退役对象。

旧数据迁移已取消，`ircs-prod` 使用 V3 migrator 初始化后的干净数据库起步。

## 当前状态

- `ircs-prod-core`：`Synced/Healthy`。
- `ircs-prod-edge`：`Synced/Healthy`。
- `ircs-prod-migrator`：保留为手动应用，避免 core 同步重复运行迁移 Job。
- `ircs-app`：旧 app automated self-heal 已关闭；删除旧业务 route 后显示
  `OutOfSync/Missing` 是预期状态。
- `ircs-prod`：24/24 Deployment Ready，3/3 StatefulSet Ready。

## 数据处理

曾启动的旧数据导入已中止。随后用迁移前的 `ircs-prod` 备份恢复干净库。

恢复后抽样：

```text
unified_videos=0
raw_videos=0
episodes=0
system_configs=133
databasechangelog=138
```

## 入口切流

- 删除旧 `ircs-system/huawai-route`。
- 删除旧 `ircs-system/ircs-route`。
- 保留旧 `ircs-system/kibana-route`。
- 新增并由 ArgoCD 接管：
  - `ircs-prod/huawai-route -> ircs-frontend-gateway:80`
  - `ircs-prod/ircs-route -> ircs-frontend-gateway:8080`

两个新 route 均为 `Accepted=True`、`ResolvedRefs=True`。

## 验证

集群内真实验证通过：

- `http://ircs-frontend-gateway:80/gateway-health` -> `204`
- `http://ircs-frontend-gateway:8080/gateway-health` -> `204`
- `http://ircs-portal-frontend:3000/` -> `200`
- `http://ircs-admin-frontend:80/` -> `200`
- portal/admin BFF、identity、task、search、ops readiness 均 -> `200`

公网真实验证通过：

- `https://huawai.sophia.fr.eu.org/gateway-health` -> `204`
- `https://ircs.sophia.fr.eu.org/gateway-health` -> `204`
- `https://huawai.sophia.fr.eu.org/` -> portal HTML `200`
- `https://ircs.sophia.fr.eu.org/` -> admin HTML `200`

## 剩余风险

- 旧 `ircs-system` 仍存在，需单独确认退役窗口。
- 旧 `ircs-app` 当前保留为回滚参考，未删除。
- 还需要生产候选安全复核、回滚演练说明和 go/no-go 报告。
