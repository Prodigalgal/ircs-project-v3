package com.prodigalgal.ircs.ops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TrafficMonitorService {

    private static final String PREFIX = "traffic:limit:";
    private static final String TMDB_PROVIDER = "TMDB";
    private static final String TMDB_API_KEY_PAYLOAD = "api_key";
    private static final int MAX_CONFIGURED_CREDENTIALS = 100;
    private static final long MAX_BLOCKING_TIME_MS = 30_000L;

    private final StringRedisTemplate redisTemplate;
    private final CredentialLabelRepository credentialLabelRepository;

    public TrafficStatusResponse getTrafficStatus() {
        List<TrafficSlotResponse> globals = new ArrayList<>();
        Map<String, TrafficSlotResponse> credentialSlots = new LinkedHashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match(PREFIX + "*").count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String fullKey = cursor.next();
                if (fullKey == null || !fullKey.startsWith(PREFIX)) {
                    continue;
                }
                String rawKey = fullKey.substring(PREFIX.length());
                TrafficSlotResponse slot = buildSlot(rawKey, fullKey);
                if (slot == null) {
                    continue;
                }
                if (rawKey.startsWith("cred:")) {
                    credentialSlots.put(slot.key(), slot);
                } else {
                    globals.add(slot);
                }
            }
        } catch (RuntimeException ignored) {
            return new TrafficStatusResponse(List.of(), List.of());
        }

        addConfiguredCredentialSlots(credentialSlots);
        List<TrafficSlotResponse> credentials = new ArrayList<>(credentialSlots.values());
        globals.sort(Comparator.comparing(TrafficSlotResponse::key));
        credentials.sort(Comparator.comparing(TrafficSlotResponse::key));
        return new TrafficStatusResponse(globals, credentials);
    }

    public void resetKey(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        redisTemplate.delete(PREFIX + key.trim());
    }

    private TrafficSlotResponse buildSlot(String rawKey, String redisKey) {
        DataType type = redisType(redisKey);
        if (DataType.HASH.equals(type)) {
            return buildTokenBucketSlot(rawKey, redisKey);
        }
        if (type != null && !DataType.STRING.equals(type) && !DataType.NONE.equals(type)) {
            return null;
        }
        return buildTimeSliceSlot(rawKey, redisKey);
    }

    private TrafficSlotResponse buildTimeSliceSlot(String rawKey, String redisKey) {
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null || value.isBlank()) {
            return null;
        }

        long scheduledTime;
        try {
            scheduledTime = Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }

        long now = System.currentTimeMillis();
        long waitTime = Math.max(0L, scheduledTime - now);
        if (waitTime == 0L && scheduledTime < now - 60_000L) {
            return null;
        }

        double congestion = Math.round(((double) waitTime / MAX_BLOCKING_TIME_MS) * 100.0) / 100.0;
        return new TrafficSlotResponse(
                rawKey,
                resolveLabel(rawKey),
                waitTime / 1000L,
                waitTime,
                congestion,
                waitTime > MAX_BLOCKING_TIME_MS,
                "TIME_SLICE",
                null,
                null);
    }

    private TrafficSlotResponse buildTokenBucketSlot(String rawKey, String redisKey) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(redisKey);
        if (values == null || values.isEmpty()) {
            return null;
        }
        long remaining = parseLong(values.get("tokens"), -1L);
        long capacity = parseLong(values.get("capacity"), 0L);
        long permits = Math.max(1L, parseLong(values.get("permits"), 1L));
        long retryAfterMs = Math.max(0L, parseLong(values.get("retry_after_ms"), 0L));
        if (remaining < 0L && capacity <= 0L) {
            return null;
        }

        long safeRemaining = Math.max(0L, remaining);
        double congestion = capacity <= 0L
                ? 0.0
                : Math.round(((double) (capacity - Math.min(capacity, safeRemaining)) / capacity) * 100.0) / 100.0;
        long waitingTasks = safeRemaining >= permits ? 0L : permits - safeRemaining;
        return new TrafficSlotResponse(
                rawKey,
                resolveLabel(rawKey),
                waitingTasks,
                retryAfterMs,
                congestion,
                retryAfterMs > MAX_BLOCKING_TIME_MS,
                "TOKEN_BUCKET",
                safeRemaining,
                capacity > 0L ? capacity : null);
    }

    private DataType redisType(String redisKey) {
        try {
            return redisTemplate.type(redisKey);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveLabel(String rawKey) {
        if (rawKey.startsWith("cred:")) {
            return resolveCredentialLabel(rawKey.substring(5));
        }
        if (rawKey.startsWith("Provider:Ip:")) {
            return resolveTrailingIpLabel(rawKey.substring(12), "全局锁");
        }
        if (rawKey.startsWith("Provider:")) {
            return "全局锁: " + rawKey.substring(9);
        }
        if (rawKey.startsWith("Global:")) {
            return resolveGlobalLabel(rawKey.substring(7));
        }
        if (rawKey.startsWith("DataSource:Ip:")) {
            return resolveTrailingIpLabel(rawKey.substring(14), "数据源锁");
        }
        if (rawKey.startsWith("DataSource:")) {
            return "数据源锁: " + rawKey.substring(11);
        }
        if (rawKey.startsWith("Domain:Ip:")) {
            return resolveTrailingIpLabel(rawKey.substring(10), "域名锁");
        }
        if (rawKey.startsWith("Domain:")) {
            return "域名锁: " + rawKey.substring(7);
        }
        return rawKey;
    }

    private String resolveGlobalLabel(String value) {
        int marker = value.indexOf(":Ip:");
        if (marker < 1 || marker >= value.length() - 4) {
            return "全局锁: " + value;
        }
        String name = value.substring(0, marker);
        String ip = value.substring(marker + 4);
        return "全局锁: " + name + " / IP " + ip;
    }

    private String resolveTrailingIpLabel(String value, String prefix) {
        int delimiter = value.lastIndexOf(':');
        if (delimiter < 1 || delimiter >= value.length() - 1) {
            return prefix + ": " + value;
        }
        String ip = value.substring(0, delimiter);
        String target = value.substring(delimiter + 1);
        return prefix + ": " + target + " / IP " + ip;
    }

    private String resolveCredentialLabel(String idText) {
        try {
            UUID id = UUID.fromString(idText);
            return credentialLabelRepository.findLabel(id)
                    .orElse("Unknown Credential (" + idText + ")");
        } catch (RuntimeException ex) {
            return "Invalid ID: " + idText;
        }
    }

    private void addConfiguredCredentialSlots(Map<String, TrafficSlotResponse> credentialSlots) {
        List<CredentialLabelRepository.CredentialTrafficLabel> labels;
        try {
            labels = credentialLabelRepository.findEnabledProviderLabels(
                    TMDB_PROVIDER,
                    TMDB_API_KEY_PAYLOAD,
                    MAX_CONFIGURED_CREDENTIALS);
        } catch (RuntimeException ignored) {
            return;
        }
        if (labels == null || labels.isEmpty()) {
            return;
        }
        for (CredentialLabelRepository.CredentialTrafficLabel label : labels) {
            if (label == null || label.id() == null) {
                continue;
            }
            String key = "cred:" + label.id();
            credentialSlots.putIfAbsent(key, idleCredentialSlot(key, label));
        }
    }

    private TrafficSlotResponse idleCredentialSlot(
            String key,
            CredentialLabelRepository.CredentialTrafficLabel label) {
        Long capacity = label.rateLimit() == null || label.rateLimit() <= 0
                ? null
                : label.rateLimit().longValue();
        return new TrafficSlotResponse(
                key,
                label.label(),
                0L,
                0L,
                0.0,
                false,
                "TOKEN_BUCKET",
                capacity,
                capacity);
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
