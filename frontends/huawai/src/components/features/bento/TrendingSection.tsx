"use client";

import { useMemo } from "react";
import { useSyncExternalStore } from "react";
import Link from "next/link";
import { ArrowUpRight, Flame } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { useAutoScroll } from "@/hooks/useAutoScroll";
import { TrendingItem } from "./TrendingItem";
import { cn } from "@/lib/utils";

interface TrendingSectionProps {
    movies: MovieCard[];
}

const subscribeToHydration = () => () => {};
const getHydratedSnapshot = () => true;
const getServerSnapshot = () => false;

export function TrendingSection({ movies }: TrendingSectionProps) {
    const hasMounted = useSyncExternalStore(
        subscribeToHydration,
        getHydratedSnapshot,
        getServerSnapshot
    );

    const loopedMovies = useMemo(() => {
        if (movies.length === 0) return [];
        if (!hasMounted) {
            return movies.map(m => ({ ...m, uniqueId: `${m.id}-trend-0` }));
        }

        const minItems = 15;
        let repeat = Math.ceil(minItems / movies.length);
        if (repeat < 2) repeat = 2;

        return Array.from({ length: repeat }).flatMap((_, loopIdx) =>
            movies.map(m => ({ ...m, uniqueId: `${m.id}-trend-${loopIdx}` }))
        );
    }, [movies, hasMounted]);

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
                    <h3 className="text-base font-bold text-foreground tracking-tight">时下热门</h3>
                </div>
                <Link
                    href="/explore?sort=trending"
                    prefetch={false}
                    className="text-xs font-bold text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors bg-secondary/30 hover:bg-secondary/60 px-2 py-1 rounded-md border border-border-subtle/10"
                >
                    完整榜单 <ArrowUpRight className="w-3 h-3" />
                </Link>
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
                    {loopedMovies.map((movie, index) => {
                        const realRank = (index % movies.length) + 1;
                        return (
                            <TrendingItem
                                key={movie.uniqueId}
                                movie={movie}
                                rank={realRank}
                            />
                        );
                    })}
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
