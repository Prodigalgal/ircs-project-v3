"use client";

import Link from "next/link";
import { Play, Star, Clock3 } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { cn } from "@/lib/utils";
import { getRankingEpisodeLabel, getRankingSourceLabel, getResourceAvailabilityLabel } from "@/lib/domain/rankingPresentation";

interface TrendingItemProps {
    movie: MovieCard;
    rank: number;
}

export function TrendingItem({ movie, rank }: TrendingItemProps) {
    const rankStr = rank < 10 ? `0${rank}` : `${rank}`;

    return (
        <Link
            href={`/movie/${movie.id}`}
            prefetch={false}
            aria-label={`全网热播第${rank}位：${movie.title}`}
            className="block relative p-2.5 rounded-xl border border-transparent hover:border-border-subtle/10 hover:bg-secondary/50 transition-all group/item select-none"
        >
            <div className="flex items-center gap-3">
                <div className="relative w-[48px] aspect-[2/3] rounded-md overflow-hidden shadow-lg border border-border-subtle/10 flex-shrink-0 bg-neutral-900">
                    <QuantumImage src={movie.posterUrl} alt={movie.title} fill className="object-cover" />
                    <div className={cn(
                        "absolute top-0 left-0 px-1 py-[1px] text-[8px] font-bold text-white rounded-br-md backdrop-blur-md",
                        rank <= 3 ? "bg-red-600/90" : "bg-neutral-600/80"
                    )}>
                        {rankStr}
                    </div>
                </div>

                <div className="flex-1 min-w-0 flex flex-col justify-center">
                    {/* [REF_CHANGE]: text-foreground */}
                    <h4 className="text-sm font-bold text-foreground/90 group-hover/item:text-primary transition-colors line-clamp-1 mb-1">
                        {movie.title}
                    </h4>

                    <div className="flex items-center gap-2 mb-1">
                        {movie.categoryName && (
                            <span className="text-[9px] text-muted-foreground bg-secondary px-1 rounded border border-border-subtle/10">
                                {movie.categoryName}
                            </span>
                        )}
                        {movie.area && (
                            <span className="text-[9px] text-muted-foreground truncate max-w-[60px]">
                                {movie.area}
                            </span>
                        )}
                    </div>

                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[9px] text-muted-foreground font-mono">
                        {movie.rating != null ? (
                            <span className="flex items-center gap-0.5 text-amber-500 font-bold" aria-label={`评分 ${movie.rating}`}>
                                <Star className="w-2.5 h-2.5 fill-current" /> {movie.rating}
                            </span>
                        ) : (
                            <span>暂无评分</span>
                        )}
                        <span>{movie.releaseYear}</span>
                    </div>
                    <p className="mt-1 text-[9px] leading-4 text-muted-foreground/90 line-clamp-1">
                        {getRankingSourceLabel(movie.ranking)}{movie.ranking?.stale ? " · 条目趋势已过期" : ""}
                    </p>
                    {getRankingEpisodeLabel(movie.ranking) && (
                        <p className="text-[9px] leading-4 text-sky-700 dark:text-sky-300 line-clamp-1">
                            {getRankingEpisodeLabel(movie.ranking)}
                        </p>
                    )}
                    <p className={cn(
                        "text-[9px] leading-4 line-clamp-1",
                        movie.ranking?.resourceAvailable === false
                            ? "text-amber-700 dark:text-amber-300"
                            : "text-muted-foreground"
                    )}>
                        {getResourceAvailabilityLabel(movie.ranking)}
                    </p>
                </div>

                <div className="w-6 h-6 rounded-full bg-primary text-primary-foreground flex items-center justify-center opacity-0 group-hover/item:opacity-100 transition-all transform scale-50 group-hover/item:scale-100">
                    {movie.ranking?.resourceAvailable === true
                        ? <Play className="w-2.5 h-2.5 fill-current ml-0.5" aria-hidden="true" />
                        : <Clock3 className="w-2.5 h-2.5" aria-hidden="true" />}
                </div>
            </div>
        </Link>
    );
}
