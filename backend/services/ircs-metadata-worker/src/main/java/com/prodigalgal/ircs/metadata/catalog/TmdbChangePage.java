package com.prodigalgal.ircs.metadata.catalog;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

record TmdbChangePage(List<Long> ids, boolean last) {

    static URI uri(String baseUrl, String apiKey, String mediaType, LocalDate day, int page) {
        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment(mediaType, "changes")
                .queryParam("api_key", apiKey).queryParam("start_date", day)
                .queryParam("end_date", day.plusDays(1)).queryParam("page", page).build().encode().toUri();
    }

    static TmdbChangePage from(JsonNode response, int requestedPage) {
        int page = response.path("page").asInt(-1);
        int totalPages = response.path("total_pages").asInt(-1);
        JsonNode results = response.path("results");
        if (page != requestedPage || totalPages < 0 || totalPages > 5000 || !results.isArray()
                || results.size() > 100 || (totalPages < page && !(page == 1 && totalPages == 0 && results.isEmpty()))) {
            throw new CatalogMetadataUnavailableException("INVALID_CHANGES_PAGE");
        }
        var ids = new LinkedHashSet<Long>();
        for (JsonNode result : results) {
            long id = result.path("id").asLong(0);
            if (id <= 0) throw new CatalogMetadataUnavailableException("INVALID_CHANGES_ID");
            ids.add(id);
        }
        return new TmdbChangePage(List.copyOf(ids), page >= totalPages);
    }
}
