package com.prodigalgal.ircs.portalbff;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PortalBffRoutes extends BffRouteTable {

    PortalBffRoutes(
            @Value("${app.bff.targets.identity}") String identityBaseUrl,
            @Value("${app.bff.targets.interaction}") String interactionBaseUrl,
            @Value("${app.bff.targets.portal}") String portalBaseUrl,
            @Value("${app.bff.targets.search}") String searchBaseUrl,
            @Value("${app.bff.targets.storage}") String storageBaseUrl) {
        super(List.of(
                new BffRoute("/api/backend/api/portal/auth", identityBaseUrl, "/api/portal/auth"),
                new BffRoute("/api/backend/api/portal/profile", identityBaseUrl, "/api/portal/profile"),
                new BffRoute("/api/backend/api/portal/interaction", interactionBaseUrl, "/api/portal/interaction"),
                new BffRoute("/api/backend/api/portal/feedback", interactionBaseUrl, "/api/portal/feedback"),
                new BffRoute("/api/backend/api/portal/search", searchBaseUrl, "/api/portal/search"),
                new BffRoute("/api/backend/api/portal", portalBaseUrl, "/api/portal"),
                new BffRoute("/api/portal/auth", identityBaseUrl, "/api/portal/auth"),
                new BffRoute("/api/portal/profile", identityBaseUrl, "/api/portal/profile"),
                new BffRoute("/api/portal/interaction", interactionBaseUrl, "/api/portal/interaction"),
                new BffRoute("/api/portal/feedback", interactionBaseUrl, "/api/portal/feedback"),
                new BffRoute("/api/portal/search", searchBaseUrl, "/api/portal/search"),
                new BffRoute("/api/portal", portalBaseUrl, "/api/portal"),
                new BffRoute("/media", storageBaseUrl, "/media")
        ));
    }
}
