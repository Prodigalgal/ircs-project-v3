package com.prodigalgal.ircs.common.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;

public final class ScheduledTriggers {

    private ScheduledTriggers() {
    }

    public static ExecutorService virtualThreadExecutor(String threadNamePrefix) {
        ThreadFactory factory = Thread.ofVirtual().name(threadNamePrefix, 0).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    public static void submit(Executor executor, Runnable task, Logger log, String triggerName) {
        try {
            executor.execute(task);
        } catch (RuntimeException ex) {
            log.warn("Scheduled trigger rejected: trigger={}, reason={}", triggerName, ex.getMessage());
        }
    }
}
