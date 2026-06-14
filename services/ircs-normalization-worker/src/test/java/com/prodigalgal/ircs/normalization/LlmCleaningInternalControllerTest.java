package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class LlmCleaningInternalControllerTest {

    private final LlmCleaningService cleaningService = org.mockito.Mockito.mock(LlmCleaningService.class);
    private final NormalizationInternalAccessPolicy accessPolicy = new NormalizationInternalAccessPolicy();
    private final LlmCleaningInternalController controller =
            new LlmCleaningInternalController(cleaningService, accessPolicy);

    @Test
    void runsLanguageCleaningThroughInternalEndpoint() {
        when(cleaningService.cleanBatch(LlmCleaningKind.LANGUAGE))
                .thenReturn(new LlmCleaningService.LlmCleaningResult(
                        LlmCleaningKind.LANGUAGE,
                        "SUCCESS",
                        3,
                        2,
                        1,
                        "CLEANED"));

        var response = controller.run("language", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().kind()).isEqualTo("LANGUAGE");
        assertThat(response.getBody().status()).isEqualTo("SUCCESS");
        assertThat(response.getBody().candidates()).isEqualTo(3);
        assertThat(response.getBody().mapped()).isEqualTo(2);
        assertThat(response.getBody().noise()).isEqualTo(1);
        verify(cleaningService).cleanBatch(LlmCleaningKind.LANGUAGE);
    }

    @Test
    void allowsConcurrentRunsAndLetsRepositoryClaimSelectAvailableBatch() {
        when(cleaningService.cleanBatch(LlmCleaningKind.GENRE))
                .thenReturn(new LlmCleaningService.LlmCleaningResult(
                        LlmCleaningKind.GENRE,
                        "SUCCESS",
                        0,
                        0,
                        0,
                        "NO_CANDIDATES"));

        var response = controller.run("genre", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().reason()).isEqualTo("NO_CANDIDATES");
        verify(cleaningService).cleanBatch(LlmCleaningKind.GENRE);
    }

    @Test
    void rejectsUnsupportedKindWithBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.run("sanitize", null, null, null));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforcesInternalServiceIdentityWhenTokenIsRequired() {
        ReflectionTestUtils.setField(accessPolicy, "requireToken", true);
        ReflectionTestUtils.setField(accessPolicy, "configuredToken", "internal-token");
        ReflectionTestUtils.setField(accessPolicy, "requiredScope", "normalization:maintenance");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.run(
                        "area",
                        "ops-service",
                        "wrong-token",
                        "normalization:maintenance"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
