package com.prodigalgal.ircs.contentsafety;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.content-safety")
class ContentSafetyProperties {

    private final InternalAccess internalAccess = new InternalAccess();
    private final Adult adult = new Adult();

    InternalAccess internalAccess() {
        return internalAccess;
    }

    Adult adult() {
        return adult;
    }

    static class InternalAccess {
        private boolean requireToken;
        private String token = "";
        private String requiredScope = "content-safety:assess";

        boolean requireToken() {
            return requireToken;
        }

        void setRequireToken(boolean requireToken) {
            this.requireToken = requireToken;
        }

        String token() {
            return token;
        }

        void setToken(String token) {
            this.token = token;
        }

        String requiredScope() {
            return requiredScope;
        }

        void setRequiredScope(String requiredScope) {
            this.requiredScope = requiredScope;
        }
    }

    static class Adult {
        private int maxTextLength = 4096;
        private final Model model = new Model();

        int maxTextLength() {
            return maxTextLength;
        }

        void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }

        Model model() {
            return model;
        }
    }

    static class Model {
        private boolean enabled;
        private String name = "uget/sexual_content_dection";
        private String version = "remote";
        private String endpoint = "";
        private Duration requestTimeout = Duration.ofSeconds(3);
        private double adultThreshold = 0.85d;
        private double suspectThreshold = 0.5d;

        boolean enabled() {
            return enabled;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        String name() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        String version() {
            return version;
        }

        void setVersion(String version) {
            this.version = version;
        }

        String endpoint() {
            return endpoint;
        }

        void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        Duration requestTimeout() {
            return requestTimeout;
        }

        void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        double adultThreshold() {
            return adultThreshold;
        }

        void setAdultThreshold(double adultThreshold) {
            this.adultThreshold = adultThreshold;
        }

        double suspectThreshold() {
            return suspectThreshold;
        }

        void setSuspectThreshold(double suspectThreshold) {
            this.suspectThreshold = suspectThreshold;
        }
    }
}
