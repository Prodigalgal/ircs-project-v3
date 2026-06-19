package com.prodigalgal.ircs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class SystemConfigDefaultsTest {

    private final SystemConfigDefaults defaults = new SystemConfigDefaults(NoOpPasswordEncoder.getInstance());

    @Test
    void includesCurrentSystemConfigKeyCatalog() {
        Set<String> actual = defaults.all().stream()
                .map(SystemConfigDefaults.DefaultConfig::key)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> expected = java.util.stream.Stream.concat(
                        CURRENT_SYSTEM_CONFIG_KEYS.stream(), V3_OAUTH_CONFIG_KEYS.stream())
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(expected, actual);
        assertEquals(expected.size(), defaults.all().size());
    }

    @Test
    void exposesCoreAndRequiredKeyBoundaries() {
        assertTrue(defaults.isCoreKey("security.admin.password"));
        assertTrue(defaults.isCoreKey("security.jwt.secret"));
        assertTrue(defaults.isCoreKey("app.ai.llm.prompt.language"));
        assertTrue(defaults.isCoreKey("app.storage.r2.enabled"));
        assertTrue(defaults.isCoreKey("app.aggregation.work-queue.batch-size"));
        assertTrue(defaults.isRequiredKey("app.storage.r2.bucket-name"));
        assertTrue(defaults.isRequiredKey("app.storage.r2.public-domain"));
        assertFalse(defaults.isRequiredKey("app.metadata.tmdb.proxy.password"));
        assertFalse(defaults.isCoreKey("custom.user.config"));
    }

    @Test
    void keepsV1ImportantDefaultValues() {
        assertEquals("100", defaults.staticDefaultValue("normalization.batch-size.min").orElseThrow());
        assertEquals("classpath:presets/ratelimit_rules.json",
                defaults.staticDefaultValue("security.ratelimit.rules").orElseThrow());
        assertEquals("3000", defaults.staticDefaultValue("global.traffic.safety-floor-ms").orElseThrow());
        assertEquals("HTTP", defaults.staticDefaultValue("app.metadata.douban.proxy.type").orElseThrow());
        assertEquals("0 0/5 * * * ?", defaults.staticDefaultValue("app.search.sync.cron").orElseThrow());
        assertEquals("true", defaults.staticDefaultValue("app.storage.image.download.enabled").orElseThrow());
        assertEquals("true", defaults.staticDefaultValue("app.storage.r2.enabled").orElseThrow());
        assertEquals("60000", defaults.staticDefaultValue("app.mail.rate-limit.max-wait-ms").orElseThrow());
        assertEquals("true", defaults.staticDefaultValue("app.ai.llm.enabled").orElseThrow());
        assertEquals("true", defaults.staticDefaultValue("app.maintenance.playlist-repair.enabled").orElseThrow());
        assertEquals("false", defaults.staticDefaultValue("app.metadata.tmdb.proxy.enabled").orElseThrow());
        assertEquals("150", defaults.staticDefaultValue("app.metadata.public-traffic.default-gap-ms").orElseThrow());
        assertEquals("40", defaults.staticDefaultValue("app.metadata.valkey-provider-worker.batch-size").orElseThrow());
        assertEquals("16", defaults.staticDefaultValue("app.metadata.valkey-provider-worker.parallelism").orElseThrow());
        assertEquals("false", defaults.staticDefaultValue("member.register.time-window.enabled").orElseThrow());
        assertEquals("false", defaults.staticDefaultValue("member.oauth.enabled").orElseThrow());
        assertEquals("true", defaults.staticDefaultValue("member.oauth.auto-register.enabled").orElseThrow());
        assertEquals("false", defaults.staticDefaultValue("member.oauth.bind-existing-email.enabled").orElseThrow());
        assertEquals("true",
                defaults.staticDefaultValue("member.oauth.email-ownership-verification.enabled").orElseThrow());
        assertEquals("https://huawai.mnnu.eu.org", defaults.staticDefaultValue("app.frontend.url").orElseThrow());
        assertEquals("https://huawai.mnnu.eu.org",
                defaults.staticDefaultValue("member.oauth.redirect-base-url").orElseThrow());
        assertEquals("google,x,github,gitee,wechat,qq",
                defaults.staticDefaultValue("member.oauth.allowed-providers").orElseThrow());
    }

    @Test
    void keepsV1LlmPromptRawPreservationContract() {
        String prompt = defaults.staticDefaultValue("app.ai.llm.prompt.language").orElseThrow();

        assertTrue(prompt.contains("Do not normalize Traditional Chinese"));
        assertTrue(prompt.contains("{rawItems}"));
        assertTrue(prompt.contains("{standardItems}"));
    }

    @Test
    void resolvesSensitiveDefaultsWithoutBlankValues() {
        SystemConfigDefaults.ResolvedDefault adminPassword = defaults.resolve(new SystemConfigDefaults.DefaultConfig(
                "security.admin.password",
                "",
                "admin password"));
        SystemConfigDefaults.ResolvedDefault jwtSecret = defaults.resolve(new SystemConfigDefaults.DefaultConfig(
                "security.jwt.secret",
                "",
                "jwt secret"));

        assertFalse(adminPassword.value().isBlank());
        assertEquals(64, jwtSecret.value().length());
    }

    @Test
    void marksNewEnabledDefaultsForLegacyFalseUpgrade() {
        SystemConfigDefaults.DefaultConfig imageDownload = defaults.all().stream()
                .filter(config -> config.key().equals("app.storage.image.download.enabled"))
                .findFirst()
                .orElseThrow();
        SystemConfigDefaults.DefaultConfig proxy = defaults.all().stream()
                .filter(config -> config.key().equals("app.metadata.tmdb.proxy.enabled"))
                .findFirst()
                .orElseThrow();

        assertTrue(imageDownload.upgradeLegacyFalse());
        assertFalse(proxy.upgradeLegacyFalse());
    }

    private static final Set<String> V3_OAUTH_CONFIG_KEYS = Set.of(
            "member.oauth.enabled",
            "member.oauth.auto-register.enabled",
            "member.oauth.bind-existing-email.enabled",
            "member.oauth.email-ownership-verification.enabled",
            "member.oauth.redirect-base-url",
            "member.oauth.allowed-providers",
            "member.oauth.github.enabled",
            "member.oauth.github.client-id",
            "member.oauth.github.client-secret",
            "member.oauth.github.scope",
            "member.oauth.github.redirect-uri",
            "member.oauth.google.enabled",
            "member.oauth.google.client-id",
            "member.oauth.google.client-secret",
            "member.oauth.google.scope",
            "member.oauth.google.redirect-uri",
            "member.oauth.x.enabled",
            "member.oauth.x.client-id",
            "member.oauth.x.client-secret",
            "member.oauth.x.scope",
            "member.oauth.x.redirect-uri",
            "member.oauth.gitee.enabled",
            "member.oauth.gitee.client-id",
            "member.oauth.gitee.client-secret",
            "member.oauth.gitee.scope",
            "member.oauth.gitee.redirect-uri",
            "member.oauth.wechat.enabled",
            "member.oauth.wechat.app-id",
            "member.oauth.wechat.app-secret",
            "member.oauth.wechat.scope",
            "member.oauth.wechat.redirect-uri",
            "member.oauth.qq.enabled",
            "member.oauth.qq.app-id",
            "member.oauth.qq.app-key",
            "member.oauth.qq.scope",
            "member.oauth.qq.redirect-uri");

    private static final Set<String> CURRENT_SYSTEM_CONFIG_KEYS = Set.of(
            "normalization.timeout.processing-min",
            "normalization.timeout.pending-min",
            "normalization.max-retries",
            "normalization.backoff.base-seconds",
            "normalization.batch-size",
            "normalization.batch-size.min",
            "normalization.batch-size.max",
            "app.normalization.valkey-worker.batch-size",
            "app.normalization.valkey-worker.visibility-timeout",
            "app.normalization.valkey-worker.initial-delay-ms",
            "app.normalization.valkey-worker.fixed-delay-ms",
            "app.metadata.timeout.processing-min",
            "app.metadata.timeout.pending-min",
            "app.metadata.cron",
            "app.metadata.interval.ms",
            "app.metadata.batch-size",
            "app.metadata.batch-size.min",
            "app.metadata.batch-size.max",
            "app.search.sync.batch-size",
            "app.frontend.url",
            "security.admin.username",
            "security.admin.password",
            "security.jwt.secret",
            "security.jwt.iat-floor",
            "security.ratelimit.enabled",
            "security.ratelimit.rules",
            "member.auth.code.validity-seconds",
            "member.auth.code.rate-limit-seconds",
            "member.register.email-verify.enabled",
            "member.register.time-window.enabled",
            "member.register.time-window.start",
            "member.register.time-window.end",
            "member.register.timezone",
            "member.message.daily-limit",
            "app.mail.enabled",
            "app.mail.host",
            "app.mail.port",
            "app.mail.protocol",
            "app.mail.from",
            "app.mail.properties.auth",
            "app.mail.properties.starttls",
            "app.mail.properties.ssl",
            "app.mail.timeout",
            "app.mail.debug",
            "app.mail.rate-limit.min-ms",
            "app.mail.rate-limit.max-ms",
            "app.mail.rate-limit.max-wait-ms",
            "app.adaptive.enabled",
            "app.adaptive.load-threshold",
            "app.adaptive.check-interval-ms",
            "app.aggregation.work-queue.batch-size",
            "app.aggregation.work-queue.batch-size.min",
            "app.aggregation.work-queue.batch-size.max",
            "app.aggregation.work-queue.worker.batch-size",
            "app.aggregation.work-queue.worker.visibility-seconds",
            "app.aggregation.work-queue.worker.max-retries",
            "app.aggregation.work-queue.worker.max-backoff-seconds",
            "app.aggregation.work-queue.worker.initial-delay-ms",
            "app.aggregation.work-queue.worker.fixed-delay-ms",
            "app.maintenance.sanitize.batch-size",
            "app.maintenance.aggregation-reset.batch-size",
            "app.maintenance.rule-cleaning.batch-size",
            "app.maintenance.search.reindex-batch-size",
            "app.maintenance.batch-size.min",
            "app.maintenance.batch-size.max",
            "app.maintenance.playlist-repair.enabled",
            "app.maintenance.playlist-repair.batch-size",
            "app.maintenance.playlist-repair.batch-size.min",
            "app.maintenance.playlist-repair.batch-size.max",
            "app.search.work-queue.batch-size",
            "app.search.work-queue.batch-size.min",
            "app.search.work-queue.batch-size.max",
            "app.search.work-queue.worker.enabled",
            "app.search.work-queue.worker.batch-size",
            "app.search.work-queue.worker.visibility-seconds",
            "app.search.work-queue.worker.max-retries",
            "app.search.work-queue.worker.max-backoff-seconds",
            "app.search.work-queue.worker.initial-delay-ms",
            "app.search.work-queue.worker.fixed-delay-ms",
            "app.search.audit-es-replication.worker.enabled",
            "app.search.audit-es-replication.worker.batch-size",
            "app.search.audit-es-replication.worker.visibility-seconds",
            "app.search.audit-es-replication.worker.max-retries",
            "app.search.audit-es-replication.worker.max-backoff-seconds",
            "app.search.audit-es-replication.worker.initial-delay-ms",
            "app.search.audit-es-replication.worker.fixed-delay-ms",
            "app.search.reconciliation.enabled",
            "app.storage.base-path",
            "app.storage.public-path",
            "app.storage.path.prefix.cover",
            "app.storage.path.prefix.avatar",
            "app.maintenance.queue-retention-days",
            "app.storage.image.download.enabled",
            "app.storage.image.max-retries",
            "app.storage.image.timeout-min",
            "app.storage.image.download.batch-size",
            "app.storage.image.download.batch-size.min",
            "app.storage.image.download.batch-size.max",
            "app.storage.image.cleanup.batch-size",
            "app.storage.image.cleanup.batch-size.min",
            "app.storage.image.cleanup.batch-size.max",
            "app.storage.r2.work-queue.worker.enabled",
            "app.storage.r2.work-queue.worker.batch-size",
            "app.storage.r2.work-queue.worker.visibility-seconds",
            "app.storage.r2.work-queue.worker.max-retries",
            "app.storage.r2.work-queue.worker.max-backoff-seconds",
            "app.storage.r2.work-queue.worker.initial-delay-ms",
            "app.storage.r2.work-queue.worker.fixed-delay-ms",
            "app.storage.r2.enabled",
            "app.storage.r2.bucket-name",
            "app.storage.r2.public-domain",
            "app.ai.llm.enabled",
            "app.ai.llm.model",
            "app.ai.llm.rate-limit.rpm",
            "app.normalization.llm-cleaning.work-queue.worker.enabled",
            "app.normalization.llm-cleaning.work-queue.worker.batch-size",
            "app.normalization.llm-cleaning.work-queue.worker.visibility-seconds",
            "app.normalization.llm-cleaning.work-queue.worker.max-retries",
            "app.normalization.llm-cleaning.work-queue.worker.max-backoff-seconds",
            "app.normalization.llm-cleaning.work-queue.worker.initial-delay-ms",
            "app.normalization.llm-cleaning.work-queue.worker.fixed-delay-ms",
            "app.ai.llm.prompt.language",
            "app.ai.llm.prompt.area",
            "app.ai.llm.prompt.genre",
            "app.ai.llm.prompt.category",
            "global.traffic.min-delay-ms",
            "global.traffic.max-delay-ms",
            "global.traffic.safety-floor-ms",
            "global.traffic.max-wait-ms",
            "app.scraper.traffic.global-enabled",
            "app.scraper.traffic.source-enabled",
            "app.metadata.public-traffic.enabled",
            "app.metadata.public-traffic.default-gap-ms",
            "app.metadata.public-traffic.max-wait",
            "app.metadata.public-traffic.ttl",
            "app.metadata.tmdb.enabled",
            "app.metadata.tmdb.min-delay-ms",
            "app.metadata.tmdb.max-delay-ms",
            "app.metadata.tmdb.proxy.enabled",
            "app.metadata.tmdb.proxy.type",
            "app.metadata.tmdb.proxy.host",
            "app.metadata.tmdb.proxy.port",
            "app.metadata.tmdb.proxy.username",
            "app.metadata.tmdb.proxy.password",
            "app.metadata.douban.enabled",
            "app.metadata.douban.min-delay-ms",
            "app.metadata.douban.max-delay-ms",
            "app.metadata.douban.proxy.enabled",
            "app.metadata.douban.proxy.type",
            "app.metadata.douban.proxy.host",
            "app.metadata.douban.proxy.port",
            "app.metadata.douban.proxy.username",
            "app.metadata.douban.proxy.password",
            "app.metadata.rotten-tomatoes.enabled",
            "app.metadata.rotten-tomatoes.min-delay-ms",
            "app.metadata.rotten-tomatoes.max-delay-ms",
            "app.metadata.rotten-tomatoes.proxy.enabled",
            "app.metadata.rotten-tomatoes.proxy.type",
            "app.metadata.rotten-tomatoes.proxy.host",
            "app.metadata.rotten-tomatoes.proxy.port",
            "app.metadata.rotten-tomatoes.proxy.username",
            "app.metadata.rotten-tomatoes.proxy.password",
            "app.metadata.valkey-dispatcher.batch-size",
            "app.metadata.valkey-dispatcher.visibility-timeout",
            "app.metadata.valkey-dispatcher.retry-delay",
            "app.metadata.valkey-dispatcher.initial-delay-ms",
            "app.metadata.valkey-dispatcher.fixed-delay-ms",
            "app.metadata.valkey-provider-worker.batch-size",
            "app.metadata.valkey-provider-worker.parallelism",
            "app.metadata.valkey-provider-worker.visibility-timeout",
            "app.metadata.valkey-provider-worker.retry-delay",
            "app.metadata.valkey-provider-worker.initial-delay-ms",
            "app.metadata.valkey-provider-worker.fixed-delay-ms",
            "app.scraper.trend-sync.enabled",
            "app.scraper.trend-sync.cron",
            "app.scraper.trend-sync.timezone",
            "app.search.sync.enabled",
            "app.search.sync.cron");
}
