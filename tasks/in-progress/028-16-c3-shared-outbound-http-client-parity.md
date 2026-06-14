# 028-16-C3 shared outbound HTTP client parity

状态：进行中，主要实现已完成，真实组合测试后置到 K8S 在线窗口

归档原文：

- `docs/reports/archive/2026-06-13-doc-compaction/028-16-c3-shared-outbound-http-client-parity-full-log.md`

## 当前结论

C3 的目标是把 V1 出站 HTTP 行为迁到 shared `OutboundHttpClient`，并按调用类型区分 public
source/provider、internal service、BFF proxy、image download 等策略。

已完成：

- shared outbound contract、timeout/circuit breaker、caller-specific ENV/K8S override。
- scraper list fetch、metadata TMDB public fetch、TMDB credential internal caller。
- admin/portal BFF proxy target validation 和 method/body/streaming adoption。
- identity avatar storage multipart internal caller。
- normalization LLM credential resolver 已迁到 shared outbound。
- source network hardening 后续已经扩展到 Apache HC5、DoH/DoT/IP policy、transport mode。

## 剩余任务

1. 在真实 K8S 环境补 caller-specific circuit ENV matrix。
2. 补 BFF 真实门户/admin 页面访问、SSE/download/debug-stream live path。
3. 补 identity -> storage avatar chain 真实在线验证。
4. 与 028-21 源站网络策略矩阵合并，验证 DNS/DoH/DoT/IP policy/HTTP protocol 策略组合。

## 当前风险

- JDK HttpClient 无法完整覆盖请求级 DNS override 与 HTTP/1.0，目标态已允许 Apache HC5/更底层方案。
- 真实源站存在 DNS 污染、IPv4/IPv6 差异、老旧协议、HTML lander、502 等情况，必须在 admin
  数据源配置中可视化策略选择和测试结果。

