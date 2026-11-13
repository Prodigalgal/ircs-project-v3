package com.prodigalgal.ircs.contentsafety;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

interface DecisionHttpTransport {
    Response exchange(String method, URI uri, Map<String, String> headers, String body, Duration timeout)
            throws IOException, InterruptedException;

    record Response(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }
    }
}
