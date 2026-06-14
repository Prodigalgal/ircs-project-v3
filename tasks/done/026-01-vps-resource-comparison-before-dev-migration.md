# 026-01 VPS 资源对比与 dev 迁移判断

状态：已完成

上游来源：

- 用户请求：比较 `ssh://root@168.138.40.214:22` 与 `ssh://root@213.35.105.106:22`，
  若资源更充足则考虑迁移开发资源。

关联报告：

- `docs/reports/026-vps-resource-comparison-before-dev-migration.md`

## 目标

- 只读检查可访问 VPS 的 CPU、memory、disk、architecture 和 runtime。
- 对比当前 K8S 节点资源。
- 判断是否应该迁移 `ircs-dev` 开发资源。

## 非目标

- 不做任何 K8S 迁移。
- 不初始化新集群。
- 不搬迁 PostgreSQL/RabbitMQ/Elasticsearch/Valkey 数据。
- 不改 DNS、HTTPRoute、Gateway 或 Harbor tag。

## 验证结果

SSH 认证：

```text
root@168.138.40.214  -> 168.138.40.214.key 可登录
root@213.35.105.106  -> 213.35.105.106.key 可登录
root@168.138.40.52   -> 168.138.40.52.key 可登录
root@217.142.229.2   -> 217.142.229.2.key 可登录
```

当前 K8S 节点：

```text
213.35.105.106: arm64, 4 vCPU, ~24Gi memory, 193G root disk, 114G available
cpu requests: 3767m/4
```

候选机器：

```text
168.138.40.214: arm64, 4 vCPU, ~24Gi memory, 102G root disk, 37G available
cpu requests: 3180m/4
```

## 结论

不迁移。`.214` CPU request 压力略低，但 CPU/内存规格与 `.106` 相同，磁盘空间明显
更紧，且已经承载 Harbor/Gitea/AI gateway 等负载；把 `ircs-dev` 整体迁过去收益不足、
风险更高。
