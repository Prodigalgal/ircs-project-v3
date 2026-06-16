package com.prodigalgal.ircs.ops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TrafficMonitorService {

    private static final String PREFIX = "traffic:limit:";
    private static final long MAX_BLOCKING_TIME_MS = 30_000L;

    private final StringRedisTemplate redisTemplate;
    private final CredentialLabelRepository credentialLabelRepository;

    public TrafficStatusResponse getTrafficStatus() {
        List<TrafficSlotResponse> globals = new ArrayList<>();
        List<TrafficSlotResponse> credentials = new ArrayList<>();
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
                    credentials.add(slot);
                } else {
                    globals.add(slot);
                }
            }
        } catch (RuntimeException ignored) {
            return new TrafficStatusResponse(List.of(), List.of());
        }

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
                waitTime > MAX_BLOCKING_TIME_MS);
    }

    private String resolveLabel(String rawKey) {
        if (rawKey.startsWith("cred:")) {
            return resolveCredentialLabel(rawKey.substring(5));
        }
        if (rawKey.startsWith("Provider:")) {
            return "全局锁: " + rawKey.substring(9);
        }
        if (rawKey.startsWith("Global:Scraper:Ip:")) {
            return "全局锁: Scraper / IP " + rawKey.substring(18);
        }
        if (rawKey.startsWith("Global:")) {
            return "全局锁: " + rawKey.substring(7);
        }
        if (rawKey.startsWith("DataSource:Ip:")) {
            return resolveDataSourceIpLabel(rawKey.substring(14));
        }
        if (rawKey.startsWith("DataSource:")) {
            return "数据源锁: " + rawKey.substring(11);
        }
        if (rawKey.startsWith("Domain:")) {
            return "域名锁: " + rawKey.substring(7);
        }
        return rawKey;
    }

    private String resolveDataSourceIpLabel(String value) {
        int delimiter = value.lastIndexOf(':');
        if (delimiter < 1 || delimiter >= value.length() - 1) {
            return "数据源锁: " + value;
        }
        String ip = value.substring(0, delimiter);
        String sourceId = value.substring(delimiter + 1);
        return "数据源锁: " + sourceId + " / IP " + ip;
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
}
