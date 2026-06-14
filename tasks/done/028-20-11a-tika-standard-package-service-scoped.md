# 028-20-11A Tika standard package service-scoped restoration

状态：完成

日期：2026-06-11

## 背景

ADR D06 已拍板：`tika-parsers-standard-package` 需要接回，但只能给确实需要文件解析行为的服务接入。
此前 `028-16-D4/F3` 为降低镜像体积，把 parser standard package 明确作为 non-goal；
本切片按 post-parity 决策更新该边界。

## 范围

- 对照 V1 Tika 调用点和依赖声明。
- scraper-service 从 `tika-parser-text-module` 切回 `tika-parsers-standard-package:3.2.2`。
- storage-service 保持 `tika-core:3.2.2` only。
- 更新 Tika smoke 依赖 guard。

## 非目标

- 不新增 PDF/Office/text extraction API。
- 不给 storage-service 接 parser standard package。
- 不执行真实 provider 外网 charset smoke。
- 不修改 normalization/aggregation/LLM/media runner。

## 证据

- V1 `ircs-backend/build.gradle` 声明 `tika-core` 与 `tika-parsers-standard-package`。
- V1 源码 Tika 调用点为：
  - storage `ImageSecurityValidatorImpl` 使用 `new Tika()` / `tika.detect(data)`。
  - scraper `ListScraperServiceImpl` 使用 `UniversalEncodingDetector`。
- V1 未发现 `AutoDetectParser` / `BodyContentHandler` / `PDFParser` 通用文本抽取调用。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ScraperCharsetDetectorTest" --tests "*ListScraperClientTest" --tests "*ManualScraperServiceTaskExecutionTest"
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*ImageSecurityValidatorTest" --tests "*FileNormalizationServiceTest"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1 -SkipGradle
.\gradlew.bat --no-daemon :services:ircs-scraper-service:bootJar
```

结果：全部通过。

## 遗留

- 真实 provider charset smoke 仍后置到 `UTB-02816-P1P2-01`。
- 如果后续发现 V1 有实际 PDF/Office/text extraction 行为，再按 service owner 单独接 API 与安全限制。
- `028-20-11` 还剩 LLM runner、media 双轨和其他 library behavior follow-up。
