# 090 Douban provider first slice 迁移记录

状态：已完成

关联任务：

- `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`

## 迁移边界

本迁移只覆盖 `028-06-E` 的 Douban provider first slice：

- `metadata-worker` Douban provider 注册与最小解析。
- credential-service `DOUBAN` provider catalog/lease 支持。
- `metadata-worker` dev gate 仍默认关闭。
- Douban live smoke 只读、单请求、无业务写入。

## 配置优先级

保持既有运行时配置优先级：

1. ENV / K8S Secret / ConfigMap / configtree / start args / Spring Environment。
2. DB 配置值。
3. default seed。

`APP_METADATA_DOUBAN_ENABLED` 和 `APP_METADATA_DOUBAN_*` 显式运行时注入不得被 DB/default seed 覆盖。

## 验证计划

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

## 迁移结果

- `metadata-worker` 已注册 `ProviderType.DOUBAN` provider implementation。
- `credential-service` 已支持 `DOUBAN` catalog/template/create/fingerprint/lease。
- `APP_METADATA_DOUBAN_ENABLED=false` 仍为 dev K8S 常驻状态，避免默认公网 queue 调用。
- focused tests 通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

- 真实外网最小 smoke 通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

结果摘要：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
externalRequestCount=1
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
Douban HTTP 200
parsed firstId=1291843, firstTitle=黑客帝国, firstSubTitle=The Matrix, firstYear=1999
```

## 后续边界

- 本迁移不改变 Rabbit、DB、K8S env 或真实业务数据。
- 后续若要验证完整 Douban queue parity，必须另拆低资源 live smoke，并显式声明启用/恢复
  `APP_METADATA_DOUBAN_ENABLED`、fixture 清理和 queue/DLQ 检查。
