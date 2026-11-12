package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AggregationInternalAccessPolicyTest {
    @Test
    void requiresConfiguredTokenAndScopeEvenWithHistoricalOptOut() {
        var policy = new AggregationInternalAccessPolicy();
        ReflectionTestUtils.setField(policy, "requireToken", false);
        assertThatThrownBy(() -> policy.assertAccess(null, null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        ReflectionTestUtils.setField(policy, "configuredToken", "secret");
        ReflectionTestUtils.setField(policy, "requiredScope", "test:maintenance");
        assertThatThrownBy(() -> policy.assertAccess("worker", "wrong", "test:maintenance"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> policy.assertAccess("worker", "secret", "other:read"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("scope is missing");
        policy.assertAccess("worker", "secret", "test:maintenance");
    }
}
