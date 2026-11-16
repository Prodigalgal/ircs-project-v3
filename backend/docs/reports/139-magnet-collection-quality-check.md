# 磁链收集质量只读复核与固定样本回归

## 范围和证据时点

本报告复核 2026-11-16 已保存的生产只读快照，不进行生产写入、人工修复、源站请求或内容下载。抽样文件为服务器管理项目 `docs/ircs-magnet-random-sample-2026-11-16.json`，共 75 条，EZTV、The Pirate Bay、YTS BZ 各 25 条。三源等额抽样是该快照的事实，不代表生产来源权重，也不能外推约 50 万条存量链接的总体准确率。

本报告附带安全固定样本与身份判定回归。身份策略和队列可靠性由同轮系统功能改造提供；本地未运行 Java、Gradle、Maven 或容器构建。后续 3.6.4 的 GitHub Actions 168 项测试及双架构 Native 运行验证均通过，已按 GitOps 发布；完整变更与运行态边界见 [磁链质量与可靠性决策记录](../decisions/2026-11-16-magnet-quality-reliability.md)。这不改变下列历史快照的采样范围，也不代表存量链接已被自动纠正。

## 75 条快照事实

| 项目 | EZTV | The Pirate Bay | YTS BZ | 合计 |
| --- | ---: | ---: | ---: | ---: |
| 抽样行数 | 25 | 25 | 25 | 75 |
| 已存状态为 APPROVED | 25 | 25 | 25 | 75 |
| 已存 `titleSource` 缺失 | 25 | 25 | 25 | 75 |
| 旧 `matched_external_id_value` 等于 `queryValue` | 25 | 25 | 25 | 75 |
| 独立保存的 YTS 返回 `imdbCode` | 0 | 0 | 25 | 25 |
| 目标 IMDb 与返回 IMDb 均存在，可直接比较 | 0 | 0 | 8 | 8 |
| 上述可比较 IMDb 明确冲突 | 0 | 0 | 8 | 8 |
| 标题显式 Sxx 与目标已知季数冲突 | 6 | 3 | 0 | 9 |

IMDb 冲突和错季统计反映不同字段证据，不能相加作为完整错误率，也不能把其他样本计为已正确。查询类型为 IMDB 25、TITLE 44、TITLE_YEAR 6；标题包含显式季标记的候选有 38 条，目标季数非空有 26 条。历史评分分布为 68 分 19 条、72 分 25 条、76 分 6 条、92 分 25 条，评分和 APPROVED 状态本身均不是身份证明。

目标分类为 series 22、anime 13、documentary 1、other 5、short-drama 10、movie 16、variety 4、adult 4。成人条目仅计数，不在固定样本和报告中保留详细标题。固定样本也移除了 URL、域名归属后缀、磁链、哈希、业务 UUID 和查询回显字段。

## 可明确定位的例子

下列 key 使用源代码与快照中的 `rn`，不使用生产链接或作品 UUID。

| 样本 key | 目标与实际候选的冲突/不足 | 复核要求 |
| --- | --- | --- |
| `eztv-01` | 《太阳召唤》目标第 1 季；候选 `Shadow and Bone S02` | 明确错季，REJECT |
| `eztv-02` | 《天空市凶案》目标第 2 季；候选 `Big Sky S03E12` | 明确错季，REJECT |
| `eztv-04` | 《战警97》目标第 2 季；候选 `X-Men 97 S01E04` | 明确错季，REJECT |
| `thepiratebay-03` | 《金鸡一朵云》目标年份 2026；候选 `A Separation 2011` | 电影年份冲突，REJECT |
| `thepiratebay-15` | 《微信奇缘》目标为电影；候选 `Dark Matter 2024 S02E04` | 电影与剧集候选冲突，REJECT |
| `yts_bz-03` | 《火遮眼》目标 IMDb `tt33311069`；YTS 返回 `tt0463985` | 返回 ID 冲突，REJECT |
| `yts_bz-16` | 《恐怖角》目标 IMDb `tt34675596`；YTS 返回 `tt0055824` | 同名不能覆盖返回 ID 冲突，REJECT |
| `thepiratebay-05` | 《圣诞天仙配》已知别名 `A Christmas Angel Match`，候选名称与 2025 年相符；历史未记录独立标题来源 | 保留 CANDIDATE，不把历史来源补写为 CANDIDATE |
| `eztv-07` | 《金斯敦市长》别名与第 4 季相符；历史未记录独立标题来源 | 保留 CANDIDATE，不因旧高分强批 |
| `yts_bz-07` | 《伤物语：铁血篇》与候选具有跨语言名称线索；目标无 IMDb，候选无显式季数佐证且历史缺独立来源 | 保留 CANDIDATE，不能只凭已有返回 IMDb 字段强批 |

全部 9 条显式错季的 key 为 `eztv-01/02/04/13/16/24`、`thepiratebay-06/23/25`。这些是字段冲突清单，不是已经修复的生产记录。其余跨语言、同名、别名未完整拆分或分类不确定的样本仍需更多证据，不能用字符串差异直接当作已判定错误。

## 固定回归的证据边界

`services/ircs-magnet-service/src/test/resources/magnet-identity-samples.json` 固定保存 18 条安全历史样本，每源 6 条；只保留样本 key、源与行号、非敏感标题/别名/副标题/年份/季数/分类、目标外部 ID，以及原 YTS 实际返回的 `imdbCode`。它是覆盖明确冲突和证据不足边界的定向子集，不是另一轮随机抽样。

18 条均缺原始 `titleSource`，回归明确标为 `STORED_CANDIDATE`：允许只读复核硬冲突，但不视为重新抓取并确认的独立候选标题。YTS 的 `imdbCode` 按真实返回字段映射为 `returnedImdbId`；从不把 `queryValue` 或旧 `matched_external_id_*` 映射为真实返回 ID。18 条历史样本的预期分布为 REJECT 11、CANDIDATE 7、AUTO_APPROVE 0；这只是固定回归预期，不是已执行 Java 测试结果，也不是 75 条的回放统计。

`MagnetIdentitySampleRegressionTest` 对每条样本调用现有 `MagnetCandidateIdentityPolicy.evaluate(Target, candidate)`，同时断言 disposition 和 reason。另有明确标注的合成来源契约对照，验证：

- 独立候选标题有完整别名与年份时可以确认；相同标题仅为历史存储、或来源缺失时仍为 CANDIDATE。
- 正确跨语言标题缺少返回 ID 和已知别名时保留 CANDIDATE；独立返回 ID 一致时才可确认，返回 ID 不一致时 REJECT。
- `queryValue`、旧匹配字段及 100 分高分不能替代返回 ID。
- 明确错季即使带相同返回 ID，且无论来源为独立候选或历史存储，都必须 REJECT。

这些合成对照不声称 75 条历史样本具备独立来源，不参与历史样本分布或质量统计。回归不联网、不修改数据库或队列，也不对已有 APPROVED 状态作一次性改写。

## 前一轮基础采集链路证据

前一轮保存的只读基线 `服务器管理/docs/ircs-magnet-quality-baseline-2026-11-16.json` 记录 raw 54,221、unified 18,960、bindings 54,100、`zeroIds=0`、`falseSuccess=0`，以及 `newRawSinceRelease=0`。`ircs-v354-final-verification-2026-11-16.json` 的数据库检查同样记录 `zeroIds=0`、`falseSuccess=0`。这里的 `falseSuccess` 是“缺少有效权威 ID 却标记增强成功”的检查结果。

这些是前一轮 3.5.4 基础链路的已保存只读快照证据，尚无新增原始采集数据，不能证明新采集准确率；也不能据此宣称本轮磁链优化已经上线、旧链接已纠正或全部数据已正确聚合。本报告未刷新生产状态，部署、运行态及新增内容的验收仍由后续真实证据确定。

## 验证和后续验收

本地仅用 Python 检查 JSON、样本数量与三源配额、预期分布、生产快照字段统计及敏感字段排除；未运行 Java 编译或测试。Java 动态回归和来源契约对照应由既有云端 CI 执行，可用 Gradle 任务 `:services:ircs-magnet-service:test --tests com.prodigalgal.ircs.magnet.MagnetIdentitySampleRegressionTest` 定向验收。CI 通过后仍需正常系统调度和真实来源返回证据，才能评估新优化的采集与审核行为。

本次无数据库迁移、版本占用、生产发布、生产写入或人工数据修复。回滚本报告与固定回归只需撤回对应新增文件，不涉及生产数据；身份策略与服务行为的回滚由主任务按不可变制品处理。
