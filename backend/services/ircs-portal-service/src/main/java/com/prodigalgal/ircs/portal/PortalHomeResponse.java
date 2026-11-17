package com.prodigalgal.ircs.portal;

import java.util.List;

public record PortalHomeResponse(
        List<PortalMovieCard> spotlight,
        List<PortalMovieCard> trending,
        List<PortalCategorySection> sections,
        List<PortalMovieCard> recentlyUpdated,
        List<PortalTrendSourceState> trendSources) {

    public PortalHomeResponse {
        recentlyUpdated = recentlyUpdated == null ? List.of() : List.copyOf(recentlyUpdated);
        trendSources = trendSources == null ? List.of() : List.copyOf(trendSources);
    }

    public PortalHomeResponse(List<PortalMovieCard> spotlight, List<PortalMovieCard> trending,
            List<PortalCategorySection> sections) {
        this(spotlight, trending, sections, List.of(), List.of());
    }
}
