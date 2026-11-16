package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Duration;

/** Extends visibility for a currently owned magnet work item. */
interface MagnetWorkLeaseStore {
    boolean renew(RuntimeWorkItem item, Duration visibility);

    boolean defer(RuntimeWorkItem item, Duration delay, String reason);
}
