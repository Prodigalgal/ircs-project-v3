# Google Decisions 文本 NSFW 接入

本能力仅评估现有内容安全链路的标题、别名、字幕、简介、备注与来源文字；Google 独立 max-text-length 默认 12000 个 UTF-16 单位，原 Python max-text-length 仍默认 512。不下载图片、视频或音频，不扩展视觉识别。默认 provider 为 `PYTHON`，原内部 Python classifier endpoint、输入输出与 disabled 行为不变。显式选择 `GOOGLE_DECISIONS` 才调用新适配器；不改变 normalization 的 OpenAI 文本生成契约。

## 官方契约与验证边界

Google 官方 [模型文档](https://aistudio.google.com/docs/models/gemini-decision-1) 与 [Decisions API](https://aistudio.google.com/docs/decisions-api) 定义：模型 `gemini-decision-1`，`POST https://generativelanguage.googleapis.com/v1beta/decisions`，`x-goog-api-key` 请求头，typed `noul` 判断输出概率与 confidence，零文本输出 token。官方模型支持文本、图像、视频、音频与 PDF；本次接入仅现有元数据文本。该专用模型不支持 chat/completions、结构化文本生成、离线 Batch API、服务端上下文缓存或 thinking 参数。本实现只发送 model/input/questions，无 temperature、response_format 或 reasoning_effort。

2026-11-13 只读检查了 9 个唯一 Google key：全部 models.list HTTP 200，并可见该模型；支持方法只有 createDecision。这证明凭据能列模型，不等价于已验证真实判断、账单 tier 或余额。未提交任何生成/判断请求，未复制凭据。project 归属尚无证据，不按 key 数叠加额度。

## 分类与故障语义

每条输入一个 noul 问题；共享输入使用 JSON 对象 item_N 标签，instructions 明文写出要评估的 item_N 边界，不依赖 question key 被模型看见。每问题包含完整的正向 NSFW 判据。判断露骨色情、图像化性行为文字或以性唤起为目的的内容；正常健康、性教育、医学、普通恋爱与非色情报道不因单词命中自动归为色情。输入中的指令视为证据，不执行。不同文档混在共享输入可能受无关上下文影响，不承诺与每条独立输入完全等效；需以项目标注集验收混批准确率。请求形状的注入隔离测试不等于已实证模型能抵抗所有提示注入。

noul 是 P(yes)，confidence 必须符合 max(noul, 1-noul)，与 choice 类型的胜出选项语义不同。通过响应数值校验且 confidence 达到默认 0.9 才使用真实 NSFW 概率，沿用已有 adult/suspect 阈值与规则合并；模型结果不会降级已有成人规则证据。Google 输入超过专属长度时仅评估前缀，返回 ADULT 仍可作为正面证据；其他结果统一 unavailable / INPUT_TRUNCATED 待复核，不能把前缀 SAFE 当作完整内容安全。有效结果 raw 记录 inputModality=text、inputTruncated、evaluatedCharacters（UTF-16 单位）；共享空白字段分隔符也计入长度。空条目单独待复核，不拖延同批其他条目。阈值需用有标注的本项目样本校准，当前值不是实测精度承诺。

失败时模型 available=false，label=UNAVAILABLE，raw 包含 provider、reason、retryable、retryAfterSeconds。除已有规则 ADULT 外，返回 SUSPECT、confidence=0、PENDING_REVIEW 信号，含义是待判断/复核，不能解释为安全或模型阳性。LOW_CONFIDENCE、EMPTY_INPUT、REQUEST_TOO_LARGE、REQUEST_ENCODING_ERROR、INPUT_REJECTED、INPUT_TRUNCATED 不自动重试，供人工复核。配额/冷却、网络、凭据与供应商暂态失败可重试；不能降级为 SFW 或通过换 key 绕过 429。

## 持久微批、缓存与恢复

Google 路径的 assessment HTTP 请求只进行入队/缓存读取。不同 HTTP 调用以完整规范文本、固定模型、text-nsfw-v1 policy、instructions、输入长度及置信/成人阈值的 SHA-256 key 去重；UUID 不参与 key，结果命中后映射回当前调用者 UUID。修改 criteria 时须同步升级 policy 版本。首次返回 QUEUE_PENDING/retryable=true，轮询延迟至少 2 秒，不向调用者报告 SAFE。

所有副本共享 `ircs:decision-queue:{google-decisions}:*`。队列满默认 20 条即具备 dispatch 条件；未满默认最老任务等待 500ms 即具备条件（50ms 调度，阈值提前一个 tick；系统调度和额度/并发等待可增加实际排队时间）。每副本最多 2 个虚拟线程批次发外部请求。不是离线 Batch API；每批是一次 Decisions create 的多个独立 questions。官方每次最多 1000 问题，本实现默认 20，再按完整 JSON 保守预算二分缩小。

队列默认容量 10000（含 inflight），job TTL 24h；先检查 cache/既有 job，再检查容量，满队列不阻止去重和结果命中。超容量返回 QUEUE_FULL 可重试。队列 FIFO 以 Redis TIME 的入队分数为主；相同毫秒按 hash 排序。为避免长文本在剩余 TPM 不足时阻挡小任务，worker 预取最多 4 个批次，尝试缩小批次及后续候选；TOKEN_LIMITED 只延期相关候选，其他候选仍可消化。已到期旧项在下一个可用 token 窗优先重新竞争，单条超过最大请求预算直接待复核。

quota 先原子预留，随后 Lua claim(owner+60s lease，处理中的 job 至少保留 120s 恢复宽限)，因此 quota 不足的任务保持 queued、不占 inflight、不取 Google key、不产生供应商失败。发送前 Lua 再检查全池 cooldown、预留时效和 Pacific 日界，并将 receipt 从 reserved 置 sent、把分钟窗锚定实际 dispatch 时刻。所有 claim/markSent/完成写入均有 owner fence；完成/延期均核对 Redis TIME 下的 inflight 租约尚未到期，过期 owner 即使尚未被 reap 也不能写结果或延后全池时间。未发送 owner 失联后可恢复；sent 失联则缓存 UNKNOWN_OUTCOME 60s，保留未知 quota，不盲目重放。外部 HTTP 无供应商幂等键，不能承诺 exactly-once。

成功和不可自动重试的人工复核结果缓存 24h；暂态供应商失败仅缓存其 retryAfter 时间，供 aggregation 按真实失败预算重试。额度/冷却返回 QUOTA_WAIT 并原地延期，不计供应商失败预算；新到任务也读取共享最早 dispatch 时间。retryAfter 是当前剩余 delay 与按每项 1000 输入 token、总 TPM 推导的粗略排队 ETA 的较大值（不是完成时刻保证），随负载可能变化。等待状态由 aggregation 的独立延期路径管理，截止策略见其契约。

Google 400 safety/prohibited_content 等拒绝可能令整批无 answers。本实现将整批 INPUT_REJECTED / retryable=false 明确送人工复核并移出队列，不把其视作 SAFE，也不为绕过过滤器自动重试/拆批。这样一条被拒绝输入不会永久阻塞微批队列，代价是同批正常条目也待复核。

可观测指标：`ircs.decision.queue.queued`、`ircs.decision.queue.inflight`、`ircs.decision.queue.oldest.age.seconds`（每秒刷新共享快照）、`ircs.decision.batch.size`、`ircs.decision.batches.dispatched`、`ircs.decision.items{outcome}`、`ircs.decision.quota.delay.seconds`、`ircs.decision.queue.errors{reason}`。吞吐/年龄应结合实际限额观察，不能把“已入队”当“已完成判断”；预算估算较保守会降低实际吞吐，需要以实际 usage 和标注集进一步调优。

## 配额

根据 [Google 限额说明](https://aistudio.google.com/docs/rate-limits#gemini-decision)，Free 标准限额为 RPM 200、输入 TPM 50,000、RPD 10,000，Google 按 project 聚合；RPD 在 Pacific 午夜重置。本实现额外按整个 IRCS 系统、所有 key/项目/副本合计一套预算：一个 Lua 事务同时检查滚动 60 秒 RPM、滚动输入 TPM、America/Los_Angeles 当日日次数和共享冷却。业务上限固定，不随 key 增加。RPM/TPM 等待时间按 Redis 中最早释放足够预算的滚动条目计算，日额度按 Pacific 新日，非一律固定等待 60 秒。Pacific 日界使用地区时区，覆盖 23/25 小时的 DST 日期。

预留估算为完整 UTF-8 JSON 字节数的两倍 + 4096 + 每问题 256；包含 shared input、instructions、criteria 与序列化开销。它是保守估算，**不是精确 tokenizer，也不是 Google 对计数上界的保证**。模型未公布 countTokens 支持，本实现不假装精确计数。响应必须报告 usage.total_input_tokens、total_output_tokens=0 且 total_tokens=input；一次幂等 reconcile 用 actual 替换预留，并不追加双扣。若实际超估算，保留实际计数并全局停发至少一分钟，结果待重试。单请求预算默认 24000、默认最多 20 条，超限先分批；单条仍超限待人工复核，不默默截断标准/问题。

只在确定请求尚未发送时取消并退还预留。网络超时、取消途中、429、无 usage 或未知结果均保留预留直到分钟窗结束，日计数保留，避免未知已消费被退款；429 取 Retry-After / RetryInfo / 日额度提示设置全局冷却。预算存储不可用时停发，不退回进程内计数。

所有副本必须连接同一持久化 Valkey DB，并为预算账本使用防淘汰策略（noeviction 或独立受保护实例），保障日计数不因内存压力丢失；不得清理 `ircs:decision-quota:{google-decisions}:*` 或将其加入临时资源回收。人为清库/丢失日账本会失去当日预算历史，恢复前需等待 Pacific 新日或根据官方用量人工重建；本实现不能从 API key 读取项目实时配额。系统外共享项目的调用也不在本地账本内，因此 Google 实际可用额度可能更低，以 429 冷却收敛。

## 配置与凭据导入

内容安全服务运行时配置（不在 config-service 提供假热更新入口）：

| 环境变量 | 默认 / 用途 |
|---|---|
| APP_CONTENT_SAFETY_ADULT_MODEL_ENABLED | false；已有总开关 |
| APP_CONTENT_SAFETY_ADULT_MODEL_PROVIDER | PYTHON；启用本能力设置 GOOGLE_DECISIONS |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_CREDENTIAL_SERVICE_BASE_URL | http://ircs-credential-service:8080 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_CREDENTIAL_SERVICE_TOKEN | 空；专用内部凭据访问令牌，使用 Secret |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_CREDENTIAL_SERVICE_ID | ircs-content-safety-service |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_CREDENTIAL_SERVICE_SCOPES | credential:lease |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_REQUEST_TIMEOUT | PT10S（最大 PT20S）；单次网络上限 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_BATCH_SIZE | 20（1–1000），仍受 token 预算限制 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_MAX_BATCH_WAIT | PT0.5S（PT0.05S–PT2S），低流量 flush 上限目标 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_QUEUE_CAPACITY | 10000（1–10000），包括 inflight |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_QUEUE_TTL | PT24H（PT1M–PT24H），未完成 job 的有界保留 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_RESULT_TTL | PT24H（PT1M–PT24H），成功/人工复核结果 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_CONCURRENT_BATCHES | 2（1–8），每副本并发批次 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_MAX_REQUEST_BUDGET | 24000（4096–50000） |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_MAX_TEXT_LENGTH | 12000（1–100000 UTF-16 单位）；并受完整 JSON 的请求 token 预算限制 |
| APP_CONTENT_SAFETY_GOOGLE_DECISION_MIN_CONFIDENCE | 0.9（0.5–1） |
| SPRING_DATA_REDIS_HOST / PORT / PASSWORD / DATABASE | Spring Redis 标准属性；新增 Redis starter，部署须配置同一受控 Valkey DB |

保留现有内部服务访问控制；credential-service 必须允许此 service-id/token 的 credential:lease。模型与 Google 官方 endpoint 固定，无自定义 endpoint 或直接 Google API key env。Google key 仅通过既有受保护 `POST /api/v1/credentials` API 导入，每个 key 一条：provider=GOOGLE_DECISIONS，payload={"api_key":"<从私密来源在内存读取>","project_id":"<可选已核实项目ID>"}；project_id 未知应省略。payload 不接受 base_url/model 或额外字段。所有 key 的预算全局共享，凭据记录 rateLimit/dayLimit 等旧字段不决定本能力总额度。示例占位符不得当成真实密钥提交。

lease 请求带 `X-IRCS-SERVICE-ID`、`X-IRCS-SERVICE-TOKEN`、`X-IRCS-SERVICE-SCOPES=credential:lease`；路径为 GET internal/credentials/providers/GOOGLE_DECISIONS/leases?requiredPayloadKey=api_key&limit=100，仅选择未过期、provider 正确且非空的 API key；在内容安全副本内轮换，Google 429 不发第二个 key 的重试。租约只用于短期凭据读取，不持有排他锁。当前 v3 lease API 的 provider 路径参数必填，repository SQL 固定 provider = :provider；不存在无 provider 的泛 LLM lease，GOOGLE_DECISIONS 不会进入 OPENAI 池，也无需靠 priority 隔离。其他 provider 的选择顺序与租约契约不变。凭据持久化沿用现有数据库 JSON payload 体系，本次未新增全库加密；管理 API 只返回字段 present/redacted 标记，只有已授权内部 lease 能取得 secretPayload。

## 安全启用顺序

1. 先通过 Actions 验证/发布代码，保持 provider=PYTHON（或已有模型关闭状态），不给 Google 任务错误流入旧池。
2. 配置所有 CS 副本使用同一持久化、防淘汰的 Valkey DB，以及 credential-service 的内部 token/scope；核验服务内部访问策略。建议 Redis connect/read timeout 均设为有限短时（如 2 秒），故障即待复核。
3. 只通过既有受保护管理 API 在内存读取私密来源并导入每个 key 的 GOOGLE_DECISIONS 凭据，确认只返回 redacted 标记，不输出/提交密钥。请求结构为 `{"provider":"GOOGLE_DECISIONS","name":"受控别名","payload":{"api_key":"私密读取值"},"enabled":true,"priority":0,"remark":"系统共享预算"}`；project_id 仅已核实才添加。
4. 只滚动 CS 配置 `APP_CONTENT_SAFETY_ADULT_MODEL_ENABLED=true`、`APP_CONTENT_SAFETY_ADULT_MODEL_PROVIDER=GOOGLE_DECISIONS`，观察 queue age、批次尺寸、实际 usage/429 与人工标注结果。不得把 models.list 成功当成判断路径验收。回滚切回 PYTHON/关闭模型，保留预算账本，不能清库重置额度；未处理 job 在 TTL 内保留，关闭 worker 时不会新发 Google 请求。

## 验证

新增协议、跨条目结果归属、Google 独立长度/截断待复核、概率/置信与零输出、预算不可用、超时、429 冷却、lease 轮换、超估算冷却、日配额 429 至 Pacific 午夜、空项隔离、故障待复核、保留成人规则证据回归；另有真实 Valkey 跨 HTTP 合批只产生一次 Google mock 请求/一次 quota 预留、满批/等待触发、cache/去重/背压、单 owner 与失联恢复、共享冷却最早时刻、并发原子预算、日次数不部分消费、usage 幂等替换、取消、滚动窗、冷却和 DST 检查。真实 Valkey 测试使用 Actions 的 IRCS_TEST_KEY_VALUE_PORT 临时 fixture，独立测试前缀，绝不指向生产。

实现阶段仅做 Java AST / YAML / diff 静态检查；未本地编译、未运行应用测试。测试与 Native/runtime 验证交给 GitHub Actions；未部署或真实消费 Google 判断。
