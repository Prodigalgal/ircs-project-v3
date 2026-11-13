package com.prodigalgal.ircs.contentsafety;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.content-safety.google-decision")
class DecisionSettings {
    static final String PROVIDER = "GOOGLE_DECISIONS";
    static final String MODEL = "gemini-decision-1";
    private String credentialServiceBaseUrl = "http://ircs-credential-service:8080";
    private String credentialServiceToken = "";
    private String credentialServiceId = "ircs-content-safety-service";
    private String credentialServiceScopes = "credential:lease";
    private Duration requestTimeout = Duration.ofSeconds(10);
    private int batchSize = 20;
    private int maxRequestBudget = 24000;
    private int maxTextLength = 12000;
    private Duration maxBatchWait = Duration.ofMillis(500);
    private int queueCapacity = 10000;
    private Duration queueTtl = Duration.ofHours(24);
    private Duration resultTtl = Duration.ofHours(24);
    private int concurrentBatches = 2;
    private double minConfidence = 0.9d;

    void validate() {
        if (batchSize < 1 || batchSize > 1000 || maxRequestBudget < 4096 || maxRequestBudget > 50000
                || maxTextLength < 1 || maxTextLength > 100000
                || queueCapacity < 1 || queueCapacity > 10000 || concurrentBatches < 1 || concurrentBatches > 8
                || maxBatchWait == null || maxBatchWait.compareTo(Duration.ofMillis(50)) < 0 || maxBatchWait.compareTo(Duration.ofSeconds(2)) > 0
                || queueTtl == null || queueTtl.compareTo(Duration.ofMinutes(1)) < 0 || queueTtl.compareTo(Duration.ofHours(24)) > 0
                || resultTtl == null || resultTtl.compareTo(Duration.ofMinutes(1)) < 0 || resultTtl.compareTo(Duration.ofHours(24)) > 0
                || !Double.isFinite(minConfidence) || minConfidence < 0.5 || minConfidence > 1
                || requestTimeout == null || !requestTimeout.isPositive() || requestTimeout.compareTo(Duration.ofSeconds(20)) > 0) {
            throw new DecisionUnavailableException("CONFIGURATION_ERROR", 60);
        }
    }
}
