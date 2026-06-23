package com.prodigalgal.ircs.adminbff;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AdminBffRoutes extends BffRouteTable {

    AdminBffRoutes(
            @Value("${app.bff.targets.identity}") String identityBaseUrl,
            @Value("${app.bff.targets.content}") String contentBaseUrl,
            @Value("${app.bff.targets.catalog}") String catalogBaseUrl,
            @Value("${app.bff.targets.task}") String taskBaseUrl,
            @Value("${app.bff.targets.ops}") String opsBaseUrl,
            @Value("${app.bff.targets.ops-alert}") String opsAlertBaseUrl,
            @Value("${app.bff.targets.search}") String searchBaseUrl,
            @Value("${app.bff.targets.storage}") String storageBaseUrl,
            @Value("${app.bff.targets.config}") String configBaseUrl,
            @Value("${app.bff.targets.credential}") String credentialBaseUrl,
            @Value("${app.bff.targets.magnet}") String magnetBaseUrl,
            @Value("${app.bff.targets.scraper}") String scraperBaseUrl,
            @Value("${app.bff.targets.interaction}") String interactionBaseUrl) {
        super(List.of(
                new BffRoute("/api/v1/auth", identityBaseUrl, "/api/v1/auth"),
                new BffRoute("/api/v1/members", identityBaseUrl, "/api/v1/members"),
                new BffRoute("/api/v1/dashboard", opsBaseUrl, "/api/v1/dashboard"),
                new BffRoute("/api/v1/ops-alert", opsAlertBaseUrl, "/api/v1/ops-alert"),
                new BffRoute("/api/v1/ops", opsBaseUrl, "/api/v1/ops"),
                new BffRoute("/api/v1/search/ops", searchBaseUrl, "/api/v1/search/ops"),
                new BffRoute("/api/v1/raw-videos", contentBaseUrl, "/api/v1/raw-videos"),
                new BffRoute("/api/v1/unified-videos", contentBaseUrl, "/api/v1/unified-videos"),
                new BffRoute("/api/v1/playlists", contentBaseUrl, "/api/v1/playlists"),
                new BffRoute("/api/v1/actors", contentBaseUrl, "/api/v1/actors"),
                new BffRoute("/api/v1/directors", contentBaseUrl, "/api/v1/directors"),
                new BffRoute("/api/v1/source-domains", contentBaseUrl, "/api/v1/source-domains"),
                new BffRoute("/api/v1/resolvers", contentBaseUrl, "/api/v1/resolvers"),
                new BffRoute("/api/v1/cover-images", storageBaseUrl, "/api/v1/cover-images"),
                new BffRoute("/api/v1/collection-tasks", taskBaseUrl, "/api/v1/collection-tasks"),
                new BffRoute("/api/v1/catalog", catalogBaseUrl, "/api/v1/catalog"),
                new BffRoute("/api/v1/standard-categories", catalogBaseUrl, "/api/v1/standard-categories"),
                new BffRoute("/api/v1/standard-genres", catalogBaseUrl, "/api/v1/standard-genres"),
                new BffRoute("/api/v1/standard-areas", catalogBaseUrl, "/api/v1/standard-areas"),
                new BffRoute("/api/v1/standard-languages", catalogBaseUrl, "/api/v1/standard-languages"),
                new BffRoute("/api/v1/raw-categories", catalogBaseUrl, "/api/v1/raw-categories"),
                new BffRoute("/api/v1/data-sources", catalogBaseUrl, "/api/v1/data-sources"),
                new BffRoute("/api/v1/configs", configBaseUrl, "/api/v1/configs"),
                new BffRoute("/api/v1/common", configBaseUrl, "/api/v1/common"),
                new BffRoute("/api/v1/credentials", credentialBaseUrl, "/api/v1/credentials"),
                new BffRoute("/api/v1/magnet-providers", magnetBaseUrl, "/api/v1/magnet-providers"),
                new BffRoute("/api/v1/magnets", magnetBaseUrl, "/api/v1/magnets"),
                new BffRoute("/api/v1/scraper/manual", scraperBaseUrl, "/api/v1/scraper/manual"),
                new BffRoute("/api/v1/debug", opsBaseUrl, "/api/v1/debug"),
                new BffRoute("/api/v1/messages", interactionBaseUrl, "/api/v1/messages"),
                new BffRoute("/media", storageBaseUrl, "/media")
        ));
    }
}
