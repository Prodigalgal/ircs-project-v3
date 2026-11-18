import type { MovieRanking, TrendSourceStatus } from "@/lib/types";

const PROVIDER_LABELS: Record<string, string> = {
    TMDB: "TMDB",
    Douban: "豆瓣",
    RottenTomatoes: "烂番茄",
    IMDb: "IMDb"
};

const SOURCE_STATUS_LABELS: Record<TrendSourceStatus["status"], string> = {
    SUCCESS: "采样成功",
    FAILED: "采样失败",
    UNAVAILABLE: "来源不可用",
    DISABLED: "未启用",
    DEFERRED: "待采样"
};

const EVENT_TYPE_LABELS: Record<string, string> = {
    RELEASE: "上映/发行",
    DIGITAL_RELEASE: "流媒体上线",
    EPISODE_AIR: "新集播出"
};

export function formatDateOnly(value: string | null | undefined): string | null {
    if (!value) return null;
    const dateOnly = value.match(/^\d{4}-\d{2}-\d{2}/)?.[0];
    return dateOnly ?? null;
}

export function formatBeijingDateTime(value: string | null | undefined): string | null {
    if (!value) return null;
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;

    const parts = new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Shanghai",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23"
    }).formatToParts(date);
    const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
    return `${values.month}-${values.day} ${values.hour}:${values.minute}`;
}

export function getTrendSourceName(provider: string, board: string): string {
    const boardLabels: Record<string, string> = {
        "trending-day": "日榜", "trending-week": "周榜", chart: "排行榜", popular: "热门榜",
        "airing-today": "今日播出", "recent-release": "近期上映", legacy: "采样", provider: "采样"
    };
    return `${getProviderName(provider)} · ${boardLabels[board] ?? "榜单"}`;
}

export function getProviderName(provider: string): string {
    return PROVIDER_LABELS[provider] ?? provider;
}

export function getTrendSourceStatusLabel(source: TrendSourceStatus): string {
    const statusLabel = SOURCE_STATUS_LABELS[source.status] ?? "状态未知";
    return source.stale ? `${statusLabel} · 数据已过期` : statusLabel;
}

export function getTrendSourceFreshnessLabel(source: TrendSourceStatus): string | null {
    const successDateTime = formatBeijingDateTime(source.lastSuccessAt);
    const attemptDateTime = formatBeijingDateTime(source.lastAttemptAt);

    if (source.status === "SUCCESS" && successDateTime) return `采样北京时间 ${successDateTime}`;
    if (attemptDateTime && successDateTime) {
        return `尝试北京时间 ${attemptDateTime} · 上次成功北京时间 ${successDateTime}`;
    }
    if (successDateTime) return `上次成功北京时间 ${successDateTime}`;
    if (attemptDateTime) return `尝试北京时间 ${attemptDateTime}`;
    return null;
}

export function getRankingSourceLabel(ranking: MovieRanking | undefined): string {
    if (!ranking) return "来源信息待提供";
    if (ranking.sources.length === 0) return "来源信息待提供";

    const observedDateTime = formatBeijingDateTime(ranking.observedAt);
    const sourceNames = ranking.sources.map(getProviderName).join(" · ");
    return observedDateTime ? `${sourceNames} · 采样北京时间 ${observedDateTime}` : sourceNames;
}

export function getRankingEpisodeLabel(ranking: MovieRanking | undefined): string | null {
    if (!ranking || ranking.latestEpisode == null) return null;

    if (ranking.latestSeason != null && ranking.latestSeason > 0) {
        return `全网已播到第${ranking.latestSeason}季第${ranking.latestEpisode}集`;
    }
    return `全网已播到第${ranking.latestEpisode}集`;
}

export function getResourceAvailabilityLabel(ranking: MovieRanking | undefined): string {
    if (!ranking) return "本站资源状态待核实";
    if (!ranking.resourceAvailable) return "资源收集中";
    if (ranking.availableEpisode != null) return `本站最高已收录第${ranking.availableEpisode}集`;
    return "本站已有资源";
}

export function getEventLabel(eventType: string | null | undefined, eventDate: string | null | undefined): string | null {
    const date = formatDateOnly(eventDate);
    if (!date) return null;
    const eventLabel = eventType ? EVENT_TYPE_LABELS[eventType] ?? "内容更新" : "内容更新";
    return `${eventLabel} · ${date}`;
}

export function getEmptyTrendingMessage(sources: TrendSourceStatus[] | undefined): string {
    if (!sources || sources.length === 0) return "榜单来源状态暂不可用，无法确认是否有新榜单";

    const allSourcesUnavailable = sources.every(source =>
        source.status === "FAILED" || source.status === "UNAVAILABLE"
    );
    if (allSourcesUnavailable) return "来源采集失败，暂时没有可验证的热播榜单";
    if (sources.some(source => source.stale)) return "榜单来源数据已过期，当前没有可验证的全网热播条目";
    if (sources.some(source => source.status === "FAILED" || source.status === "UNAVAILABLE")) {
        return "部分来源采集失败，当前榜单可能不完整";
    }
    if (!sources.some(source => source.status === "SUCCESS")) {
        return "来源尚未成功采样，暂时没有可验证的热播榜单";
    }
    return "当前暂无可展示的全网热播条目";
}
