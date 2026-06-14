package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataPendingRegistry {

    private static final String PREFIX = "enrich:pending:";
    private static final DefaultRedisScript<Long> REGISTER_SCRIPT = new DefaultRedisScript<>(
            """
            for i = 1, #ARGV - 1 do
              redis.call('SADD', KEYS[1], ARGV[i])
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[#ARGV])
            return redis.call('SCARD', KEYS[1])
            """,
            Long.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return -1
            end
            redis.call('SREM', KEYS[1], ARGV[1])
            return redis.call('SCARD', KEYS[1])
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public void register(UUID videoId, List<ProviderType> providers, Duration ttl) {
        if (providers.isEmpty()) {
            return;
        }
        if (ttl == null || !ttl.isPositive()) {
            throw new IllegalArgumentException("metadata pending registry ttl must be positive");
        }
        String key = key(videoId);
        List<String> args = new ArrayList<>(providers.size() + 1);
        providers.stream()
                .map(Enum::name)
                .forEach(args::add);
        args.add(String.valueOf(ttl.toMillis()));
        redisTemplate.execute(REGISTER_SCRIPT, Collections.singletonList(key), args.toArray());
    }

    public PendingCompletion complete(UUID videoId, ProviderType providerType) {
        if (providerType == null) {
            return PendingCompletion.untracked();
        }
        String key = key(videoId);
        Long remaining = redisTemplate.execute(COMPLETE_SCRIPT, Collections.singletonList(key), providerType.name());
        if (remaining == null || remaining < 0) {
            return PendingCompletion.untracked();
        }
        return new PendingCompletion(true, remaining);
    }

    public void clear(UUID videoId) {
        redisTemplate.delete(key(videoId));
    }

    public String key(UUID videoId) {
        return PREFIX + videoId;
    }

    public record PendingCompletion(boolean tracked, long remaining) {
        public static PendingCompletion untracked() {
            return new PendingCompletion(false, 0);
        }
    }
}
