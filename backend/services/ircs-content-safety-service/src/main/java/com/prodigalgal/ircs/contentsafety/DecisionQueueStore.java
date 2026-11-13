package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;
import java.util.List;

interface DecisionQueueStore {
    Lookup submit(String key, AdultAssessmentItem item);
    List<Job> ready();
    Claim claim(List<Job> jobs);
    boolean markSent(Claim claim);
    void complete(Claim claim, Job job, AdultAssessmentModelResult result);
    void defer(Claim claim, Job job, long seconds, String upstreamReason);
    void deferPending(List<Job> jobs, long seconds, String upstreamReason);
    Snapshot snapshot();

    record Lookup(AdultAssessmentModelResult result, long retryAfterSeconds, long ahead, boolean full) {}
    record Job(String key, AdultAssessmentItem item, long createdAt) {}
    record Claim(String owner, List<Job> jobs) {}
    record Snapshot(long queued, long inflight, double oldestAgeSeconds) {}
}
