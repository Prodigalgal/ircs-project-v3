# 队列 Prometheus 指标快照

`QueueGaugeSnapshot` 在后台刷新队列指标。Prometheus gauge 回调只读取已发布的不可变映射，不连接 Valkey、RabbitMQ 管理 API 或 AMQP。既有 `ircs.valkey.runtime_work.*` 和 `ircs.rabbit.queue.*` 名称、标签及队列覆盖范围保持不变。

每轮刷新对每种 runtime task 调用一次 `RuntimeWorkQueue.counts`，三个 gauge 复用该返回值。RabbitMQ 每轮只请求一次完整管理快照；管理 API 失败时，后台对每个队列至多读取一次 AMQP 属性。完整管理列表中不存在的队列视为确实不存在，计数为零；AMQP 回退失败或返回空属性时视为未知。

两个来源分别保留完整的最后成功映射与成功时间，再通过一次引用替换发布。单个来源刷新失败不覆盖它的旧值，也不阻止另一来源推进；首次成功之前输出 `NaN`。失败后保留值不表示数据仍新鲜，告警应同时检查快照年龄和刷新失败计数。

新增指标：

| Micrometer 名称 | 标签 | 含义 |
| --- | --- | --- |
| `ircs.ops.queue.snapshot.age` | `source=runtime/rabbit` | 最后成功快照年龄，单位秒，首次成功前为 `NaN` |
| `ircs.ops.queue.snapshot.refresh.failures` | `source=runtime/rabbit` | 刷新失败累计次数 |
| `ircs.ops.queue.snapshot.refresh.duration` | `source=runtime/rabbit` | 每轮来源刷新耗时，包含失败尝试 |
| `ircs.ops.queue.snapshot.refresh.running` | 无 | 当前有刷新任务时为 1，否则为 0 |

配置使用 Spring 启动属性：

| 属性 | 默认值 | 含义 |
| --- | --- | --- |
| `app.ops.queue-gauges.initial-delay-ms` | `0` | 首次后台刷新触发延迟 |
| `app.ops.queue-gauges.refresh-delay-ms` | `5000` | 后台刷新触发间隔 |

触发器使用既有 `ScheduledTriggers` 虚拟线程 helper；上一轮仍运行时跳过重叠刷新。慢依赖不会延长 scrape，但会反映到刷新耗时、快照年龄与失败计数；组件关闭时取消刷新 executor。

回归测试 `QueueGaugeSnapshotTest` 使用真实 `PrometheusMeterRegistry.scrape()` 验证抓取不产生依赖 I/O，并覆盖每任务读取次数、异步单次执行、失败保留、未知值、年龄、AMQP 回退及并发不可变发布。测试仅由 CI/Actions 执行。
