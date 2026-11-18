"use client";

import Link from "next/link";
import { ArrowUpRight, ChevronDown, Flame } from "lucide-react";
import type { MovieCard, TrendSourceStatus } from "@/lib/types";
import { useAutoScroll } from "@/hooks/useAutoScroll";
import { TrendingItem } from "./TrendingItem";
import { cn } from "@/lib/utils";
import { getEmptyTrendingMessage, getProviderName, getTrendSourceFreshnessLabel, getTrendSourceName, getTrendSourceStatusLabel } from "@/lib/domain/rankingPresentation";

interface TrendingSectionProps {
    movies: MovieCard[];
    sources?: TrendSourceStatus[];
}

export function TrendingSection({ movies, sources }: TrendingSectionProps) {
    const { scrollRef, handleScroll } = useAutoScroll({ itemCount: movies.length });
    const freshProviders = [...new Set(sources?.filter(source => source.status === "SUCCESS" && !source.stale
        && source.itemCount > 0).map(source => source.provider))];

    return (
        <div
            className="lg:col-span-3 flex flex-col h-[500px] lg:h-full overflow-hidden gap-3"
        >
            <div className="flex-none flex items-center justify-between px-2 pt-1">
                <div className="flex items-center gap-2">
                    <div className="p-1.5 bg-amber-500/10 rounded-lg text-amber-500 border border-amber-500/20">
                        <Flame className="w-4 h-4 fill-current" />
                    </div>
                    <h3 className="text-base font-bold text-foreground tracking-tight">全网热播</h3>
                </div>
                <Link
                    href="/explore?sort=trending"
                    prefetch={false}
                    className="text-xs font-bold text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors bg-secondary/30 hover:bg-secondary/60 px-2 py-1 rounded-md border border-border-subtle/10"
                >
                    全部热播 <ArrowUpRight className="w-3 h-3" />
                </Link>
            </div>

            {sources && sources.length > 0 ? (
                <details className="group/sources flex-none -mt-2 px-2">
                    <summary className="flex min-h-8 cursor-pointer list-none items-center gap-2 rounded-md px-2 text-xs text-muted-foreground hover:bg-secondary/40 [&::-webkit-details-marker]:hidden">
                        <span className="shrink-0">来源详情</span>
                        <span className="ml-auto truncate text-right">
                            {freshProviders.length ? freshProviders.map(getProviderName).join("、") : "暂无新采样"}
                        </span>
                        <ChevronDown className="h-3.5 w-3.5 shrink-0 transition-transform group-open/sources:rotate-180" aria-hidden="true" />
                    </summary>
                    <ul className="mt-1 max-h-36 space-y-1 overflow-y-auto rounded-md border border-border-subtle/10 p-2 text-[11px] leading-4" aria-label="热播榜来源状态">
                        {sources.map(source => (
                            <li key={`${source.provider}-${source.board}-${source.mediaType}-${source.window}`}
                                title={source.errorCode ?? undefined} className="text-muted-foreground">
                                <span className="text-foreground">{getTrendSourceName(source.provider, source.board)}</span>
                                {` · ${source.mediaType === "movie" ? "电影" : source.mediaType === "tv" ? "剧集" : "全部"} · ${getTrendSourceStatusLabel(source)}`}
                                {source.status === "SUCCESS" ? ` · ${source.itemCount}条` : ""}
                                {getTrendSourceFreshnessLabel(source) ? ` · ${getTrendSourceFreshnessLabel(source)}` : ""}
                            </li>
                        ))}
                    </ul>
                </details>
            ) : (
                <p className="flex-none px-4 text-xs text-muted-foreground">来源状态待提供</p>
            )}

            <div className={cn(
                "flex-1 relative rounded-3xl overflow-hidden group/section",
                "bg-card border border-border-subtle/10"
            )}>
                <div className="absolute top-0 left-0 right-0 h-12 bg-gradient-to-b from-card to-transparent z-20 pointer-events-none" />
                <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-card to-transparent z-20 pointer-events-none" />

                <div className="absolute inset-0 border border-transparent group-hover/section:border-border-subtle/20 rounded-3xl transition-colors duration-500 z-30 pointer-events-none" />

                <div
                    ref={scrollRef}
                    onScroll={handleScroll}
                    className="absolute inset-0 overflow-y-auto px-4 py-4 scrollbar-hide space-y-2"
                    style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
                >
                    {movies.length > 0 ? (
                        <ol className="m-0 list-none space-y-2 p-0" aria-label="全网热播排行">
                            {movies.map((movie, index) => (
                                <li key={movie.id}>
                                    <TrendingItem movie={movie} rank={index + 1} />
                                </li>
                            ))}
                        </ol>
                    ) : (
                        <p className="px-3 py-5 text-sm text-muted-foreground" role="status">
                            {getEmptyTrendingMessage(sources)}
                        </p>
                    )}
                </div>
            </div>

            <style jsx>{`
                .scrollbar-hide::-webkit-scrollbar {
                    display: none;
                }
            `}</style>
        </div>
    );
}
