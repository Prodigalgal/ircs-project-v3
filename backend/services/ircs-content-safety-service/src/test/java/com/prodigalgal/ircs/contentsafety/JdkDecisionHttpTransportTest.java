package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JdkDecisionHttpTransportTest {

    @Test
    void sendsGoogleDecisionRequestThroughPublicProxyPool() throws Exception {
        RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                .thenReturn("http://oracle.gateway:17890,http://cf.gateway:17890");
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        List<com.prodigalgal.ircs.common.outbound.OutboundHttpRequest> sent = new ArrayList<>();
        OutboundHttpClient client = new OutboundHttpClient(
                new OutboundUrlPolicy(host -> List.of(InetAddress.getByName("93.184.216.34"))),
                request -> {
                    sent.add(request);
                    return new OutboundHttpResponse(200, Map.of(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                },
                pool);

        DecisionHttpTransport.Response response = new JdkDecisionHttpTransport(client).exchange(
                "POST",
                GoogleDecisionNsfwClient.ENDPOINT,
                Map.of("x-goog-api-key", "test-key", "Content-Type", "application/json"),
                "{}",
                Duration.ofSeconds(4));

        assertThat(response.status()).isEqualTo(200);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().policy().type()).isEqualTo(
                com.prodigalgal.ircs.common.outbound.OutboundHttpPolicyType.PUBLIC_FETCH);
        assertThat(sent.getFirst().policy().maxRetries()).isZero();
        assertThat(sent.getFirst().policy().proxy().host()).isEqualTo("oracle.gateway");
        assertThat(sent.getFirst().headers()).containsEntry("x-goog-api-key", "test-key");
    }
}
