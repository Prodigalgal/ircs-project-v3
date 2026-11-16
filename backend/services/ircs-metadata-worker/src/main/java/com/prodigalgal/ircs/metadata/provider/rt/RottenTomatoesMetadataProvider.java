package com.prodigalgal.ircs.metadata.provider.rt;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProvider;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataCandidateTitles;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataEnrichmentPolicy;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class RottenTomatoesMetadataProvider implements MetadataProvider {

    private final RottenTomatoesCredentialRepository credentialRepository;
    private final RottenTomatoesHttpClient httpClient;
    private final RottenTomatoesProviderProperties properties;

    @Override
    public ProviderType getType() {
        return ProviderType.ROTTEN_TOMATOES;
    }

    @Override
    public boolean supports(MetadataSearchContext context) {
        return MetadataEnrichmentPolicy.supports(context, getType());
    }

    @Override
    public Optional<EnrichedMetadataDTO> enrich(MetadataSearchContext context) {
        if (!supports(context)) {
            return Optional.empty();
        }
        Optional<RottenTomatoesCredential> credential = credentialRepository.findPreferred();
        Optional<String> response = httpClient.getHtml(buildSearchUri(context), credential);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        return mapFirstMatch(context, response.get());
    }

    private URI buildSearchUri(MetadataSearchContext context) {
        return UriComponentsBuilder.fromUriString(properties.getSearchUrl())
                .queryParam("search", context.getFullTitle())
                .build()
                .encode()
                .toUri();
    }

    private Optional<EnrichedMetadataDTO> mapFirstMatch(MetadataSearchContext context, String html) {
        try {
            Document document = Jsoup.parse(html);
            Elements links = document.select("search-page-media-row a[slot=title]");
            if (links.isEmpty()) {
                links = document.select("ul[slot=list] li a");
            }
            for (Element link : links) {
                String title = link.text();
                String id = AuthoritativeIds.normalizeRottenTomatoes(extractIdFromUrl(link.attr("href")));
                if (StringUtils.hasText(id) && matchesMediaType(context, link.attr("href"))
                        && MetadataCandidateTitles.matches(context, title)) {
                    EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
                    metadata.setRottenTomatoesId(id);
                    return Optional.of(metadata);
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            throw new MetadataProviderRetryableException("PROVIDER_PARSE_ERROR", "Unable to parse Rotten Tomatoes search response", ex);
        }
    }

    private boolean matchesMediaType(MetadataSearchContext context, String href) {
        String path;
        try {
            path = URI.create(href).getPath();
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (path == null || (!path.startsWith("/m/") && !path.startsWith("/tv/"))) {
            return false;
        }
        boolean television = "tv".equals(MetadataEnrichmentPolicy.searchType(context));
        return television ? path.startsWith("/tv/")
                : !"movie".equals(context.getCategorySlug()) || path.startsWith("/m/");
    }

    private String extractIdFromUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }
        try {
            String normalized = href.trim();
            if (!normalized.startsWith("http")) {
                normalized = "https://www.rottentomatoes.com" + (normalized.startsWith("/") ? "" : "/") + normalized;
            }
            String path = URI.create(normalized).getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String[] segments = path.replaceFirst("/+$", "").split("/");
            return segments.length == 0 ? null : segments[segments.length - 1];
        } catch (Exception ignored) {
            return null;
        }
    }

}
