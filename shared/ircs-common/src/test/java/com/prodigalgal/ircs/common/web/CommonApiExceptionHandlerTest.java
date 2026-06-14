package com.prodigalgal.ircs.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class CommonApiExceptionHandlerTest {

    private final CommonApiExceptionHandler handler = new CommonApiExceptionHandler();

    @Test
    void mapsUnknownMvcResourcesToUniformNotFoundEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard");
        request.addHeader(ApiErrorResponses.TRACE_HEADER, "trace-404");
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/api/v1/dashboard");

        var response = handler.handleNotFound(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("http.404");
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/dashboard");
        assertThat(response.getBody().traceId()).isEqualTo("trace-404");
    }
}
