# 025 V1 路由覆盖闭环需求

状态：进行中

更新时间：2026-06-08

## 背景

`011` 父任务已完成 18 个目标业务服务的 MVP rollout，但这只证明 V3 服务骨架和低风险
切片已经在 `ircs-dev` 跑通，并不等于 V1 单体能力已经完整迁移。当前继续推进目标是：
以 V1 `ircs-backend` Controller 路由为基准，逐步关闭 V3 的 HTTP/API、队列和后台任务
覆盖缺口，直到 V1 到 V3 的完整微服务拆分可以被逐项验证。

## 目标

- 建立 V1/V3 Controller 路由覆盖审计口径。
- 将缺口按目标服务 ownership 拆成可实现、可验证、可回滚的子任务。
- 每个子任务开始前明确边界、风险、非目标和测试口径。
- 优先关闭前端可见、低资源、低破坏性的缺口。
- 保持 `ircs-dev` 无公共 HTTPRoute，继续使用低资源 profile。

## 非目标

- 不把 18/18 MVP rollout 视为完整迁移完成。
- 不一次性复制 V1 所有破坏性 admin/maintenance 写命令。
- 不在本需求内做生产流量切换。
- 不在本需求内做物理数据库拆分。
- 不在没有 task spec 的情况下直接补接口。

## 当前基线

基于 2026-06-04 的只读路由抽取：

```text
V1 backend Controller routes: 203
V3 service Controller routes: 54
Direct missing routes after path normalization: 160
```

最大缺口集中在：

```text
content:     49
system/ops: 36
datasource: 20
metadata:   14
interaction:12
portal:      9
task:        8
storage:     7
magnet:      3
search:      0
scraper:     2
```

详细审计见：

```text
docs/reports/025-v1-v3-route-coverage-audit.md
```

当前已完成 `025-01` 到 `025-20` 后的推进口径：

```text
V1 backend Controller routes: 203
V3 service Controller routes: 226
Direct missing routes: 0
```

说明：`025-18` 到 `025-20` 关闭了 23 条源码复核 candidate route，其中 ops 类 route
按 guarded dev contract 复现 HTTP 返回、状态流和可观察性；真实生产级 maintenance
runner、外部 provider search、队列消费者和物理数据拆分继续作为行为深度迁移推进。
`025` closeout 时刻 V3 route 数为 224；当前 226 包含 2026-06-08 `028-12`
新增的 2 条 worker/job audit ops 查询端点，不影响 direct missing=0。

