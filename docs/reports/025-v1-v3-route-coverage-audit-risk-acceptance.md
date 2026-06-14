# 025 V1/V3 route coverage audit - risk and acceptance

来源：`025-v1-v3-route-coverage-audit.md` 拆分。

## 风险

- Controller route 覆盖不等于行为完全等价；每个切片仍需读 V1 Service、Repository、DTO
  和前端调用方式。
- 高风险 ops maintenance、DLQ retry/discard、批量删除、ES reset 不能按路由缺口机械迁移。
- content/datasource/metadata 缺口包含大量写接口，必须先明确服务 ownership 和写入路径。
- 当前 `ircs-dev` 仍保留 2 个缓冲 Pod 和 2 个缓冲 Service；新功能优先复用已有 Deployment。

## 验收口径

- 本报告只证明“已发现并分类当前 HTTP 路由缺口”。
- 不能据此宣布完整迁移完成。
- 每个关闭缺口的子任务完成后，需要回写本报告或后续迁移记录，更新剩余缺口。
