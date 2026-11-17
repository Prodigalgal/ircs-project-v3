"use client";

import Link from "next/link";
import { ArrowUpRight, Flame } from "lucide-react";
import type { MovieCard, TrendSourceStatus } from "@/lib/types";
import { useAutoScroll } from "@/hooks/useAutoScroll";
import { TrendingItem } from "./TrendingItem";
import { cn } from "@/lib/utils";
import { getEmptyTrendingMessage, getTrendSourceFreshnessLabel, getTrendSourceName, getTrendSourceStatusLabel } from "@/lib/domain/rankingPresentation";

interface TrendingSectionProps {
    movies: MovieCard[];
    sources?: TrendSourceStatus[];
}

export function TrendingSection({ movies, sources }: TrendingSectionProps) {
    const { scrollRef, handleScroll } = useAutoScroll({ itemCount: movies.length });

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

            <div className="flex-none px-2 -mt-2 flex flex-wrap gap-1.5" aria-label="热播榜来源状态">
                {sources && sources.length > 0 ? sources.map(source => (
                    <span
                        key={`${source.provider}-${source.board}-${source.mediaType}-${source.window}`}
                        title={`${source.mediaType} · ${source.window}${source.errorCode ? ` · ${source.errorCode}` : ""}`}
                        className={cn(
                            "text-[9px] leading-4 px-1.5 rounded border border-border-subtle/10 bg-secondary/30 text-muted-foreground",
                            source.status === "SUCCESS" && !source.stale && "text-emerald-600 dark:text-emerald-400",
                            (source.status === "FAILED" || source.status === "UNAVAILABLE" || source.stale) && "text-amber-600 dark:text-amber-400"
                        )}
                    >
                        {getTrendSourceName(source.provider, source.board)} · {source.mediaType}/{source.window} · {getTrendSourceStatusLabel(source)}
                        {getTrendSourceFreshnessLabel(source) ? ` · ${getTrendSourceFreshnessLabel(source)}` : ""}
                        {source.status === "SUCCESS" ? ` · ${source.itemCount}条` : ""}
                    </span>
                )) : (
                    <span className="text-[9px] leading-4 text-muted-foreground">来源状态待提供</span>
                )}
            </div>

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
