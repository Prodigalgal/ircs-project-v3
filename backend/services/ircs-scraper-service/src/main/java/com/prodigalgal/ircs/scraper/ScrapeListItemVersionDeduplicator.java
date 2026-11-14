package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.scraper.ScraperDtos.ListItem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Deduplicates only list entries that identify the same source version with an explicit update time. */
final class ScrapeListItemVersionDeduplicator {

    static final int MAX_RETAINED_VERSIONS = 10_000;
    static final int MAX_SOURCE_VID_LENGTH = 256;
    static final int MAX_UPDATE_TIME_LENGTH = 64;

    private final int capacity;
    private final Set<Version> seenVersions = new LinkedHashSet<>();

    ScrapeListItemVersionDeduplicator() {
        this(MAX_RETAINED_VERSIONS);
    }

    ScrapeListItemVersionDeduplicator(int capacity) {
        if (capacity < 1 || capacity > MAX_RETAINED_VERSIONS) {
            throw new IllegalArgumentException("deduplicator capacity must be between 1 and " + MAX_RETAINED_VERSIONS);
        }
        this.capacity = capacity;
    }

    List<ListItem> keepUnseenVersions(List<ListItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ListItem> kept = new ArrayList<>(items.size());
        for (ListItem item : items) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.id()) || !StringUtils.hasText(item.updateTime())) {
                kept.add(item);
                continue;
            }
            String sourceVid = item.id().trim();
            String updateTime = item.updateTime().trim();
            if (sourceVid.length() > MAX_SOURCE_VID_LENGTH || updateTime.length() > MAX_UPDATE_TIME_LENGTH) {
                kept.add(item);
                continue;
            }
            Version version = new Version(sourceVid, updateTime);
            if (seenVersions.contains(version)) {
                continue;
            }
            seenVersions.add(version);
            kept.add(item);
            evictOldestVersions();
        }
        return List.copyOf(kept);
    }

    private void evictOldestVersions() {
        while (seenVersions.size() > capacity) {
            Iterator<Version> iterator = seenVersions.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private record Version(String sourceVid, String updateTime) {
    }
}
