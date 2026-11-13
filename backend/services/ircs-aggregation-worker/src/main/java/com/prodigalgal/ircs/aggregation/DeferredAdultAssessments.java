package com.prodigalgal.ircs.aggregation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Operation-local plan; it never holds DB resources or network futures. */
final class DeferredAdultAssessments implements AutoCloseable {
    private static final ThreadLocal<DeferredAdultAssessments> CURRENT = new ThreadLocal<>();
    private final Set<UUID> ids = new LinkedHashSet<>();
    private final Map<String, Runnable> indexes = new LinkedHashMap<>();
    private final Set<UUID> restricted = new LinkedHashSet<>();
    private final Set<UUID> restrictedPublished = new LinkedHashSet<>();
    private boolean publishing;

    static DeferredAdultAssessments open() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested adult assessment operation");
        }
        var scope = new DeferredAdultAssessments();
        CURRENT.set(scope);
        return scope;
    }

    static boolean register(UUID id) {
        var scope = CURRENT.get();
        if (scope == null) { return false; }
        scope.ids.add(id);
        return true;
    }

    static void registerRestricted(UUID id) {
        var scope = CURRENT.get();
        if (scope != null) { scope.restricted.add(id); }
    }

    static boolean contains(UUID id) {
        var scope = CURRENT.get();
        return scope != null && scope.ids.contains(id);
    }

    static boolean deferIndex(String key, Runnable index) {
        var scope = CURRENT.get();
        if (scope == null || scope.publishing) { return false; }
        scope.indexes.putIfAbsent(key, index);
        return true;
    }

    List<UUID> ids() { return List.copyOf(ids); }

    boolean restrictedWasPublished(UUID id) { return restrictedPublished.contains(id); }

    // Only committed, locally proven restrictions bypass AI waiting. Never flush other indexes here.
    void publishRestrictedIndexes() {
        publishing = true;
        try {
            for (UUID id : restricted) {
                Runnable index = indexes.remove("unified:" + id);
                if (index != null) {
                    index.run();
                    restrictedPublished.add(id);
                }
            }
        } finally {
            publishing = false;
        }
    }

    void publishIndexes() {
        publishing = true;
        try {
            indexes.values().forEach(Runnable::run);
        } finally {
            publishing = false;
        }
    }

    @Override
    public void close() { CURRENT.remove(); }
}
