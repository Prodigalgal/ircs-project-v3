package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleLlmCleaningClientTest {

    @Test
    void internalAiGatewayKeepsClusterRoutingAndDoesNotUsePublicProxyPool() throws Exception {
        RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        List<com.prodigalgal.ircs.common.outbound.OutboundHttpRequest> sent = new ArrayList<>();
        OutboundHttpClient httpClient = new OutboundHttpClient(
                new OutboundUrlPolicy(host -> List.of(InetAddress.getByName("10.244.1.2"))), request -> {
                    sent.add(request);
                    return new OutboundHttpResponse(200, Map.of(),
                            "{\"choices\":[{\"message\":{\"content\":\"[]\"}}]}".getBytes(StandardCharsets.UTF_8));
                }, pool);
        NormalizationConfigValues values = org.mockito.Mockito.mock(NormalizationConfigValues.class);
        when(values.llmRequestTimeoutSeconds()).thenReturn(4);
        new OpenAiCompatibleLlmCleaningClient(new ObjectMapper(), values, httpClient).analyzeAndMap(
                List.of("raw"), Set.of("canonical"), LlmCleaningKind.GENRE,
                new LlmCredential("test-key", "http://ai-gateway.ircs-prod.svc.cluster.local:8080/v1", "test-model", "test"));
        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.policy().type()).isEqualTo(com.prodigalgal.ircs.common.outbound.OutboundHttpPolicyType.INTERNAL_SERVICE);
            assertThat(request.policy().proxy().enabled()).isFalse();
            assertThat(request.policy().maxRetries()).isZero();
        });
    }

    @Test
    void sendsLiveLlmRequestThroughPublicProxyPool() throws Exception {
        RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                .thenReturn("http://oracle.gateway:17890,http://cf.gateway:17890");
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        List<com.prodigalgal.ircs.common.outbound.OutboundHttpRequest> sent = new ArrayList<>();
        String responseBody = """
                {"choices":[{"message":{"content":"[{\\"raw\\":\\"raw\\",\\"standard\\":\\"canonical\\",\\"noise\\":false}]"}}]}
                """;
        OutboundHttpClient httpClient = new OutboundHttpClient(
                new OutboundUrlPolicy(host -> List.of(InetAddress.getByName("93.184.216.34"))),
                request -> {
                    sent.add(request);
                    return new OutboundHttpResponse(200, Map.of(), responseBody.getBytes(StandardCharsets.UTF_8));
                },
                pool);
        NormalizationConfigValues configValues = org.mockito.Mockito.mock(NormalizationConfigValues.class);
        when(configValues.llmRequestTimeoutSeconds()).thenReturn(4);
        OpenAiCompatibleLlmCleaningClient client = new OpenAiCompatibleLlmCleaningClient(
                new ObjectMapper(), configValues, httpClient);

        List<LlmCleaningDecision> decisions = client.analyzeAndMap(
                List.of("raw"), Set.of("canonical"), LlmCleaningKind.GENRE,
                new LlmCredential("test-key", "https://provider.test/v1", "test-model", "test"));

        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.raw()).isEqualTo("raw");
            assertThat(decision.standard()).isEqualTo("canonical");
            assertThat(decision.noise()).isFalse();
        });
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().policy().proxy().host()).isEqualTo("oracle.gateway");
        assertThat(sent.getFirst().policy().maxRetries()).isZero();
        assertThat(sent.getFirst().policy().timeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(sent.getFirst().headers()).containsEntry("Authorization", "Bearer test-key");
    }
}
