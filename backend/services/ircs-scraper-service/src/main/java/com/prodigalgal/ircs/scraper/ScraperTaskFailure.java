package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.OutboundCircuitOpenException;
import com.prodigalgal.ircs.messaging.TaskHttpStatusException;
import com.prodigalgal.ircs.messaging.TaskSourceTerminalException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessException;

/** Error details safe to persist in broker headers, progress snapshots and worker audit. */
final class ScraperTaskFailure extends RuntimeException {

    private static final int MAX_CAUSE_DEPTH = 12;

    ScraperTaskFailure(String reason) {
        super(reason, null, false, false);
    }

    static ScraperTaskFailure summarize(Throwable error) {
        String reason = "Task processing failed (" + error.getClass().getSimpleName() + ")";
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            String knownReason = switch (cause) {
                case TaskHttpStatusException http -> "HTTP status " + http.statusCode();
                case TaskSourceTerminalException ignored -> "Invalid collection source response";
                case URISyntaxException ignored -> "Invalid source request URI";
                case ScraperTrafficLimiter.SourceTrafficLimitException traffic -> traffic.getMessage();
                case OutboundCircuitOpenException ignored -> "Outbound circuit is open";
                case HttpTimeoutException ignored -> "Outbound HTTP timeout";
                case SocketTimeoutException ignored -> "Outbound socket timeout";
                case TimeoutException ignored -> "Scraper task processing timeout";
                case UnknownHostException ignored -> "Outbound DNS resolution failed";
                case SSLException ignored -> "Outbound TLS connection failed";
                case ConnectException ignored -> "Outbound connection failed";
                case InterruptedException ignored -> "Scraper task interrupted";
                case AmqpException ignored -> "RabbitMQ publication or consumption failed";
                case DataAccessException ignored -> "Task persistence or cache access failed";
                default -> null;
            };
            if (knownReason != null) {
                return new ScraperTaskFailure(knownReason);
            }
            if (cause instanceof IOException io) {
                // A generic transport wrapper may contain a more specific DNS or TLS cause.
                String message = io.getMessage();
                if (message != null && message.toLowerCase(Locale.ROOT).contains("proxy")) {
                    reason = "Outbound proxy connection failed";
                } else if (!"Outbound proxy connection failed".equals(reason)) {
                    reason = "Outbound transport failed";
                }
            }
        }
        return new ScraperTaskFailure(reason);
    }
}
