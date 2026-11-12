package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StorageInternalAccessPolicyTest {
    @Test
    void failsClosedAndRetainsTokenScopeValidation() {
        var missing = new StorageInternalAccessPolicy(false, "", "storage:maintenance");
        assertThatThrownBy(() -> missing.assertAvatarAccess(null, null, null))
                .isInstanceOfSatisfying(StorageApiException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        var secured = new StorageInternalAccessPolicy(false, "secret", "storage:maintenance");
        assertThatThrownBy(() -> secured.assertAvatarAccess("worker", "secret", "storage:read"))
                .isInstanceOfSatisfying(StorageApiException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED));
        secured.assertAvatarAccess("worker", "secret", "storage:maintenance");
    }
}
