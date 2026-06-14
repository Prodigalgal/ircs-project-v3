# 091 RT provider first slice 迁移记录

状态：完成 first slice

关联：

- `docs/reports/097-02806h-rt-provider-first-slice.md`
- `tasks/done/028-06-h-rt-provider-first-slice.md`
- `tasks/in-progress/028-06-real-provider-live-parity.md`

## 迁移内容

- 将 V1 Rotten Tomatoes metadata provider 的最小 search-page 行为迁入 V3 `metadata-worker`。
- 新增 `ProviderType.ROTTEN_TOMATOES` implementation，低频只读访问 RT search 页面并解析首个匹配 title link。
- 使用 `OutboundHttpPolicy.publicFetch` 防止 private/internal outbound。
- 在 credential-service catalog 增加 `ROTTEN_TOMATOES` 可选 cookie/user_agent credential 支持。
- 保持 dev `APP_METADATA_RT_ENABLED=false` 默认 gate。

## 行为语义

- V1 只回填 Rotten Tomatoes URL slug，本切片保持为 `rottenTomatoesId` 最小字段映射。
- `404` 映射为空结果；`429/5xx/outbound/parse` 映射为 retryable；`403` 映射为 terminal。
- 无 credential 时继续走公开 search 页面；credential 仅用于可选 Cookie/User-Agent header。

## 验证

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

结果：通过。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1
```

结果：`SUCCESS_WITH_DEV_GATE_DISABLED`，真实 `https://www.rottentomatoes.com/search?search=The%20Matrix`
HTTP 200，解析 `The Matrix -> matrix`，`externalRequestCount=1`，未写 DB、未投递 queue、未修改 K8S env。

## 剩余

- RT full queue parity 需要在 metadata-worker dev 镜像包含本切片代码后另拆 rollout + 受控 gate smoke。
