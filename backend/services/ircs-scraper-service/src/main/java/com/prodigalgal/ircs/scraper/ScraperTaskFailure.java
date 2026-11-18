package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.OutboundCircuitOpenException;
import com.prodigalgal.ircs.messaging.TaskHttpStatusException;
import com.prodigalgal.ircs.messaging.TaskSourceTerminalException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessException;

/** Error details safe to persist in broker headers, progress snapshots and worker audit. */
final class ScraperTaskFailure extends RuntimeException {

    ScraperTaskFailure(String reason) {
        super(reason, null, false, false);
    }

    static ScraperTaskFailure summarize(Throwable error) {
        String reason = "Task processing failed (" + error.getClass().getSimpleName() + ")";
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String knownReason = switch (cause) {
                case TaskHttpStatusException http -> "HTTP status " + http.statusCode();
                case TaskSourceTerminalException ignored -> "Invalid collection source response";
                case ScraperTrafficLimiter.SourceTrafficLimitException traffic -> traffic.getMessage();
                case OutboundCircuitOpenException ignored -> "Outbound circuit is open";
                case HttpTimeoutException ignored -> "Outbound HTTP timeout";
                case SocketTimeoutException ignored -> "Outbound socket timeout";
                case TimeoutException ignored -> "Scraper task processing timeout";
                case UnknownHostException ignored -> "Outbound DNS resolution failed";
                case SSLException ignored -> "Outbound TLS connection failed";
                case InterruptedException ignored -> "Scraper task interrupted";
                case AmqpException ignored -> "RabbitMQ publication or consumption failed";
                case DataAccessException ignored -> "Task persistence or cache access failed";
                default -> null;
            };
            if (knownReason != null) {
                return new ScraperTaskFailure(knownReason);
            }
            if (cause instanceof IOException io) {
                String message = io.getMessage();
                reason = message != null && message.toLowerCase(Locale.ROOT).contains("proxy")
                        ? "Outbound proxy connection failed" : "Outbound transport failed";
            }
        }
        return new ScraperTaskFailure(reason);
    }
}
