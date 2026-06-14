package com.prodigalgal.ircs.search;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.cache.CacheRegistry;
import com.prodigalgal.ircs.common.cache.TieredReadThroughCache;
import com.prodigalgal.ircs.common.cache.TieredStringCacheStore;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SearchPortalReadModelCache {

    static final String CACHE_PORTAL_SUGGEST = "search.portal.suggest.public";
    static final String CACHE_PORTAL_RECOMMEND = "search.portal.recommend.public";

    private static final Duration DEFAULT_SUGGEST_TTL = Duration.ofSeconds(60);
    private static final Duration DEFAULT_RECOMMEND_TTL = Duration.ofSeconds(60);

    private final boolean enabled;
    private final TieredReadThroughCache<String, List<String>> suggestCache;
    private final TieredReadThroughCache<String, RecommendationPage> recommendCache;
    SearchPortalReadModelCache(
            ObjectMapper objectMapper,
            CacheRegistry cacheRegistry,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            ObjectProvider<TieredStringCacheStore> externalStore,
            @Value("${app.search.cache.enabled:true}") boolean enabled,
            @Value("${app.search.cache.portal-suggest-ttl:PT60S}") Duration suggestTtl,
            @Value("${app.search.cache.portal-recommend-ttl:PT60S}") Duration recommendTtl) {
        ObjectMapper mapper = objectMapper == null
                ? JsonMapper.builder().findAndAddModules().build()
                : objectMapper;
        CacheRegistry registry = cacheRegistry == null ? new CacheRegistry() : cacheRegistry;
        TieredStringCacheStore store = store(redisTemplate, externalStore);
        this.enabled = enabled;
        this.suggestCache = register(registry, new TieredReadThroughCache<>(
                CACHE_PORTAL_SUGGEST,
                ttl(suggestTtl, DEFAULT_SUGGEST_TTL),
                "ircs:cache:" + CACHE_PORTAL_SUGGEST + ":",
                key -> key,
                mapper::writeValueAsString,
                raw -> mapper.readValue(raw, stringListType(mapper)),
                store));
        this.recommendCache = register(registry, new TieredReadThroughCache<>(
                CACHE_PORTAL_RECOMMEND,
                ttl(recommendTtl, DEFAULT_RECOMMEND_TTL),
                "ircs:cache:" + CACHE_PORTAL_RECOMMEND + ":",
                key -> key,
                mapper::writeValueAsString,
                raw -> mapper.readValue(raw, RecommendationPage.class),
                store));
    }

    static SearchPortalReadModelCache disabled() {
        return new SearchPortalReadModelCache(
                JsonMapper.builder().findAndAddModules().build(),
                new CacheRegistry(),
                null,
                null,
                false,
                DEFAULT_SUGGEST_TTL,
                DEFAULT_RECOMMEND_TTL);
    }

    List<String> suggest(
            IrcsRequestPrincipal principal,
            String safeKeyword,
            Supplier<List<String>> loader) {
        if (!cacheablePublic(principal) || safeKeyword == null) {
            return loader.get();
        }
        List<String> result = suggestCache.get(token(safeKeyword), loader);
        return result == null ? List.of() : result;
    }

    Page<PortalMovieCardResponse> recommend(
            IrcsRequestPrincipal principal,
            UUID videoId,
            Pageable pageable,
            Supplier<Page<PortalMovieCardResponse>> loader) {
        if (!cacheablePublic(principal) || videoId == null || pageable == null) {
            return loader.get();
        }
        RecommendationPage cached = recommendCache.get(recommendKey(videoId, pageable), () -> RecommendationPage.from(loader.get()));
        return cached == null ? Page.empty(pageable) : cached.toPage(pageable);
    }

    public void evictPortalPublicReadModel() {
        suggestCache.evictAll();
        recommendCache.evictAll();
    }

    private boolean cacheablePublic(IrcsRequestPrincipal principal) {
        return enabled && (principal == null || principal.isAnonymous());
    }

    private String recommendKey(UUID videoId, Pageable pageable) {
        return "%s|p=%d|s=%d|sort=%s".formatted(
                videoId,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                token(pageable.getSort().toString()));
    }

    private String token(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }

    private <T> TieredReadThroughCache<String, T> register(
            CacheRegistry registry,
            TieredReadThroughCache<String, T> cache) {
        registry.register(cache);
        return cache;
    }

    private static JavaType stringListType(ObjectMapper mapper) {
        return mapper.getTypeFactory().constructCollectionType(List.class, String.class);
    }

    private static Duration ttl(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static TieredStringCacheStore store(
            ObjectProvider<StringRedisTemplate> redisTemplate,
            ObjectProvider<TieredStringCacheStore> externalStore) {
        if (externalStore != null) {
            TieredStringCacheStore store = externalStore.getIfAvailable();
            if (store != null) {
                return store;
            }
        }
        if (redisTemplate == null) {
            return TieredStringCacheStore.noop();
        }
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template == null ? TieredStringCacheStore.noop() : new SearchRedisStringCacheStore(template);
    }

    record RecommendationPage(
            List<PortalMovieCardResponse> content,
            long totalElements,
            int pageNumber,
            int pageSize) {

        static RecommendationPage from(Page<PortalMovieCardResponse> page) {
            if (page == null) {
                return new RecommendationPage(List.of(), 0, 0, 20);
            }
            return new RecommendationPage(
                    page.getContent() == null ? List.of() : page.getContent(),
                    page.getTotalElements(),
                    page.getNumber(),
                    page.getSize());
        }

        Page<PortalMovieCardResponse> toPage(Pageable fallback) {
            Pageable pageable = fallback == null ? org.springframework.data.domain.PageRequest.of(pageNumber, pageSize) : fallback;
            return new PageImpl<>(content == null ? List.of() : content, pageable, totalElements);
        }
    }
}
