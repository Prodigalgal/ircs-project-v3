# 磁链采集源

magnet_service 通过 `magnet_providers` 表中的 `provider_type` 选择 runner。新增站点时只改三处：URL 形态（`GenericMagnetRequestUrlBuilder`）、类型白名单（`GatedMagnetProviderSearchRunner`，解析嵌套响应时另加专用 runner）、以及一条 DB 迁移。

## 当前可用源

| 站点 | provider_type | 接入方式 | 需要会话/账号 | 实测 |
| --- | --- | --- | --- | --- |
| thepiratebay.org | `THE_PIRATE_BAY` | `apibay.org/q.php?q=&cat=0` JSON | 否 | ✅ 20 条候选 |
| yts.pw | `YTS_BZ` | `movies-api.accel.li/api/v2/list_movies.json` | 否 | ✅ 3 条候选 |
| therarbg.com | `THE_RARBG` | `/api/v1/search/?imdb=` 或 `?q=` JSON | 否 | ✅ 有候选 |

## 磁链质量

磁链服务保存来源提供的 URI，并在读取已审核磁链时按当前配置补全公共 Tracker。来源返回的 Tracker 优先保留。

### 职责分层

| 阶段 | 类 | 职责 |
| --- | --- | --- |
| 构造 | `MagnetUriUtils.buildMagnetUri` | 只产出身份部分（`xt` + `dn`） |
| 解析各源 | `GenericMagnetCandidateParser` / `YtsBzMagnetProviderSearchRunner` | 原样保留上游给的磁链，**不追加 tracker** |
| 身份校验 | `MagnetSearchExecutor` | 校验 hash、作品身份，写入 `providerEvidence.sourceTrackerCount` |
| 合并 | `JdbcMagnetRepository.upsertLink` + `MagnetUriUtils.mergeMagnets` | 合并同一 hash 的来源 URI，保留 hybrid topic 和其他参数 |
| 交付 | `MagnetQueryService.findApprovedLinks` | 读取缓存中的来源磁链，按当前公共 Tracker 配置拼接响应 |

站点抽样曾观察到 TheRarBg 自带 22 个 Tracker，而 Apibay 和 YTS 样本只返回 hash。合并保留来源字段。每次交付从运行时配置缓存获取一份快照，配置项增删会在缓存刷新后作用于已存磁链的新响应。

### 可配置的 tracker 列表

**`app.magnet.tracker-list`**，逗号或换行分隔：

```
app.magnet.tracker-list = udp://tracker.example:1337/announce,https://other.example/announce
```

| 取值 | 行为 |
| --- | --- |
| `app.magnet.tracker-append-enabled=false` | 交付来源磁链，关闭公共列表追加 |
| 列表未设置或为空 | 使用内置 `MagnetUriUtils.DEFAULT_TRACKERS`（14 个） |
| 列表有合法 announce URL | 使用配置列表 |
| 列表有值且解析结果为空 | 交付来源磁链 |

配置列表支持 `http`、`https`、`udp`、`ws` 和 `wss`。URL 要有 host。按首次出现去重，最多取 40 个。

内置列表的挑选标准：**全部做过 DNS 可解析校验**（我实测 30 个候选，剔除 6 个失效的），且大多出现在真实发行版的 announce 列表里。**站点专属 tracker 刻意排除**（`tracker.therarbg.to`、`tracker.knaben.org` 等）—— 它们不接受外部种子，加了反而误导客户端。

### 同一 infohash 的合并

不同源对同一条种子给的信息量不同（therarbg 22 个 tracker vs apibay 0 个），而落库键是 `unique(unified_video_id, info_hash)`。原实现是 `magnet_uri = excluded.magnet_uri`，即**后跑的 provider 覆盖先跑的** —— apibay 排在 therarbg 之后执行时，22-tracker 的完整磁链会被降级成裸链。

处理单次采集响应时，先按作品和候选 hash 批量读取已存 URI，再合并写入。处理同批重复 hash 时使用刚合并的 URI。公共配置在已审核磁链的读取路径应用。

### 完整度可观测

`MagnetLinkSummary.trackerCount` 统计当前响应中 URI 的去重 Tracker 数。审核读接口返回来源 URI，交付读接口返回补全 URI。`providerEvidence.sourceTrackerCount` 记录最近一次来源观测的数量，可与该次观测的 `provider_code` 一起查看。合并保留磁链字段，来源证据对应最近一次写入。

### 自动采纳

`auto_approve_allowed` 现状：

| provider | autoApprove |
| --- | --- |
| `yts_bz` / `thepiratebay` / `eztv` | true |
| `therarbg` | 新增时为 false |
| `ext_to` / `thepiratebay_org` | false（已剔除/停用） |

TheRarBg 首批候选按同名、不同年份和剧集季号抽样后，再决定是否开启自动采纳。达到自动采纳条件需要独立的作品身份依据和来源开关。审核、拒绝和隐藏的状态由后续采集保留。

### 待审队列

`GET /api/v1/magnets/links?status=CANDIDATE&page=0&size=50` 按状态和页码枚举。页码从 0 开始，每页上限 200。

全局列表用于发现跨作品的待审条目。按作品查看磁链和修改状态继续使用 `/api/v1/magnets/unified/{id}/links` 下的管理接口。审核事务提交后，磁链缓存失效并提交搜索索引更新。

### 发布与回退

3.17.0 迁移新建 TheRarBg 来源和公共 Tracker 配置，按条目更新采集白名单与间隔。兼容的回退路径是停用 TheRarBg，移除它的白名单条目，并用上一不可变磁链服务镜像回滚。现有磁链和审核状态随数据库保留。

## 为什么 TPB 不用网页

`thepiratebay.org` 已改为 JS 渲染：搜索页只返回一个脚本外壳，由前端再向 `apibay.org` 取数，静态 HTML 里**零条磁力链接**。因此 `THE_PIRATE_BAY_FRONTEND`（`thepiratebay_org`）在迁移中被停用，TPB 统一走 `apibay.org` API —— 即站点自己用的那个接口。apibay 的字段（`info_hash`/`name`/`size`/`seeders`/`leechers`/`added`/`imdb`）与通用 JSON 解析器完全对齐，无需专用代码。

## 已剔除的源

| 站点 | 原因 |
| --- | --- |
| extto.com / ext.to | 全站（含 `robots.txt`）由 Cloudflare 托管挑战拦截，`cf-mitigated: challenge`；有头 Chrome + 点击 Turnstile 后仍 403，`cf_clearance` 只下发到 `.cloudflare.com`，判定为出口 IP 被标记。需要 CF 求解服务（CapSolver `AntiCloudflareTask`、FlareSolverr/Byparr）或干净住宅代理 |
| torrentclaw.to | 匿名只返回作品与种子元数据，**`infoHash`/`magnetUrl` 被剥离**，响应带 `_notice` 要求注册账号取免费 API key。本服务只消费磁力，所以完全未注册该 provider。另注意其源站偶发 Cloudflare 502 |
| 0bt0.com | `app_id`/`identity` 静态鉴权可通过（`/prod/api/v1/getVideoList` 匿名 200），但 `all_seeds` 中 73/73 条种子均为 `need_vip=1` 且 `zlink`/`down` 为空 —— 磁力与网盘链接全部 VIP 门控，匿名拿不到任何磁链 |

三者都在 `app.magnet.real-provider.allowlist` 中被排除。

## 排查手法

排查时通过配置的代理请求候选 API，检查状态码、重定向、候选字段和有效 hash。页面响应的状态码需要结合正文判断。

- **重定向**：`therarbg.com/api/v1/search` 301 到 `/api/v1/search/`，而出站客户端**不跟随重定向**，所以路径必须带尾斜杠。
- **JS 渲染**：TPB 前端返回 1.3 KB 外壳（HTTP 200、标题正常）却零磁链 —— 只看状态码会误判为可用。

## 验证

CI 覆盖 URL、限流、URI 保真、动态配置、待审分页、缓存和现有生产结构的升级。需要验证当前来源响应时，可在具备代理的 CI 环境按需开启在线测试：

```bash
./gradlew :services:ircs-magnet-service:test --tests '*MagnetProvidersLiveSmokeTest' \
  -Dircs.smoke.magnet=true -Dircs.smoke.proxy=127.0.0.1:10808
```

三个源都必须返回带合法 40 位 hash 的候选。
