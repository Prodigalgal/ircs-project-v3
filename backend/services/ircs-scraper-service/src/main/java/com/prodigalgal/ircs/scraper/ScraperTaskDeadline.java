package com.prodigalgal.ircs.scraper;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

final class ScraperTaskDeadline {

    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

    private ScraperTaskDeadline() {
    }

    static void run(Duration timeout, Runnable action) {
        DEADLINE.set(System.nanoTime() + timeout.toNanos());
        try {
            check();
            action.run();
        } finally {
            DEADLINE.remove();
        }
    }

    static void check() {
        Long deadline = DEADLINE.get();
        if (Thread.currentThread().isInterrupted()
                || (deadline != null && System.nanoTime() - deadline >= 0)) {
            throw new IllegalStateException("Scraper task processing deadline exceeded", new TimeoutException());
        }
    }
}
