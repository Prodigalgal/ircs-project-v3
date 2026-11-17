import assert from "node:assert/strict";
import test from "node:test";
import { compactInitialHomeData } from "./homeData.ts";
import {
    formatDateOnly,
    formatBeijingDateTime,
    getEmptyTrendingMessage,
    getEventLabel,
    getRankingEpisodeLabel,
    getRankingSourceLabel,
    getResourceAvailabilityLabel,
    getTrendSourceFreshnessLabel,
    getTrendSourceStatusLabel
} from "./rankingPresentation.ts";

const ranking = {
    sources: ["TMDB"],
    observedAt: "2026-11-16T08:30:00Z",
    stale: false,
    eventDate: "2026-11-15",
    eventType: "EPISODE_AIR",
    latestSeason: 2,
    latestEpisode: 8,
    resourceAvailable: true,
    availableEpisode: 6,
    heat: 0
};

const movie = {
    id: "movie-1",
    title: "Example",
    posterUrl: "/poster.jpg",
    rating: 9.2,
    releaseYear: "2026",
    categoryName: "剧集",
    area: "中国大陆",
    ranking
};

test("home compaction keeps ranking data and limits recent updates", () => {
    const compacted = compactInitialHomeData({
        spotlight: [movie],
        trending: [movie],
        sections: [],
        recentlyUpdated: [movie, { ...movie, id: "movie-2" }],
        trendSources: []
    });

    assert.deepEqual(compacted.trending[0]?.ranking, ranking);
    assert.deepEqual(compacted.recentlyUpdated?.[0]?.ranking, ranking);
    assert.equal(compacted.recentlyUpdated?.length, 2);
});

test("legacy home payload without new optional fields remains usable", () => {
    const compacted = compactInitialHomeData({ spotlight: [movie], trending: [movie], sections: [] });

    assert.deepEqual(compacted.recentlyUpdated, []);
    assert.deepEqual(compacted.trendSources, []);
});

test("ranking presentation distinguishes source, event, broadcast and local resource state", () => {
    assert.equal(formatDateOnly("2026-11-15T18:00:00Z"), "2026-11-15");
    assert.equal(formatBeijingDateTime("2026-11-16T08:30:00Z"), "11-16 16:30");
    assert.equal(getEventLabel("EPISODE_AIR", "2026-11-15"), "新集播出 · 2026-11-15");
    assert.equal(getRankingSourceLabel(ranking), "TMDB · 采样北京时间 11-16 16:30");
    assert.equal(getRankingEpisodeLabel(ranking), "全网已播到第2季第8集");
    assert.equal(getResourceAvailabilityLabel(ranking), "本站最高已收录第6集");
    assert.equal(getResourceAvailabilityLabel({ ...ranking, resourceAvailable: false }), "资源收集中");
});

test("source failures and stale state stay visible when a list is empty", () => {
    const failedSource = {
        provider: "TMDB",
        board: "Trending TV",
        mediaType: "tv",
        window: "week",
        status: "FAILED",
        errorCode: "UPSTREAM_ERROR",
        lastAttemptAt: "2026-11-16T08:30:00Z",
        lastSuccessAt: "2026-11-15T08:30:00Z",
        itemCount: 12,
        stale: true
    } as const;

    assert.equal(getTrendSourceStatusLabel(failedSource), "采样失败 · 数据已过期");
    assert.equal(getTrendSourceFreshnessLabel(failedSource), "尝试北京时间 11-16 16:30 · 上次成功北京时间 11-15 16:30");
    assert.equal(getEmptyTrendingMessage([failedSource]), "来源采集失败，暂时没有可验证的热播榜单");
    assert.equal(getEmptyTrendingMessage(undefined), "榜单来源状态暂不可用，无法确认是否有新榜单");
});
