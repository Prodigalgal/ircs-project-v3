package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;

/** Reuses SDK connection pools without closing a client while another upload is using it. */
@Slf4j
final class R2S3ClientPool implements AutoCloseable {
    private final int maximumSize;
    private final Map<OutboundProxy, Slot> clients = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    R2S3ClientPool(int maximumSize) {
        if (maximumSize < 1) throw new IllegalArgumentException("R2 client limit must be positive");
        this.maximumSize = maximumSize;
    }

    <T> T execute(OutboundProxy route, Supplier<S3Client> factory, Function<S3Client, T> operation) {
        Slot slot = acquire(route, factory);
        try {
            return operation.apply(slot.client);
        } finally {
            release(route, slot);
        }
    }

    private synchronized Slot acquire(OutboundProxy route, Supplier<S3Client> factory) {
        if (closed) throw new IllegalStateException("R2 storage client pool is closed");
        Slot slot = clients.get(route);
        if (slot == null) {
            if (clients.size() >= maximumSize) {
                var iterator = clients.entrySet().iterator();
                while (iterator.hasNext()) {
                    Slot candidate = iterator.next().getValue();
                    if (candidate.users == 0) {
                        iterator.remove();
                        closeClient(candidate.client);
                        break;
                    }
                }
                if (clients.size() >= maximumSize) throw new IllegalStateException("R2 storage client pool is busy");
            }
            slot = new Slot(factory.get());
            clients.put(route, slot);
        }
        slot.users++;
        return slot;
    }

    private synchronized void release(OutboundProxy route, Slot slot) {
        slot.users--;
        if (closed && slot.users == 0) {
            clients.remove(route);
            closeClient(slot.client);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        var iterator = clients.values().iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next();
            if (slot.users == 0) {
                iterator.remove();
                closeClient(slot.client);
            }
        }
    }

    private static void closeClient(S3Client client) {
        try {
            client.close();
        } catch (RuntimeException ex) {
            log.warn("R2 SDK client close failed: {}", ex.getClass().getSimpleName());
        }
    }

    private static final class Slot {
        private final S3Client client;
        private int users;

        private Slot(S3Client client) {
            this.client = client;
        }
    }
}
