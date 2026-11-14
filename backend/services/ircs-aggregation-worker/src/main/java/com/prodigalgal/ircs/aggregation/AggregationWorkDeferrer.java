package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;

/** Aggregation-only waiting transition, without consuming a business failure attempt. */
interface AggregationWorkDeferrer {
    boolean defer(RuntimeWorkItem item, Duration delay, String reason);

    boolean renew(RuntimeWorkItem item, Duration visibility);

    default int failureAttempt(RuntimeWorkItem item) { return item.attempt(); }
}
