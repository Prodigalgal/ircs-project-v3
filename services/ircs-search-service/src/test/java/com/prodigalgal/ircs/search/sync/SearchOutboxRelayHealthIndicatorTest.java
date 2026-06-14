package com.prodigalgal.ircs.search.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

class SearchOutboxRelayHealthIndicatorTest {

    private final ObjectProvider<SearchOutboxRelay> relayProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    private final SearchSyncTaskOpsRepository statsRepository = org.mockito.Mockito.mock(SearchSyncTaskOpsRepository.class);

    @Test
    void disabledRelayReportsUpWithoutQueryingOutboxTable() {
        SearchOutboxRelayHealthIndicator indicator = indicator(new MockEnvironment());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("enabled", false);
        verify(statsRepository, never()).stats();
    }

    @Test
    void enabledRelayReportsOutboxStatsAndWorkerState() {
        SearchOutboxRelay relay = org.mockito.Mockito.mock(SearchOutboxRelay.class);
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        when(relay.state()).thenReturn(new SearchOutboxRelayState(
                false,
                "search-pod-1",
                Instant.now(),
                Instant.now(),
                null,
                2,
                1,
                0,
                null));
        when(statsRepository.stats()).thenReturn(new SearchSyncTaskStatsResponse(
                true,
                null,
                3,
                2,
                0,
                1,
                0,
                1,
                1,
                2,
                2,
                1,
                0,
                Instant.now().minusSeconds(30),
                Instant.now()));

        var health = indicator(new MockEnvironment()
                .withProperty("app.search.outbox.relay.enabled", "true"))
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("enabled", true)
                .containsEntry("workerId", "search-pod-1")
                .containsEntry("lastPublished", 2);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> outbox = (java.util.Map<String, Object>) health.getDetails().get("outbox");
        assertThat(outbox)
                .containsEntry("pending", 2L)
                .containsEntry("duePending", 1L);
    }

    @Test
    void enabledRelayReportsDownWhenStatsAreUnavailable() {
        SearchOutboxRelay relay = org.mockito.Mockito.mock(SearchOutboxRelay.class);
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        when(relay.state()).thenReturn(new SearchOutboxRelayState(
                false,
                "search-pod-1",
                null,
                null,
                null,
                0,
                0,
                0,
                null));
        when(statsRepository.stats()).thenReturn(SearchSyncTaskStatsResponse.unavailable("search_sync_tasks_unavailable"));

        var health = indicator(new MockEnvironment()
                .withProperty("app.search.outbox.relay.enabled", "true"))
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "search_sync_tasks_unavailable");
    }

    @Test
    void enabledRelayReportsDownAfterLastRunFailure() {
        SearchOutboxRelay relay = org.mockito.Mockito.mock(SearchOutboxRelay.class);
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        when(relay.state()).thenReturn(new SearchOutboxRelayState(
                false,
                "search-pod-1",
                Instant.now(),
                null,
                Instant.now(),
                0,
                0,
                1,
                "IllegalStateException: rabbit down"));
        when(statsRepository.stats()).thenReturn(new SearchSyncTaskStatsResponse(
                true,
                null,
                1,
                1,
                0,
                0,
                0,
                1,
                1,
                0,
                1,
                0,
                0,
                Instant.now(),
                Instant.now()));

        var health = indicator(new MockEnvironment()
                .withProperty("app.search.outbox.relay.enabled", "true"))
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "search_outbox_relay_last_run_failed");
    }

    private SearchOutboxRelayHealthIndicator indicator(MockEnvironment environment) {
        return new SearchOutboxRelayHealthIndicator(environment, relayProvider, statsRepository);
    }
}
