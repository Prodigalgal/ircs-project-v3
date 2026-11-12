package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ContentSafetyInternalAccessPolicyTest {
    @Test
    void failsClosedDespiteHistoricalOptOut() {
        var properties = new ContentSafetyProperties();
        properties.internalAccess().setRequireToken(false);
        var policy = new ContentSafetyInternalAccessPolicy(properties);
        assertThatThrownBy(() -> policy.assertAccess(null, null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        properties.internalAccess().setToken("secret");
        assertThatThrownBy(() -> policy.assertAccess("worker", "secret", "other:read"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("scope is missing");
        policy.assertAccess("worker", "secret", "content-safety:assess");
    }
}
