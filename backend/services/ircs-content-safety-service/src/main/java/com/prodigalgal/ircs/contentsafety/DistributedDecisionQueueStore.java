package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

class DistributedDecisionQueueStore implements DecisionQueueStore {
    static final String PREFIX = "ircs:decision-queue:{google-decisions}:";
    private static final String NOW = "local t=redis.call('TIME'); local now=t[1]*1000+math.floor(t[2]/1000)\n";
    static final String SUBMIT = NOW + """
            local capacityWait=math.max(0,tonumber(redis.call('GET',KEYS[5]) or '0')-now)
            local result=redis.call('GET',KEYS[4])
            if result then return {0,result,redis.call('PTTL',KEYS[4]),0} end
            local due=redis.call('ZSCORE',KEYS[1],ARGV[1])
            if redis.call('EXISTS',KEYS[3])==1 then
              local rank=redis.call('ZRANK',KEYS[1],ARGV[1]) or 0
              return {1,'',math.max(capacityWait,tonumber(due or now)-now),rank}
            end
            redis.call('ZREM',KEYS[1],ARGV[1]); redis.call('ZREM',KEYS[2],ARGV[1])
            if redis.call('ZCARD',KEYS[1])+redis.call('ZCARD',KEYS[2])>=tonumber(ARGV[3]) then return {3,'',math.max(2000,capacityWait),tonumber(ARGV[3])} end
            redis.call('HSET',KEYS[3],'payload',ARGV[2],'created',now,'state','queued')
            redis.call('PEXPIRE',KEYS[3],ARGV[4]); redis.call('ZADD',KEYS[1],now,ARGV[1])
            return {2,'',capacityWait,redis.call('ZRANK',KEYS[1],ARGV[1]) or 0}
            """;
    static final String READY = NOW + """
            local out={}
            local expired=redis.call('ZRANGEBYSCORE',KEYS[2],'-inf',now,'LIMIT',0,100)
            for _,id in ipairs(expired) do
              local job=ARGV[1]..'job:'..id
              if redis.call('HGET',job,'state')=='sent' then
                redis.call('SET',ARGV[1]..'result:'..id,ARGV[4],'PX',60000)
                redis.call('DEL',job)
              elseif redis.call('EXISTS',job)==1 then
                redis.call('HSET',job,'state','queued'); redis.call('HDEL',job,'owner')
                redis.call('ZADD',KEYS[1],now,id)
              end
              redis.call('ZREM',KEYS[2],id)
            end
            if tonumber(redis.call('GET',KEYS[3]) or '0')>now then return out end
            local ids=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',now,'LIMIT',0,tonumber(ARGV[2]))
            local oldest=now
            for _,id in ipairs(ids) do
              local job=ARGV[1]..'job:'..id; local payload=redis.call('HGET',job,'payload')
              if payload then
                local created=tonumber(redis.call('HGET',job,'created')); oldest=math.min(oldest,created)
                table.insert(out,id); table.insert(out,payload); table.insert(out,tostring(created))
              else redis.call('ZREM',KEYS[1],id) end
            end
            if #out/3<tonumber(ARGV[5]) and now-oldest<tonumber(ARGV[3]) then return {} end
            return out
            """;
    static final String CLAIM = NOW + """
            local out={}
            for i=3,#KEYS do
              local id=ARGV[i-1]; local due=tonumber(redis.call('ZSCORE',KEYS[1],id) or '-1')
              if due>=0 and due<=now and redis.call('HGET',KEYS[i],'state')=='queued' then
                redis.call('ZREM',KEYS[1],id); redis.call('ZADD',KEYS[2],now+60000,id)
                redis.call('HSET',KEYS[i],'state','claimed','owner',ARGV[1]); table.insert(out,id)
                redis.call('PEXPIRE',KEYS[i],math.max(120000,redis.call('PTTL',KEYS[i])))
              end
            end
            return out
            """;
    static final String SENT = NOW + """
            for i=2,#KEYS do
              if redis.call('HGET',KEYS[i],'owner')~=ARGV[1]
                or redis.call('HGET',KEYS[i],'state')~='claimed'
                or tonumber(redis.call('ZSCORE',KEYS[1],ARGV[i]) or '0')<=now then return 0 end
            end
            for i=2,#KEYS do redis.call('HSET',KEYS[i],'state','sent') end
            return 1
            """;
    static final String COMPLETE = NOW + """
            if redis.call('HGET',KEYS[3],'owner')~=ARGV[1] then return 0 end
            local state=redis.call('HGET',KEYS[3],'state')
            if state~='claimed' and state~='sent' then return 0 end
            if tonumber(redis.call('ZSCORE',KEYS[2],ARGV[2]) or '0')<=now then return 0 end
            redis.call('SET',KEYS[4],ARGV[3],'PX',ARGV[4])
            redis.call('DEL',KEYS[3]); redis.call('ZREM',KEYS[1],ARGV[2]); redis.call('ZREM',KEYS[2],ARGV[2])
            return 1
            """;
    static final String DEFER = NOW + """
            if ARGV[1]~='' and redis.call('HGET',KEYS[3],'owner')~=ARGV[1] then return 0 end
            if ARGV[1]~='' then
              local state=redis.call('HGET',KEYS[3],'state')
              if state~='claimed' and state~='sent' then return 0 end
              if tonumber(redis.call('ZSCORE',KEYS[2],ARGV[2]) or '0')<=now then return 0 end
            end
            if ARGV[1]=='' and redis.call('HGET',KEYS[3],'state')~='queued' then return 0 end
            local ending=now+tonumber(ARGV[4])
            redis.call('HSET',KEYS[3],'state','queued'); redis.call('HDEL',KEYS[3],'owner')
            redis.call('ZREM',KEYS[2],ARGV[2]); redis.call('ZADD',KEYS[1],ending,ARGV[2])
            redis.call('SET',KEYS[4],ARGV[3],'PX',ARGV[4])
            if ARGV[5]=='global' then
              local untilMs=math.max(tonumber(redis.call('GET',KEYS[5]) or '0'),ending)
              redis.call('SET',KEYS[5],untilMs,'PX',math.max(1,untilMs-now))
            end
            return 1
            """;
    static final String SNAPSHOT = NOW + """
            local oldest=now
            local ids=redis.call('ZRANGE',KEYS[1],0,-1)
            for _,id in ipairs(ids) do
              local created=redis.call('HGET',ARGV[1]..'job:'..id,'created')
              if created then oldest=math.min(oldest,tonumber(created)) end
            end
            return {redis.call('ZCARD',KEYS[1]),redis.call('ZCARD',KEYS[2]),math.max(0,now-oldest)}
            """;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final DecisionSettings settings;
    private final String prefix;

    DistributedDecisionQueueStore(StringRedisTemplate redis, ObjectMapper mapper, DecisionSettings settings, String prefix) {
        this.redis = redis; this.mapper = mapper; this.settings = settings; this.prefix = prefix;
    }

    @Override
    public Lookup submit(String key, AdultAssessmentItem item) {
        List<?> result = list(SUBMIT, List.of(pending(), inflight(), job(key), result(key), dispatchAfter()), key,
                mapper.writeValueAsString(item), Integer.toString(settings.getQueueCapacity()), Long.toString(settings.getQueueTtl().toMillis()));
        int status = ((Number) result.get(0)).intValue();
        AdultAssessmentModelResult cached = status == 0 ? mapper.readValue((String) result.get(1), AdultAssessmentModelResult.class) : null;
        long remaining = Math.max(2, (((Number) result.get(2)).longValue()+999)/1000);
        // ETA is rough dispatch capacity, not an SLA; quota deferrals carry their actual earliest time.
        long ahead = ((Number) result.get(3)).longValue();
        long eta = Math.max(remaining, (ahead*1000L*60L+49999L)/50000L);
        return new Lookup(cached, eta, ahead, status == 3);
    }

    @Override
    public List<Job> ready() {
        String unknown = mapper.writeValueAsString(GoogleDecisionNsfwClient.unavailable("UNKNOWN_OUTCOME", 60));
        List<?> values = list(READY, List.of(pending(), inflight(), dispatchAfter()), prefix, Integer.toString(Math.min(1000, settings.getBatchSize()*4)),
                Long.toString(Math.max(1, settings.getMaxBatchWait().toMillis()-50)), unknown, Integer.toString(settings.getBatchSize()));
        List<Job> jobs = new ArrayList<>();
        for (int i=0; i<values.size(); i+=3) {
            jobs.add(new Job((String) values.get(i), mapper.readValue((String) values.get(i+1), AdultAssessmentItem.class), Long.parseLong((String) values.get(i+2))));
        }
        return jobs;
    }

    @Override
    public Claim claim(List<Job> jobs) {
        String owner = UUID.randomUUID().toString();
        List<String> keys = new ArrayList<>(List.of(pending(), inflight()));
        List<String> args = new ArrayList<>(List.of(owner));
        jobs.forEach(job -> { keys.add(job(job.key())); args.add(job.key()); });
        List<?> claimed = list(CLAIM, keys, args.toArray(String[]::new));
        return new Claim(owner, jobs.stream().filter(job -> claimed.contains(job.key())).toList());
    }

    @Override
    public boolean markSent(Claim claim) {
        if (claim.jobs().isEmpty()) { return false; }
        List<String> keys = new ArrayList<>(List.of(inflight()));
        List<String> args = new ArrayList<>(List.of(claim.owner()));
        claim.jobs().forEach(job -> { keys.add(job(job.key())); args.add(job.key()); });
        return number(SENT, keys, args.toArray(String[]::new)) == 1;
    }

    @Override
    public void complete(Claim claim, Job job, AdultAssessmentModelResult model) {
        long ttl = model.available() || !Boolean.TRUE.equals(model.raw().get("retryable")) ? settings.getResultTtl().toMillis()
                : Math.max(2000, ((Number) model.raw().getOrDefault("retryAfterSeconds", 60L)).longValue()*1000);
        number(COMPLETE, List.of(pending(), inflight(), job(job.key()), result(job.key())), claim.owner(), job.key(), mapper.writeValueAsString(model), Long.toString(ttl));
    }

    @Override
    public void defer(Claim claim, Job job, long seconds, String upstreamReason) { deferJob(claim.owner(), job, seconds, upstreamReason); }
    @Override
    public void deferPending(List<Job> jobs, long seconds, String upstreamReason) { jobs.forEach(job -> deferJob("", job, seconds, upstreamReason)); }

    private void deferJob(String owner, Job job, long seconds, String upstreamReason) {
        AdultAssessmentModelResult waiting = DecisionMicrobatchClient.pending("QUOTA_WAIT", seconds, 0, upstreamReason);
        number(DEFER, List.of(pending(), inflight(), job(job.key()), result(job.key()), dispatchAfter()), owner, job.key(),
                mapper.writeValueAsString(waiting), Long.toString(Math.max(2, seconds)*1000), "TOKEN_LIMITED".equals(upstreamReason) ? "job" : "global");
    }

    @Override
    public Snapshot snapshot() {
        List<?> values = list(SNAPSHOT, List.of(pending(), inflight()), prefix);
        return new Snapshot(((Number) values.get(0)).longValue(), ((Number) values.get(1)).longValue(), ((Number) values.get(2)).doubleValue()/1000);
    }

    private List<?> list(String script, List<String> keys, String... args) {
        try {
            List<?> values = redis.execute(new DefaultRedisScript<>(script, List.class), keys, (Object[]) args);
            if (values == null) { throw new IllegalStateException("Missing queue result"); }
            return values;
        } catch (RuntimeException ex) { throw new DecisionUnavailableException("QUEUE_UNAVAILABLE", 2); }
    }
    private long number(String script, List<String> keys, String... args) {
        try {
            Long value = redis.execute(new DefaultRedisScript<>(script, Long.class), keys, (Object[]) args);
            if (value == null) { throw new IllegalStateException("Missing queue result"); }
            return value;
        } catch (RuntimeException ex) { throw new DecisionUnavailableException("QUEUE_UNAVAILABLE", 2); }
    }
    private String pending() { return prefix+"pending"; }
    private String inflight() { return prefix+"inflight"; }
    private String dispatchAfter() { return prefix+"dispatch-after"; }
    private String job(String key) { return prefix+"job:"+key; }
    private String result(String key) { return prefix+"result:"+key; }
}
