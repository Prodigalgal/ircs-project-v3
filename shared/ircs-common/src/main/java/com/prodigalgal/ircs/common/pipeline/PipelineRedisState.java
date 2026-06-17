package com.prodigalgal.ircs.common.pipeline;

import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class PipelineRedisState {

    private static final String PREFIX = "ircs:pipeline:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Autowired
    public PipelineRedisState(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    PipelineRedisState(StringRedisTemplate redisTemplate, Clock clock) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate is required");
        }
        this.redisTemplate = redisTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Optional<PipelineLease> tryLease(
            PipelineStep step,
            UUID rawVideoId,
            String pipelineVersion,
            String ownerId,
            Duration ttl) {
        if (step == null || rawVideoId == null) {
            throw new IllegalArgumentException("pipeline step and rawVideoId are required");
        }
        String normalizedOwner = StringUtils.hasText(ownerId) ? ownerId.trim() : "worker";
        Duration normalizedTtl = normalizeTtl(ttl);
        Instant acquiredAt = Instant.now(clock);
        String version = normalizeVersion(pipelineVersion);
        String token = normalizedOwner + ":" + UUID.randomUUID();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(leaseKey(step, rawVideoId, version), token, normalizedTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        return Optional.of(new PipelineLease(
                step,
                rawVideoId,
                version,
                normalizedOwner,
                token,
                acquiredAt,
                acquiredAt.plus(normalizedTtl)));
    }

    public void markComplete(PipelineStep step, UUID rawVideoId, String pipelineVersion, Duration ttl) {
        redisTemplate.opsForValue().set(
                completeKey(step, rawVideoId, normalizeVersion(pipelineVersion)),
                Long.toString(Instant.now(clock).toEpochMilli()),
                normalizeTtl(ttl));
    }

    public boolean release(PipelineLease lease) {
        if (lease == null) {
            return false;
        }
        Long released = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(leaseKey(lease.step(), lease.rawVideoId(), lease.pipelineVersion())),
                lease.token());
        return Long.valueOf(1L).equals(released);
    }

    private String leaseKey(PipelineStep step, UUID rawVideoId, String pipelineVersion) {
        return PREFIX + "lease:" + step.name() + ":" + rawVideoId + ":" + normalizeVersion(pipelineVersion);
    }

    private String completeKey(PipelineStep step, UUID rawVideoId, String pipelineVersion) {
        return PREFIX + "complete:" + step.name() + ":" + rawVideoId + ":" + normalizeVersion(pipelineVersion);
    }

    private static String normalizeVersion(String pipelineVersion) {
        return StringUtils.hasText(pipelineVersion) ? pipelineVersion.trim() : "_";
    }

    private static Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("pipeline ttl must be positive");
        }
        return ttl;
    }
}
