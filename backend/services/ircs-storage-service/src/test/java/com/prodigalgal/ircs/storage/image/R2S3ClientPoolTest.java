package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class R2S3ClientPoolTest {
    private final OutboundProxy oracle = OutboundProxy.http("oracle", 17890, null, null);
    private final OutboundProxy cloudflare = OutboundProxy.http("cf", 17890, null, null);

    @Test
    void concurrentOperationsReuseOneClientAndShutdownClosesItExactlyOnce() throws Exception {
        var client = mock(S3Client.class);
        var created = new AtomicInteger();
        var pool = new R2S3ClientPool(2);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 64).mapToObj(index -> executor.submit(() ->
                    pool.execute(oracle, () -> { created.incrementAndGet(); return client; }, value -> value)))
                    .toList();
            for (var call : calls) assertThat(call.get(5, TimeUnit.SECONDS)).isSameAs(client);
        }
        assertThat(created).hasValue(1);
        verify(client, never()).close();
        pool.close();
        pool.close();
        verify(client, times(1)).close();
        assertThatThrownBy(() -> pool.execute(oracle, () -> client, value -> value))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
    }

    @Test
    void endpointChangesEvictIdleClientsButNeverInterruptAnActiveUpload() throws Exception {
        var first = mock(S3Client.class);
        var second = mock(S3Client.class);
        var replacement = mock(S3Client.class);
        var active = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var pool = new R2S3ClientPool(2);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var upload = executor.submit(() -> pool.execute(oracle, () -> first, client -> {
                active.countDown();
                try {
                    if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test upload was not released");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return true;
            }));
            try {
                assertThat(active.await(5, TimeUnit.SECONDS)).isTrue();
                pool.execute(cloudflare, () -> second, client -> true);
                pool.execute(OutboundProxy.disabled(), () -> replacement, client -> true);
                verify(second).close();
                verify(first, never()).close();
                pool.close();
                verify(replacement).close();
                verify(first, never()).close();
            } finally {
                finish.countDown();
            }
            assertThat(upload.get(5, TimeUnit.SECONDS)).isTrue();
            verify(first).close();
        } finally {
            pool.close();
        }
    }

    @Test
    void failedOperationsReleaseTheirLeaseAndBusyRoutesDoNotAllocateAnotherClient() {
        var client = mock(S3Client.class);
        try (var pool = new R2S3ClientPool(1)) {
            assertThatThrownBy(() -> pool.execute(oracle, () -> client, active -> {
                assertThatThrownBy(() -> pool.execute(cloudflare, () -> mock(S3Client.class), value -> true))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("busy");
                throw new IllegalArgumentException("operation failed");
            })).isInstanceOf(IllegalArgumentException.class);
            var replacement = mock(S3Client.class);
            S3Client reused = pool.execute(cloudflare, () -> replacement, value -> value);
            assertThat(reused).isSameAs(replacement);
            verify(client).close();
        }
    }
}
