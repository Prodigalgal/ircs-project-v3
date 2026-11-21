package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ScraperTaskFailureTest {

    @Test
    void wrappedTransportFailureIsClassifiedWithoutPersistingSensitiveMessage() {
        var failure = ScraperTaskFailure.summarize(new IllegalStateException(
                "fetch failed for https://provider.example.test/api?token=private-token",
                new IOException("connection reset while contacting proxy-user:proxy-password@proxy.example.test")));

        assertThat(failure).hasMessage("Outbound proxy connection failed").hasNoCause();
        assertThat(failure.getMessage())
                .doesNotContain("provider.example.test", "private-token", "proxy-password");
    }

    @Test
    void wrappedNonProxyTransportFailureDoesNotCollapseToIllegalStateException() {
        var failure = ScraperTaskFailure.summarize(new IllegalStateException(
                "HTTP fetch failed",
                new IOException("connection reset")));

        assertThat(failure).hasMessage("Outbound transport failed").hasNoCause();
    }

    @Test
    void specificNestedDnsClassificationTakesPriorityOverGenericTransport() {
        var failure = ScraperTaskFailure.summarize(new IllegalStateException("fetch failed",
                new IOException("transport failed for proxy-credential",
                        new UnknownHostException("private-host.test"))));

        assertThat(failure).hasMessage("Outbound DNS resolution failed").hasNoCause();
    }
}
