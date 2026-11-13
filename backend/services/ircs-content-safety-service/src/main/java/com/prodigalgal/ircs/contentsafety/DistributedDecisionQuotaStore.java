package com.prodigalgal.ircs.contentsafety;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class DistributedDecisionQuotaStore implements DecisionQuotaStore {
    static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    static final String PREFIX = "ircs:decision-quota:{google-decisions}:";
    // TIME is authoritative for rolling windows. The supplied calendar interval must contain TIME.
    static final String RESERVE = """
            local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000)
            local begin=tonumber(ARGV[3]); local ending=tonumber(ARGV[4])
            if now<begin or now>=ending then return {4,60} end
            local cooldown=tonumber(redis.call('GET',KEYS[4]) or '0')
            if cooldown>now then return {3,math.ceil((cooldown-now)/1000)} end
            local old=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',now-60000)
            for _,id in ipairs(old) do redis.call('HDEL',KEYS[2],id) end
            redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',now-60000)
            local tokens=0
            for _,v in ipairs(redis.call('HVALS',KEYS[2])) do tokens=tokens+tonumber(v) end
            local rpm=redis.call('ZCARD',KEYS[1]); local day=tonumber(redis.call('GET',KEYS[3]) or '0')
            if day>=10000 then return {2,math.ceil((ending-now)/1000)} end
            if rpm>=200 then
              local oldest=redis.call('ZRANGE',KEYS[1],0,0,'WITHSCORES')
              return {1,math.max(1,math.ceil((tonumber(oldest[2])+60000-now)/1000))}
            end
            if tokens+tonumber(ARGV[2])>50000 then
              local required=tokens+tonumber(ARGV[2])-50000; local released=0
              local ordered=redis.call('ZRANGE',KEYS[1],0,-1,'WITHSCORES')
              for i=1,#ordered,2 do
                released=released+tonumber(redis.call('HGET',KEYS[2],ordered[i]) or '0')
                if released>=required then
                  return {5,math.max(1,math.ceil((tonumber(ordered[i+1])+60000-now)/1000))}
                end
              end
              return {5,60}
            end
            if redis.call('EXISTS',KEYS[5])==1 then return {4,60} end
            redis.call('ZADD',KEYS[1],now,ARGV[1]); redis.call('HSET',KEYS[2],ARGV[1],ARGV[2])
            redis.call('PEXPIRE',KEYS[1],120000); redis.call('PEXPIRE',KEYS[2],120000)
            redis.call('INCR',KEYS[3]); redis.call('PEXPIREAT',KEYS[3],ending+172800000)
            redis.call('SET',KEYS[5],'reserved','PX',172800000)
            return {0,0}
            """;
    static final String RECONCILE = """
            if redis.call('GET',KEYS[3])~='sent' then return 0 end
            if redis.call('HEXISTS',KEYS[2],ARGV[1])==1 then
                redis.call('HSET',KEYS[2],ARGV[1],ARGV[2])
            end
            redis.call('SET',KEYS[3],'reconciled','PX',172800000)
            return 1
            """;
    static final String CANCEL = """
            if redis.call('GET',KEYS[4])~='reserved' then return 0 end
            redis.call('ZREM',KEYS[1],ARGV[1]); redis.call('HDEL',KEYS[2],ARGV[1])
            if tonumber(redis.call('GET',KEYS[3]) or '0')>0 then redis.call('DECR',KEYS[3]) end
            redis.call('SET',KEYS[4],'cancelled','PX',172800000)
            return 1
            """;
    static final String COOLDOWN = """
            local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000)
            local untilMs=math.max(tonumber(redis.call('GET',KEYS[1]) or '0'),tonumber(ARGV[1]))
            if untilMs>now then redis.call('SET',KEYS[1],untilMs,'PX',math.ceil(untilMs-now)) end
            return 1
            """;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String prefix;

    DistributedDecisionQuotaStore(StringRedisTemplate redis, Clock clock, String prefix) {
        this.redis = redis; this.clock = clock; this.prefix = prefix;
    }

    static Instant dayStart(Instant instant) { return instant.atZone(PACIFIC).toLocalDate().atStartOfDay(PACIFIC).toInstant(); }
    static Instant nextDay(Instant instant) { return instant.atZone(PACIFIC).toLocalDate().plusDays(1).atStartOfDay(PACIFIC).toInstant(); }

    @Override
    public Reservation reserve(long estimate) {
        if (estimate < 1 || estimate > 50000) { throw new DecisionUnavailableException("REQUEST_TOO_LARGE", 60); }
        Instant now = clock.instant();
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), LocalDate.ofInstant(now, PACIFIC).toString(), estimate);
        try {
            List<?> result = redis.execute(new DefaultRedisScript<>(RESERVE, List.class),
                    List.of(prefix+"minute", prefix+"amounts", dayKey(reservation), prefix+"cooldown", receipt(reservation)),
                    reservation.id(), Long.toString(estimate), Long.toString(dayStart(now).toEpochMilli()), Long.toString(nextDay(now).toEpochMilli()));
            if (result == null || result.size() != 2) { throw new IllegalStateException("Missing quota result"); }
            long status = ((Number) result.get(0)).longValue();
            if (status != 0) {
                throw new DecisionUnavailableException(switch ((int) status) {
                    case 1 -> "RATE_LIMITED"; case 2 -> "DAILY_LIMIT"; case 3 -> "COOLDOWN"; case 5 -> "TOKEN_LIMITED";
                    default -> "QUOTA_UNAVAILABLE";
                }, ((Number) result.get(1)).longValue());
            }
            return reservation;
        } catch (DecisionUnavailableException ex) { throw ex;
        } catch (RuntimeException ex) { throw new DecisionUnavailableException("QUOTA_UNAVAILABLE", 60); }
    }

    @Override
    public void beginSend(Reservation reservation) {
        try {
            List<?> result = redis.execute(new DefaultRedisScript<>("""
                    local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000)
                    local untilMs=tonumber(redis.call('GET',KEYS[1]) or '0')
                    if untilMs>now then return {1,math.ceil((untilMs-now)/1000)} end
                    if redis.call('GET',KEYS[3])~='reserved' then return {3,60} end
                    if now<tonumber(ARGV[2]) or now>=tonumber(ARGV[3]) then return {2,2} end
                    local created=tonumber(redis.call('ZSCORE',KEYS[2],ARGV[1]) or '0')
                    if created<=now-60000 then return {2,60} end
                    redis.call('ZADD',KEYS[2],now,ARGV[1]); redis.call('PEXPIRE',KEYS[2],120000)
                    redis.call('PEXPIRE',KEYS[4],120000)
                    redis.call('SET',KEYS[3],'sent','PX',172800000)
                    return {0,0}
                    """, List.class), List.of(prefix+"cooldown", prefix+"minute", receipt(reservation), prefix+"amounts"), reservation.id(),
                    Long.toString(LocalDate.parse(reservation.day()).atStartOfDay(PACIFIC).toInstant().toEpochMilli()),
                    Long.toString(LocalDate.parse(reservation.day()).plusDays(1).atStartOfDay(PACIFIC).toInstant().toEpochMilli()));
            if (result == null || result.size() != 2) { throw new IllegalStateException("Missing dispatch fence result"); }
            int status = ((Number) result.getFirst()).intValue();
            if (status != 0) { throw new DecisionUnavailableException(status == 1 ? "COOLDOWN" : status == 2 ? "RATE_LIMITED" : "QUOTA_UNAVAILABLE",
                    ((Number) result.get(1)).longValue()); }
        } catch (DecisionUnavailableException ex) { throw ex;
        } catch (RuntimeException ex) { throw new DecisionUnavailableException("QUOTA_UNAVAILABLE", 60); }
    }

    @Override
    public void reconcile(Reservation reservation, long actual) {
        if (actual < 0) { throw new DecisionUnavailableException("INVALID_USAGE", 60); }
        execute(RECONCILE, List.of(prefix+"minute", prefix+"amounts", receipt(reservation)), reservation.id(), Long.toString(actual));
    }
    @Override
    public void cancelBeforeSend(Reservation reservation) {
        execute(CANCEL, List.of(prefix+"minute", prefix+"amounts", dayKey(reservation), receipt(reservation)), reservation.id());
    }
    @Override
    public void coolDown(Instant until) { execute(COOLDOWN, List.of(prefix+"cooldown"), Long.toString(until.toEpochMilli())); }

    private void execute(String script, List<String> keys, String... args) {
        try {
            Long result = redis.execute(new DefaultRedisScript<>(script, Long.class), keys, (Object[]) args);
            if (result == null) { throw new IllegalStateException("Missing quota result"); }
        } catch (RuntimeException ex) { throw new DecisionUnavailableException("QUOTA_UNAVAILABLE", 60); }
    }
    private String dayKey(Reservation r) { return prefix+"day:"+r.day(); }
    private String receipt(Reservation r) { return prefix+"reservation:"+r.id(); }
}
