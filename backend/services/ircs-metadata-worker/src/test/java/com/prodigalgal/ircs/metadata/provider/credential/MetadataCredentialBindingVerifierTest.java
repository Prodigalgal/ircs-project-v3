package com.prodigalgal.ircs.metadata.provider.credential;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.credential.ProviderCredentialLease;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class MetadataCredentialBindingVerifierTest {
    @Test
    void checksTheSameArrayContractUsedByAllThreeProviderCredentialRepositories() {
        assertDoesNotThrow(() -> new MetadataCredentialBindingVerifier(JsonMapper.builder().build()).verify());
    }

    @Test
    void refusesStartupWhenRequiredLeaseFieldsCannotBeBound() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), eq(ProviderCredentialLease[].class)))
                .thenReturn(new ProviderCredentialLease[] {new ProviderCredentialLease()});
        assertThrows(IllegalStateException.class, () -> new MetadataCredentialBindingVerifier(mapper).verify());
    }
}
