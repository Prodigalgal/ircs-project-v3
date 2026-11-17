import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import type { MovieCard } from "@/lib/types";
import { MovieCard as PosterCard } from "@/components/features/MovieCard";
import { getEventLabel, getRankingEpisodeLabel, getRankingSourceLabel, getResourceAvailabilityLabel } from "@/lib/domain/rankingPresentation";

interface GlobalUpdatesRailProps {
    movies: MovieCard[];
}

export function GlobalUpdatesRail({ movies }: GlobalUpdatesRailProps) {
    return (
        <section className="space-y-4 py-8 relative group/rail overflow-hidden select-none" aria-labelledby="global-updates-title">
            <div className="px-6 md:px-10 flex items-center justify-between mb-2">
                <div className="flex items-center gap-3">
                    <div className="w-1 h-5 bg-primary rounded-full shadow-[0_0_12px_rgba(99,102,241,0.5)]" />
                    <h2 id="global-updates-title" className="text-xl font-bold text-foreground tracking-tight text-shadow-sm">
                        全网最近更新
                    </h2>
                </div>
                <Link
                    href="/explore?sort=global-latest"
                    prefetch={false}
                    className="min-h-11 inline-flex items-center gap-1 rounded-md border border-border-subtle/10 bg-secondary/30 px-3 py-2 text-xs font-bold text-muted-foreground transition-colors hover:bg-secondary/60 hover:text-foreground"
                >
                    更多更新 <ArrowUpRight className="w-3 h-3" aria-hidden="true" />
                </Link>
            </div>

            <div className="flex gap-3 overflow-x-auto overflow-y-hidden px-6 py-2 md:gap-4 md:px-10 snap-x snap-mandatory touch-pan-x" style={{ scrollbarWidth: "none" }}>
                {movies.length === 0 ? (
                    <p className="w-full py-8 text-sm text-muted-foreground" role="status">
                        当前暂无可核实的全网更新条目
                    </p>
                ) : movies.map((movie, index) => {
                    const eventLabel = getEventLabel(movie.ranking?.eventType, movie.ranking?.eventDate);
                    const episodeLabel = getRankingEpisodeLabel(movie.ranking);

                    return (
                        <article key={movie.id} className="w-[130px] flex-none snap-start sm:w-[160px] md:w-[190px]">
                            <PosterCard movie={movie} index={index} />
                            <div className="space-y-1 px-1 pt-2">
                                <p className="min-h-4 text-[10px] font-semibold leading-4 text-foreground/90">
                                    {eventLabel ?? "更新日期待核实"}
                                </p>
                                {episodeLabel && (
                                    <p className="text-[9px] leading-4 text-sky-700 dark:text-sky-300 line-clamp-1">{episodeLabel}</p>
                                )}
                                <p className="text-[9px] leading-4 text-muted-foreground line-clamp-1">
                                    {getRankingSourceLabel(movie.ranking)}
                                </p>
                                <p className={`text-[9px] leading-4 line-clamp-1 ${movie.ranking?.resourceAvailable === false ? "text-amber-700 dark:text-amber-300" : "text-muted-foreground"}`}>
                                    {getResourceAvailabilityLabel(movie.ranking)}
                                </p>
                            </div>
                        </article>
                    );
                })}
            </div>
        </section>
    );
}
