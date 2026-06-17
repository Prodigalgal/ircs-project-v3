package com.prodigalgal.ircs.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigDefaults {

    static final String ADMIN_PASSWORD_KEY = "security.admin.password";
    static final String JWT_SECRET_KEY = "security.jwt.secret";
    static final Set<String> REQUIRED_KEYS = Set.of(
            ADMIN_PASSWORD_KEY,
            JWT_SECRET_KEY,
            "app.storage.r2.bucket-name",
            "app.storage.r2.public-domain");
    static final Set<String> LLM_PROMPT_KEYS = Set.of(
            "app.ai.llm.prompt.language",
            "app.ai.llm.prompt.area",
            "app.ai.llm.prompt.genre",
            "app.ai.llm.prompt.category");
    static final Set<String> CORE_KEYS;
    private static final Map<String, List<String>> INJECTION_ALIASES = Map.ofEntries(
            Map.entry("security.admin.username", List.of("app.identity.admin.username")),
            Map.entry("security.admin.password", List.of("app.identity.admin.password-hash")),
            Map.entry("security.jwt.secret", List.of("app.identity.jwt.secret")),
            Map.entry("security.jwt.iat-floor", List.of("app.identity.jwt.iat-floor")),
            Map.entry("member.auth.code.validity-seconds", List.of("app.identity.code.validity")),
            Map.entry("member.auth.code.rate-limit-seconds", List.of("app.identity.code.rate-limit")),
            Map.entry("member.register.email-verify.enabled", List.of("app.identity.register.email-verify-enabled")),
            Map.entry("member.register.timezone", List.of("app.identity.register.timezone")),
            Map.entry("app.mail.enabled", List.of("app.identity.mail.enabled")),
            Map.entry("app.frontend.url", List.of("app.identity.frontend-url")),
            Map.entry("app.storage.r2.public-domain", List.of("app.storage.r2-public-domain")));

    private static final List<DefaultConfig> DEFAULTS = List.of(
            cfg("normalization.timeout.processing-min", "30", "归一化处理超时时间(分钟)"),
            cfg("normalization.timeout.pending-min", "60", "归一化等待超时补救(分钟)"),
            cfg("normalization.max-retries", "5", "归一化最大重试次数"),
            cfg("normalization.backoff.base-seconds", "60", "归一化重试基础退避时间(秒)"),
            cfg("normalization.batch-size", "500", "归一化处理基准批次"),
            cfg("normalization.batch-size.min", "100", "归一化最小批次"),
            cfg("normalization.batch-size.max", "1000", "归一化最大批次"),
            cfg("app.normalization.valkey-worker.enabled", "true", "是否启用归一化 Valkey pipeline worker"),
            cfg("app.normalization.valkey-worker.batch-size", "10", "归一化 Valkey worker 每轮领取数量"),
            cfg("app.normalization.valkey-worker.visibility-timeout", "PT10M", "归一化 Valkey worker 任务可见性超时"),
            cfg("app.normalization.valkey-worker.initial-delay-ms", "10000", "归一化 Valkey worker 启动延迟(毫秒)"),
            cfg("app.normalization.valkey-worker.fixed-delay-ms", "1000", "归一化 Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.metadata.timeout.processing-min", "45", "元数据丰富处理超时时间(分钟)"),
            cfg("app.metadata.timeout.pending-min", "120", "元数据等待超时补救(分钟)"),
            cfg("app.metadata.enabled", "true", "全局启用元数据填充服务"),
            cfg("app.metadata.cron", "0 0/30 * * * ?", "元数据填充任务 CRON 表达式"),
            cfg("app.metadata.interval.ms", "1800000", "元数据填充回退间隔(毫秒)"),
            cfg("app.metadata.batch-size", "200", "元数据填充任务批处理大小"),
            cfg("app.metadata.batch-size.min", "50", "元数据填充最小批次"),
            cfg("app.metadata.batch-size.max", "500", "元数据填充最大批次"),
            cfg("app.search.sync.batch-size", "200", "搜索增量同步每批次数量"),
            cfg("app.frontend.url", "https://huawai.mnnu.eu.org", "前端访问域名"),
            cfg("security.admin.username", "admin", "管理员账号"),
            cfg(ADMIN_PASSWORD_KEY, "", "管理员密码 (BCrypt)"),
            cfg(JWT_SECRET_KEY, "", "JWT 签名密钥"),
            cfg("security.jwt.iat-floor", "0", "JWT 签发时间地板"),
            cfg("security.ratelimit.enabled", "true", "是否启用 API 请求限流"),
            cfg("security.ratelimit.rules", "classpath:presets/ratelimit_rules.json", "动态限流路由规则 (JSON数组)"),
            cfg("member.auth.code.validity-seconds", "900", "注册/激活验证码有效期(秒)"),
            cfg("member.auth.code.rate-limit-seconds", "120", "验证码获取频率限制(秒)"),
            cfg("member.register.email-verify.enabled", "true", "是否启用注册邮箱强制验证"),
            cfg("member.register.time-window.enabled", "false", "是否启用注册时间段限制"),
            cfg("member.register.time-window.start", "09:00", "允许注册开始时间"),
            cfg("member.register.time-window.end", "18:00", "允许注册结束时间"),
            cfg("member.register.timezone", "Asia/Shanghai", "注册时间限制所依据的时区"),
            cfg("member.message.daily-limit", "5", "每个用户每日允许提交的留言数量"),
            cfg("member.oauth.enabled", "false", "是否启用会员第三方登录"),
            cfg("member.oauth.auto-register.enabled", "true", "第三方登录首次进入时是否自动创建会员"),
            cfg("member.oauth.bind-existing-email.enabled", "false", "第三方登录邮箱命中已有会员时是否自动绑定"),
            cfg("member.oauth.email-ownership-verification.enabled", "true", "第三方登录绑定已有邮箱前是否强制验证邮箱归属"),
            cfg("member.oauth.redirect-base-url", "https://huawai.mnnu.eu.org", "第三方登录回调基础域名"),
            cfg("member.oauth.allowed-providers", "google,x,github,gitee,wechat,qq", "允许展示的第三方登录 Provider 列表"),
            cfg("member.oauth.google.enabled", "false", "是否启用 Google 登录"),
            cfg("member.oauth.google.client-id", "", "Google OAuth Client ID"),
            cfg("member.oauth.google.client-secret", "", "Google OAuth Client Secret"),
            cfg("member.oauth.google.scope", "openid email profile", "Google OAuth Scope"),
            cfg("member.oauth.google.redirect-uri", "/api/portal/auth/oauth/google/callback", "Google OAuth 回调路径"),
            cfg("member.oauth.x.enabled", "false", "是否启用 X 登录"),
            cfg("member.oauth.x.client-id", "", "X OAuth Client ID"),
            cfg("member.oauth.x.client-secret", "", "X OAuth Client Secret"),
            cfg("member.oauth.x.scope", "users.read tweet.read offline.access", "X OAuth Scope"),
            cfg("member.oauth.x.redirect-uri", "/api/portal/auth/oauth/x/callback", "X OAuth 回调路径"),
            cfg("member.oauth.github.enabled", "false", "是否启用 GitHub 登录"),
            cfg("member.oauth.github.client-id", "", "GitHub OAuth Client ID"),
            cfg("member.oauth.github.client-secret", "", "GitHub OAuth Client Secret"),
            cfg("member.oauth.github.scope", "read:user user:email", "GitHub OAuth Scope"),
            cfg("member.oauth.github.redirect-uri", "/api/portal/auth/oauth/github/callback", "GitHub OAuth 回调路径"),
            cfg("member.oauth.gitee.enabled", "false", "是否启用 Gitee 登录"),
            cfg("member.oauth.gitee.client-id", "", "Gitee OAuth Client ID"),
            cfg("member.oauth.gitee.client-secret", "", "Gitee OAuth Client Secret"),
            cfg("member.oauth.gitee.scope", "user_info emails", "Gitee OAuth Scope"),
            cfg("member.oauth.gitee.redirect-uri", "/api/portal/auth/oauth/gitee/callback", "Gitee OAuth 回调路径"),
            cfg("member.oauth.wechat.enabled", "false", "是否启用微信登录"),
            cfg("member.oauth.wechat.app-id", "", "微信开放平台 App ID"),
            cfg("member.oauth.wechat.app-secret", "", "微信开放平台 App Secret"),
            cfg("member.oauth.wechat.scope", "snsapi_login", "微信 OAuth Scope"),
            cfg("member.oauth.wechat.redirect-uri", "/api/portal/auth/oauth/wechat/callback", "微信登录回调路径"),
            cfg("member.oauth.qq.enabled", "false", "是否启用 QQ 登录"),
            cfg("member.oauth.qq.app-id", "", "QQ 互联 App ID"),
            cfg("member.oauth.qq.app-key", "", "QQ 互联 App Key"),
            cfg("member.oauth.qq.scope", "get_user_info", "QQ OAuth Scope"),
            cfg("member.oauth.qq.redirect-uri", "/api/portal/auth/oauth/qq/callback", "QQ 登录回调路径"),
            cfg("app.mail.enabled", "true", "是否启用邮件发送功能"),
            cfg("app.mail.host", "smtp.gmail.com", "SMTP 服务器地址"),
            cfg("app.mail.port", "465", "SMTP 端口"),
            cfg("app.mail.protocol", "smtp", "邮件协议"),
            cfg("app.mail.from", "HuaWai System <huawai.system@gmail.com>", "默认发件人地址"),
            cfg("app.mail.properties.auth", "true", "是否需要SMTP认证"),
            cfg("app.mail.properties.starttls", "false", "是否启用STARTTLS"),
            cfg("app.mail.properties.ssl", "true", "是否启用纯SSL"),
            cfg("app.mail.timeout", "10000", "邮件超时时间(毫秒)"),
            cfg("app.mail.debug", "false", "是否开启邮件调试日志"),
            cfg("app.mail.rate-limit.min-ms", "1000", "邮件发送最小间隔(毫秒)"),
            cfg("app.mail.rate-limit.max-ms", "2000", "邮件发送最大间隔(毫秒)"),
            cfg("app.mail.rate-limit.max-wait-ms", "60000", "邮件限流最大等待时间(毫秒)"),
            cfg("app.adaptive.enabled", "true", "是否启用自适应负载调度"),
            cfg("app.adaptive.load-threshold", "0.8", "系统高负载阈值 (0.0-1.0)"),
            cfg("app.adaptive.check-interval-ms", "10000", "资源监控采样间隔(毫秒)"),
            cfg("app.aggregation.work-queue.batch-size", "100", "聚合运行队列基准批次"),
            cfg("app.aggregation.work-queue.batch-size.min", "20", "聚合运行队列最小批次"),
            cfg("app.aggregation.work-queue.batch-size.max", "500", "聚合运行队列最大批次"),
            cfg("app.aggregation.work-queue.worker.enabled", "true", "是否启用聚合 Valkey 运行队列 worker"),
            cfg("app.aggregation.work-queue.worker.batch-size", "100", "聚合 Valkey worker 每轮领取数量"),
            cfg("app.aggregation.work-queue.worker.visibility-seconds", "600", "聚合 Valkey worker 任务可见性超时(秒)"),
            cfg("app.aggregation.work-queue.worker.max-retries", "8", "聚合 Valkey worker 最大重试次数"),
            cfg("app.aggregation.work-queue.worker.max-backoff-seconds", "900", "聚合 Valkey worker 最大退避时间(秒)"),
            cfg("app.aggregation.work-queue.worker.initial-delay-ms", "10000", "聚合 Valkey worker 启动延迟(毫秒)"),
            cfg("app.aggregation.work-queue.worker.fixed-delay-ms", "1000", "聚合 Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.maintenance.sanitize.batch-size", "1000", "全量清洗任务每批次数量"),
            cfg("app.maintenance.aggregation-reset.batch-size", "1000", "聚合重置任务每批次数量"),
            cfg("app.maintenance.rule-cleaning.batch-size", "100", "规则清洗每批次数量"),
            cfg("app.maintenance.search.reindex-batch-size", "500", "搜索索引全量重建每批次数量"),
            cfg("app.maintenance.batch-size.min", "200", "运维任务最小批次"),
            cfg("app.maintenance.batch-size.max", "2000", "运维任务最大批次"),
            cfgUpgradeLegacyFalse("app.maintenance.playlist-repair.enabled", "true", "是否自动修复无剧集的视频"),
            cfg("app.maintenance.playlist-repair.batch-size", "50", "播放列表修复基准批次"),
            cfg("app.maintenance.playlist-repair.batch-size.min", "10", "播放列表修复最小批次"),
            cfg("app.maintenance.playlist-repair.batch-size.max", "100", "播放列表修复最大批次"),
            cfg("app.search.work-queue.batch-size", "500", "搜索同步运行队列基准批次"),
            cfg("app.search.work-queue.batch-size.min", "100", "搜索同步运行队列最小批次"),
            cfg("app.search.work-queue.batch-size.max", "1000", "搜索同步运行队列最大批次"),
            cfg("app.search.work-queue.worker.enabled", "true", "是否启用搜索 Valkey 运行队列 worker"),
            cfg("app.search.work-queue.worker.batch-size", "100", "搜索 Valkey worker 每轮领取数量"),
            cfg("app.search.work-queue.worker.visibility-seconds", "300", "搜索 Valkey worker 任务可见性超时(秒)"),
            cfg("app.search.work-queue.worker.max-retries", "5", "搜索 Valkey worker 最大重试次数"),
            cfg("app.search.work-queue.worker.max-backoff-seconds", "300", "搜索 Valkey worker 最大退避时间(秒)"),
            cfg("app.search.work-queue.worker.initial-delay-ms", "10000", "搜索 Valkey worker 启动延迟(毫秒)"),
            cfg("app.search.work-queue.worker.fixed-delay-ms", "1000", "搜索 Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.search.reconciliation.enabled", "true", "是否启用ES-DB数据对账定时任务"),
            cfg("app.storage.base-path", "./storage", "文件存储本地根目录"),
            cfg("app.storage.public-path", "/media", "静态资源Web访问前缀"),
            cfg("app.storage.path.prefix.cover", "covers", "影视封面存储目录前缀"),
            cfg("app.storage.path.prefix.avatar", "avatars", "用户头像存储目录前缀"),
            cfg("app.maintenance.queue-retention-days", "7", "采集队列日志保留天数"),
            cfgUpgradeLegacyFalse("app.storage.image.download.enabled", "true", "是否开启图片本地化下载"),
            cfg("app.storage.image.max-retries", "3", "图片下载/上传最大重试次数"),
            cfg("app.storage.image.timeout-min", "30", "图片处理超时时间(分钟)"),
            cfg("app.storage.image.download.batch-size", "100", "图片下载任务基准批次"),
            cfg("app.storage.image.download.batch-size.min", "20", "图片下载任务最小批次"),
            cfg("app.storage.image.download.batch-size.max", "200", "图片下载任务最大批次"),
            cfg("app.storage.image.cleanup.batch-size", "100", "图片清理任务基准批次"),
            cfg("app.storage.image.cleanup.batch-size.min", "50", "图片清理任务最小批次"),
            cfg("app.storage.image.cleanup.batch-size.max", "500", "图片清理任务最大批次"),
            cfg("app.storage.r2.work-queue.worker.enabled", "true", "是否启用存储 R2 Valkey 运行队列 worker"),
            cfg("app.storage.r2.work-queue.worker.batch-size", "25", "存储 R2 Valkey worker 每轮领取数量"),
            cfg("app.storage.r2.work-queue.worker.visibility-seconds", "900", "存储 R2 Valkey worker 任务可见性超时(秒)"),
            cfg("app.storage.r2.work-queue.worker.max-retries", "8", "存储 R2 Valkey worker 最大重试次数"),
            cfg("app.storage.r2.work-queue.worker.max-backoff-seconds", "1800", "存储 R2 Valkey worker 最大退避时间(秒)"),
            cfg("app.storage.r2.work-queue.worker.initial-delay-ms", "30000", "存储 R2 Valkey worker 启动延迟(毫秒)"),
            cfg("app.storage.r2.work-queue.worker.fixed-delay-ms", "5000", "存储 R2 Valkey worker 空转轮询间隔(毫秒)"),
            cfgUpgradeLegacyFalse("app.storage.r2.enabled", "true", "是否启用 Cloudflare R2"),
            cfg("app.storage.r2.bucket-name", "ircs", "R2 Bucket Name"),
            cfg("app.storage.r2.public-domain", "img.mnnu.eu.org", "R2 公开访问域名"),
            cfgUpgradeLegacyFalse("app.ai.llm.enabled", "true", "是否启用 LLM 辅助清洗"),
            cfg("app.ai.llm.model", "gemma-4-31b-it", "LLM 模型名称"),
            cfg("app.ai.llm.rate-limit.rpm", "10", "LLM RPM 限制"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.enabled", "true", "是否启用 LLM 清洗 Valkey 运行队列 worker"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.batch-size", "100", "LLM 清洗 Valkey worker 每轮领取数量"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.visibility-seconds", "900", "LLM 清洗 Valkey worker 任务可见性超时(秒)"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.max-retries", "8", "LLM 清洗 Valkey worker 最大重试次数"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.max-backoff-seconds", "1800", "LLM 清洗 Valkey worker 最大退避时间(秒)"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.initial-delay-ms", "30000", "LLM 清洗 Valkey worker 启动延迟(毫秒)"),
            cfg("app.normalization.llm-cleaning.work-queue.worker.fixed-delay-ms", "5000", "LLM 清洗 Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.ai.llm.prompt.language", llmPrompt("linguistic", "standard list"), "LLM 语言清洗 Prompt 模板"),
            cfg("app.ai.llm.prompt.area", llmPrompt("geographic", "country or region list"), "LLM 地区清洗 Prompt 模板"),
            cfg("app.ai.llm.prompt.genre", llmPrompt("video genre", "genre list"), "LLM 题材清洗 Prompt 模板"),
            cfg("app.ai.llm.prompt.category", llmPrompt("video category", "category list"), "LLM 分类清洗 Prompt 模板"),
            cfg("global.traffic.min-delay-ms", "3000", "全局默认最小请求间隔(毫秒)"),
            cfg("global.traffic.max-delay-ms", "5000", "全局默认最大请求间隔(毫秒)"),
            cfg("global.traffic.safety-floor-ms", "3000", "全局流量安全底线(毫秒)"),
            cfg("global.traffic.max-wait-ms", "120000", "流量控制最大容忍等待时间(毫秒)"),
            cfg("app.metadata.tmdb.enabled", "true", "启用 TMDB 数据源"),
            cfg("app.metadata.tmdb.min-delay-ms", "200", "TMDB 请求最小延迟"),
            cfg("app.metadata.tmdb.max-delay-ms", "500", "TMDB 请求最大延迟"),
            cfg("app.metadata.tmdb.proxy.enabled", "false", "TMDB 是否走代理"),
            cfg("app.metadata.tmdb.proxy.type", "HTTP", "TMDB 代理类型"),
            cfg("app.metadata.tmdb.proxy.host", "", "TMDB 代理主机"),
            cfg("app.metadata.tmdb.proxy.port", "0", "TMDB 代理端口"),
            cfg("app.metadata.tmdb.proxy.username", "", "TMDB 代理用户名"),
            cfg("app.metadata.tmdb.proxy.password", "", "TMDB 代理密码"),
            cfg("app.metadata.douban.enabled", "true", "启用豆瓣爬虫"),
            cfg("app.metadata.douban.min-delay-ms", "2000", "豆瓣请求最小延迟"),
            cfg("app.metadata.douban.max-delay-ms", "5000", "豆瓣请求最大延迟"),
            cfg("app.metadata.douban.proxy.enabled", "false", "豆瓣是否走代理"),
            cfg("app.metadata.douban.proxy.type", "HTTP", "豆瓣 代理类型"),
            cfg("app.metadata.douban.proxy.host", "", "豆瓣 代理主机"),
            cfg("app.metadata.douban.proxy.port", "0", "豆瓣 代理端口"),
            cfg("app.metadata.douban.proxy.username", "", "豆瓣 代理用户名"),
            cfg("app.metadata.douban.proxy.password", "", "豆瓣 代理密码"),
            cfg("app.metadata.rotten-tomatoes.enabled", "true", "启用烂番茄爬虫"),
            cfg("app.metadata.rotten-tomatoes.min-delay-ms", "2000", "烂番茄请求最小延迟"),
            cfg("app.metadata.rotten-tomatoes.max-delay-ms", "5000", "烂番茄请求最大延迟"),
            cfg("app.metadata.rotten-tomatoes.proxy.enabled", "false", "烂番茄是否走代理"),
            cfg("app.metadata.rotten-tomatoes.proxy.type", "HTTP", "烂番茄 代理类型"),
            cfg("app.metadata.rotten-tomatoes.proxy.host", "", "烂番茄 代理主机"),
            cfg("app.metadata.rotten-tomatoes.proxy.port", "0", "烂番茄 代理端口"),
            cfg("app.metadata.rotten-tomatoes.proxy.username", "", "烂番茄 代理用户名"),
            cfg("app.metadata.rotten-tomatoes.proxy.password", "", "烂番茄 代理密码"),
            cfg("app.metadata.valkey-dispatcher.enabled", "true", "是否启用元数据分发 Valkey pipeline worker"),
            cfg("app.metadata.valkey-dispatcher.batch-size", "10", "元数据分发 Valkey worker 每轮领取数量"),
            cfg("app.metadata.valkey-dispatcher.visibility-timeout", "PT10M", "元数据分发 Valkey worker 任务可见性超时"),
            cfg("app.metadata.valkey-dispatcher.retry-delay", "PT5M", "元数据分发 Valkey worker 重试延迟"),
            cfg("app.metadata.valkey-dispatcher.initial-delay-ms", "10000", "元数据分发 Valkey worker 启动延迟(毫秒)"),
            cfg("app.metadata.valkey-dispatcher.fixed-delay-ms", "1000", "元数据分发 Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.metadata.valkey-provider-worker.enabled", "true", "是否启用元数据 Provider Valkey pipeline worker"),
            cfg("app.metadata.valkey-provider-worker.batch-size", "10", "元数据 Provider Valkey worker 每轮领取数量"),
            cfg("app.metadata.valkey-provider-worker.visibility-timeout", "PT10M", "元数据 Provider Valkey worker 任务可见性超时"),
            cfg("app.metadata.valkey-provider-worker.retry-delay", "PT2M", "元数据 Provider Valkey worker 重试延迟"),
            cfg("app.metadata.valkey-provider-worker.initial-delay-ms", "10000", "元数据 Provider Valkey worker 启动延迟(毫秒)"),
            cfg("app.metadata.valkey-provider-worker.fixed-delay-ms", "1000", "元数据 Provider Valkey worker 空转轮询间隔(毫秒)"),
            cfg("app.scraper.trend-sync.enabled", "true", "是否启用热门榜单自动同步"),
            cfg("app.scraper.trend-sync.cron", "0 0 3 * * ?", "热门榜单同步任务 CRON 表达式"),
            cfg("app.scraper.trend-sync.timezone", "Asia/Shanghai", "热门榜单同步任务执行时区"),
            cfg("app.search.sync.enabled", "true", "是否启用搜索增量同步"),
            cfg("app.search.sync.cron", "0 0/5 * * * ?", "搜索增量同步任务 CRON"));

    static {
        CORE_KEYS = DEFAULTS.stream().map(DefaultConfig::key).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private final PasswordEncoder passwordEncoder;

    public SystemConfigDefaults(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public List<DefaultConfig> all() {
        return DEFAULTS;
    }

    public ResolvedDefault resolve(DefaultConfig config) {
        if (ADMIN_PASSWORD_KEY.equals(config.key())) {
            return new ResolvedDefault(
                    config.key(),
                    passwordEncoder.encode(generateRandomPassword()),
                    config.description(),
                    config.upgradeLegacyFalse());
        }
        if (JWT_SECRET_KEY.equals(config.key())) {
            return new ResolvedDefault(
                    config.key(),
                    generateJwtSecret(),
                    config.description(),
                    config.upgradeLegacyFalse());
        }
        return new ResolvedDefault(config.key(), config.defaultValue(), config.description(), config.upgradeLegacyFalse());
    }

    public boolean isCoreKey(String key) {
        return CORE_KEYS.contains(key);
    }

    public boolean isRequiredKey(String key) {
        return REQUIRED_KEYS.contains(key);
    }

    public java.util.Optional<String> staticDefaultValue(String key) {
        return DEFAULTS.stream()
                .filter(config -> config.key().equals(key))
                .findFirst()
                .map(DefaultConfig::defaultValue);
    }

    public List<String> injectionKeys(String key) {
        List<String> aliases = INJECTION_ALIASES.getOrDefault(key, List.of());
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(key), aliases.stream())
                .flatMap(SystemConfigDefaults::propertyAndEnvKeys)
                .distinct()
                .toList();
    }

    private static java.util.stream.Stream<String> propertyAndEnvKeys(String key) {
        return java.util.stream.Stream.of(key, toEnvironmentVariableName(key));
    }

    private static String toEnvironmentVariableName(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(java.util.Locale.ROOT);
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 12);
    }

    private String generateJwtSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static DefaultConfig cfg(String key, String defaultValue, String description) {
        return new DefaultConfig(key, defaultValue, description, false);
    }

    private static DefaultConfig cfgUpgradeLegacyFalse(String key, String defaultValue, String description) {
        return new DefaultConfig(key, defaultValue, description, true);
    }

    private static String llmPrompt(String expert, String validList) {
        return """
        You are a %s data cleaning expert.
        Task:
        - Map each raw item to one item from the valid %s.
        - If no confident match exists, return `"standard": null` and `"isNoise": true`.
        Rules:
        1. The `raw` field must be an exact verbatim copy of the input item.
        2. Do not normalize Traditional Chinese, case, spacing, punctuation, or symbols in `raw`.
        3. Respond with JSON only. Do not wrap the response in Markdown.
        Valid Standard Items:
        {standardItems}
        Input Raw Items:
        {rawItems}
        Return a JSON array with this schema:
        [{"raw":"string","standard":"string|null","isNoise":boolean}]
        """.formatted(expert, validList).trim();
    }

    public record DefaultConfig(String key, String defaultValue, String description, boolean upgradeLegacyFalse) {
        public DefaultConfig(String key, String defaultValue, String description) {
            this(key, defaultValue, description, false);
        }
    }

    public record ResolvedDefault(String key, String value, String description, boolean upgradeLegacyFalse) {
        public ResolvedDefault(String key, String value, String description) {
            this(key, value, description, false);
        }
    }
}
