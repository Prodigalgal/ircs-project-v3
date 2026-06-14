package com.prodigalgal.ircs.credential;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CredentialProviderCatalog {

    static final String DEFAULT_OPENAI_BASE_URL = "https://ai.mnnu.eu.org/v1";

    public List<CredentialTemplateField> templates(String provider) {
        return switch (normalizeProvider(provider)) {
            case "OPENAI" -> List.of(
                    field("api_key", "OpenAI API Key", "password", true, "sk-...", null,
                            "支持 OpenAI / OneAPI / NewAPI 等兼容 OpenAI API 的密钥。"),
                    field("base_url", "Base URL", "text", true, DEFAULT_OPENAI_BASE_URL, DEFAULT_OPENAI_BASE_URL,
                            "填写网关根路径，例如 https://api.openai.com/v1；不要追加 /chat/completions。"));
            case "TMDB" -> List.of(
                    field("api_key", "TMDB API Key", "text", true, "v3 API Key (32 chars)", null, null));
            case "DOUBAN" -> List.of(
                    field("cookie", "Douban Cookie", "password", false, "可选，用于低频只读查询", null,
                            "为空时 Douban provider 仅使用公开 suggest endpoint。"),
                    field("user_agent", "User-Agent", "text", false, "可选浏览器 UA", null,
                            "为空时使用 metadata-worker 默认 User-Agent。"));
            case "ROTTEN_TOMATOES" -> List.of(
                    field("cookie", "Rotten Tomatoes Cookie", "password", false, "可选，用于低频只读查询", null,
                            "为空时 RT provider 仅使用公开 search 页面。"),
                    field("user_agent", "User-Agent", "text", false, "可选浏览器 UA", null,
                            "为空时使用 metadata-worker 默认 User-Agent。"));
            case "MAIL" -> List.of(
                    field("username", "邮箱账号", "text", true, "e.g. yourname@gmail.com", null,
                            "SMTP 服务器配置请在【系统设置】中统一管理"),
                    field("password", "邮箱密码/授权码", "password", true, "App Password or Password", null, null));
            case "R2" -> List.of(
                    field("account_id", "Cloudflare Account ID", "text", true, null, null, null),
                    field("access_key", "Access Key ID", "text", true, null, null, null),
                    field("secret_key", "Secret Access Key", "password", true, null, null, null));
            default -> throw new IllegalArgumentException("Provider not supported");
        };
    }

    public void validate(CredentialWriteRequest request) {
        Map<String, Object> payload = request.payload();
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        switch (normalizeProvider(request.provider())) {
            case "OPENAI" -> validateOpenAi(payload);
            case "TMDB" -> requireText(payload, "api_key", "API Key is required");
            case "DOUBAN" -> validateDouban(payload);
            case "ROTTEN_TOMATOES" -> validateRottenTomatoes(payload);
            case "MAIL" -> {
                requireText(payload, "username", "Username is required");
                requireText(payload, "password", "Password is required");
            }
            case "R2" -> {
                requireText(payload, "account_id", "Account ID required");
                requireText(payload, "access_key", "Access Key required");
                requireText(payload, "secret_key", "Secret Key required");
            }
            default -> throw new IllegalArgumentException("Provider not supported");
        }
        normalizeRateLimitUnit(request.rateLimitUnit());
    }

    public String fingerprintSource(String provider, Map<String, Object> payload) {
        return switch (normalizeProvider(provider)) {
            case "OPENAI" -> resolveOpenAiBaseUrl(payload) + "\n" + requireText(payload, "api_key", "API Key is required");
            case "TMDB" -> requireText(payload, "api_key", "API Key is required");
            case "DOUBAN" -> optionalText(payload, "cookie") + "\n" + optionalText(payload, "user_agent");
            case "ROTTEN_TOMATOES" -> optionalText(payload, "cookie") + "\n" + optionalText(payload, "user_agent");
            case "MAIL" -> requireText(payload, "username", "Username is required") + "|"
                    + requireText(payload, "password", "Password is required");
            case "R2" -> requireText(payload, "account_id", "Account ID required") + "|"
                    + requireText(payload, "access_key", "Access Key required") + "|"
                    + requireText(payload, "secret_key", "Secret Key required");
            default -> throw new IllegalArgumentException("Provider not supported");
        };
    }

    public String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeRateLimitUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        String normalized = unit.trim().toUpperCase(Locale.ROOT);
        if (!"SECOND".equals(normalized) && !"MINUTE".equals(normalized)) {
            throw new IllegalArgumentException("Rate limit unit must be SECOND or MINUTE");
        }
        return normalized;
    }

    private CredentialTemplateField field(
            String key,
            String label,
            String type,
            boolean required,
            String placeholder,
            Object defaultValue,
            String helpText) {
        return new CredentialTemplateField(key, label, type, required, placeholder, defaultValue, helpText, null);
    }

    private void validateOpenAi(Map<String, Object> payload) {
        requireText(payload, "api_key", "API Key is required");
        try {
            URI baseUrl = URI.create(resolveOpenAiBaseUrl(payload));
            String scheme = baseUrl.getScheme();
            if (!StringUtils.hasText(scheme)
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Base URL is invalid");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base URL is invalid", e);
        }
    }

    private void validateDouban(Map<String, Object> payload) {
        if (!StringUtils.hasText(optionalText(payload, "cookie"))
                && !StringUtils.hasText(optionalText(payload, "user_agent"))) {
            throw new IllegalArgumentException("Douban cookie or user_agent is required");
        }
    }

    private void validateRottenTomatoes(Map<String, Object> payload) {
        if (!StringUtils.hasText(optionalText(payload, "cookie"))
                && !StringUtils.hasText(optionalText(payload, "user_agent"))) {
            throw new IllegalArgumentException("Rotten Tomatoes cookie or user_agent is required");
        }
    }

    private String resolveOpenAiBaseUrl(Map<String, Object> payload) {
        Object value = payload.get("base_url");
        String normalized = StringUtils.hasText(value == null ? null : String.valueOf(value))
                ? String.valueOf(value).trim()
                : DEFAULT_OPENAI_BASE_URL;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized;
    }

    private String requireText(Map<String, Object> payload, String key, String message) {
        Object value = payload.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String optionalText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
