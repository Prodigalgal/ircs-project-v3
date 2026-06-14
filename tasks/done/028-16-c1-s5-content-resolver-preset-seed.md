# 028-16-C1-S5 content-service resolver preset seed task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `ResolverInitializer`
- V1 `VideoResolverSource`
- V3 `services/ircs-content-service`

## 背景

V1 `ResolverInitializer` 在初始化阶段写入默认视频解析源和线路，保证空库环境下
portal/admin 播放器解析源可用。V3 content-service 已完成 resolver CRUD 和 active query，
但 C1 初始化矩阵仍显示缺默认 resolver seed。该缺口不涉及真实 secret，适合做 C1 的下一刀。

## 目标

- 复刻 V1 `ResolverInitializer` 的默认 resolver preset。
- content-service 启动时幂等补齐默认 resolver，不覆盖用户已存在的同名 resolver。
- 默认启用 seed，并提供 `APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED` 显式关闭开关。
- 用本地 content-service 单元测试固定默认字段、线路 JSON、幂等不覆盖和 disabled 分支。
- 更新 C1/089/docs/tasks 索引并标记 C1-S5 第一刀完成情况。

## 非目标

- 不改 resolver CRUD HTTP 契约。
- 不迁移播放器前端逻辑。
- 不访问外部解析站点，不做 live 播放 smoke。
- 不处理 credential、cache warmup、global readiness orchestrator。

## 影响范围

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/auxiliary/**
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/auxiliary/**
services/ircs-content-service/src/main/resources/application.yaml
deploy/k8s/dev/content-service-dev.yaml
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/5 V1 ResolverInitializer default facts documented in this task: done 2026-06-09
MARK 2/5 content-service startup runner creates missing default resolvers: done 2026-06-09
MARK 3/5 seed is idempotent and does not overwrite existing resolver rows: done 2026-06-09
MARK 4/5 disabled gate skips writes: done 2026-06-09
MARK 5/5 content-service tests and K8S dry-run pass, docs/tasks updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
```

## 当前状态

2026-06-09：已完成第一刀实现并归档。

V1 `ResolverInitializer` 事实：

- `getOrder() = 50`。
- 对每个默认 resolver 先按 `repository.existsByName(info.name)` 判断，已存在则跳过，
  不覆盖用户编辑值。
- 新建 `VideoResolverSource` 时写入 `name`、`remark`、`isActive=true`，
  `lines = [{name: "默认线路", url: info.urlPattern}]`。
- 默认 resolver 为：
  - `金蝉解析` / `https://zy.jinchancaiji.com/?url=` / `支持多线路聚合解析`
  - `789解析` / `https://www.789jiexi.com/?url=` / `速度较快，广告较少`
  - `火花解析` / `https://cj.huohua.live/?url=` / `部分资源需授权`
  - `麒麟解析` / `https://www.qilinzyz.com/?url=` / `综合性解析接口`

V3 实现结果：

- content-service 新增 `DefaultResolverPresetSeeder`，`@Order(50)`，默认启用。
- 新增 `APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED` 开关，dev manifest 显式开启。
- seed 行为按名称补缺，已有同名 resolver 直接跳过，不覆盖 `remark`、`lines`、
  `is_active` 等字段。
- 单条 insert 若遇到 duplicate key 会记录 debug 并继续后续 preset，避免启动补缺因
  并发重复写入中断。
- `JdbcAuxiliaryAdminRepository` 新增 `resolverExistsByName()`，复用现有
  `createResolver()` 写入 JSONB `lines`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
32 tests completed

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

后续未完成项：

- 不包含真实播放器 live smoke。
- 不包含 resolver cache TTL/evict 矩阵。
- 不包含全局 readiness orchestrator 自动放行。
