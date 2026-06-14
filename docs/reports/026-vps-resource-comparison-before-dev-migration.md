# 026 VPS 资源对比与 dev 迁移判断

状态：已完成

更新时间：2026-06-04

## 背景

用户提出对比以下两台机器资源，并在新机器资源更充足时考虑迁移开发资源：

```text
ssh://root@168.138.40.214:22
ssh://root@213.35.105.106:22
```

当前已知 `ircs-dev` 运行在 Kubernetes context `kubernetes-admin@kubernetes`，API server
为 `https://213.35.105.106:6443`。

## SSH 认证结果

使用用户提供的 key：

```text
D:\WorkSpace\Key\168.138.40.52.key
D:\WorkSpace\Key\217.142.229.2.key
```

已先修复 Windows OpenSSH 私钥 ACL 过宽问题，移除公共读权限并保留当前用户只读。
随后用户补充了 `.214` 的实际 key，当前可用 key 为：

```text
D:\WorkSpace\Key\213.35.105.106.key
D:\WorkSpace\Key\168.138.40.214.key
```

认证结果：

```text
root@213.35.105.106  -> 213.35.105.106.key 可登录
root@168.138.40.214  -> 168.138.40.214.key 可登录
root@168.138.40.52   -> 168.138.40.52.key 可登录，但不是本次目标
root@217.142.229.2   -> 217.142.229.2.key 可登录，但不是本次目标
```

## 当前 K8S 节点资源：213.35.105.106

来源：

```text
kubectl describe node instance-20251229-0833
```

结果：

```text
node:         instance-20251229-0833
architecture: arm64
cpu:          4 allocatable
memory:       24448108Ki allocatable (~23.3Gi)
disk:         ~186Gi allocatable ephemeral storage
runtime:      containerd://2.2.1
k8s:          v1.35.0
running pods: 64
root disk:    193G total, 114G available, 42% used
host memory:  23Gi total, 10Gi available
load:         0.37, 0.48, 0.47
```

当前调度压力：

```text
cpu requests:    3767m / 4    (94%)
cpu limits:      14700m / 4   (367%, overcommitted)
memory requests: 11722Mi      (49%)
memory limits:   33266Mi      (139%, overcommitted)
node pressure:   MemoryPressure=False, DiskPressure=False, PIDPressure=False
```

`ircs-dev` namespace 当前配额：

```text
pods:            23 / 24
services:        19 / 20
requests.cpu:    795m / 4
requests.memory: 3968Mi / 8Gi
limits.cpu:      6700m / 8
limits.memory:   12672Mi / 16Gi
```

## 候选迁移机器资源：168.138.40.214

来源：

```text
ssh root@168.138.40.214
kubectl describe node instance-20250708-1530
```

结果：

```text
node:         instance-20250708-1530
architecture: arm64
cpu:          4 allocatable
memory:       24448108Ki allocatable (~23.3Gi)
disk:         ~98Gi allocatable ephemeral storage
runtime:      containerd://2.1.4
k8s:          v1.33.4
running pods: 49
root disk:    102G total, 37G available, 65% used
host memory:  23Gi total, 11Gi available
load:         0.54, 0.82, 0.93
```

当前调度压力：

```text
cpu requests:    3180m / 4    (79%)
cpu limits:      4180m / 4    (104%, slightly overcommitted)
memory requests: 4732Mi       (19%)
memory limits:   6887568Ki    (28%)
node pressure:   MemoryPressure=False, DiskPressure=False, PIDPressure=False
```

主要已有负载：

```text
ai-gateway
argocd
cert-manager
daily-report-db
envoy-gateway-system
gitea
harbor
hysteria
kube-system
monitoring
```

## 额外可登录但非本次目标的 VPS

### root@168.138.40.52

```text
hostname:     instance-20240925-1824
architecture: x86_64
cpu:          2
memory:       954Mi total, 367Mi available, 2.0Gi swap
disk:         45G root, 29G available
runtime:      docker, containerd
```

### root@217.142.229.2

```text
hostname:     instance-20250707-2155
architecture: x86_64
cpu:          2
memory:       956Mi total, 438Mi available, 2.0Gi swap
disk:         45G root, 18G available
runtime:      docker, containerd
```

## 判断

不建议把当前 `ircs-dev` 开发资源迁移到 `168.138.40.214`：

- 两台目标机器 CPU 和内存规格相同，都是 `arm64`、4 vCPU、约 24GiB memory。
- `.214` 的 CPU request 压力较低，`3180m/4` 对比 `.106` 的 `3767m/4`。
- 但 `.214` 磁盘明显更紧：root disk `102G/37G available`，而 `.106` 为
  `193G/114G available`。
- `.214` 已承载 Harbor、Gitea、AI gateway、monitoring 等关键工作负载，把 IRCS dev
  的 PostgreSQL/RabbitMQ/Elasticsearch/Valkey 和 18 个服务迁过去，会把磁盘和集群职责
  进一步混在一起。
- `.106` 当前虽然 CPU request 紧，但无 node pressure，且磁盘空间更适合承载
  Elasticsearch/PostgreSQL/RabbitMQ 这类 dev 数据面。

## 建议

- 暂不迁移当前开发资源。
- 若后续要扩容，更建议给 `.106` 加新 worker node，或在 `.106` 上继续压缩 request/limit，
  而不是把 dev 环境整体迁到 `.214`。
- 当前更现实的优化方向仍是：
  - 继续降低 `monitoring`、非关键系统和 dev Java 服务 request/limit。
  - 保持 worker-only 服务不创建 ClusterIP Service。
  - 对 post-MVP 功能优先复用既有 Deployment，不新增 Pod/Service。
