package com.prodigalgal.ircs.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleDecisionCredentialCatalogTest {
    private final CredentialProviderCatalog catalog = new CredentialProviderCatalog();

    @Test
    void dedicatedProviderRequiresKeyAndRetainsOptionalProjectWithoutChangingOpenAi() {
        catalog.validate(request(Map.of("api_key", "test-key", "project_id", "project-example")));
        assertThat(catalog.templates("GOOGLE_DECISIONS")).extracting(CredentialTemplateField::key)
                .containsExactly("api_key", "project_id");
        assertThat(catalog.templates("OPENAI")).extracting(CredentialTemplateField::key).containsExactly("api_key", "base_url");
        assertThat(catalog.fingerprintSource("GOOGLE_DECISIONS", Map.of("api_key", "test-key", "project_id", "first-project")))
                .isEqualTo(catalog.fingerprintSource("GOOGLE_DECISIONS", Map.of("api_key", "test-key", "project_id", "other-project")));
    }

    @Test
    void rejectsMissingKeyCustomEndpointAndInvalidProjectMetadata() {
        assertThatThrownBy(() -> catalog.validate(request(Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.validate(request(Map.of("api_key", "key", "base_url", "https://example.test"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.validate(request(Map.of("api_key", "key", "project_id", "bad id"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.validate(request(Map.of("api_key", 123))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.validate(request(Map.of("api_key", "key", "project_id", "a".repeat(31)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managementReadModelRetainsOnlyPresenceMarkersForGoogleSecrets() {
        var sanitizer = new CredentialSanitizer(new tools.jackson.databind.ObjectMapper());
        assertThat(sanitizer.redactedPayload("GOOGLE_DECISIONS", Map.of("api_key", "test-secret", "project_id", "project-example")))
                .containsEntry("api_key", Map.of("present", true, "redacted", true))
                .doesNotContainValue("test-secret");
    }
    private CredentialWriteRequest request(Map<String,Object> payload) {
        return new CredentialWriteRequest("GOOGLE_DECISIONS", "test", payload, true, 0,
                null, null, 0L, 0L, 0L, 0L, "system budget is shared");
    }
}
