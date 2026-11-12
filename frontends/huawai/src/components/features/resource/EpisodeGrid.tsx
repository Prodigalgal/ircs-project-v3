"use client";

import { Episode } from "@/lib/types";
import { cn } from "@/lib/utils";
import { cleanEpisodeTitle } from "./utils";

interface EpisodeGridProps {
    episodes: Episode[];
    isNative: boolean;
    onPlay: (id: string) => void;
}

export function EpisodeGrid({ episodes, isNative, onPlay }: EpisodeGridProps) {
    return (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8 gap-3">
            {episodes.map((ep, idx) => {
                const cleanTitle = cleanEpisodeTitle(ep.title);
                return (
                    <button
                        key={ep.id}
                        onClick={() => onPlay(ep.id)}
                        style={{ animationDelay: `${Math.min(idx * 5, 300)}ms` }}
                        className={cn(
                            "group relative flex items-center justify-center h-12 border rounded-lg transition-all overflow-hidden active:scale-95 animate-in fade-in zoom-in-95 duration-200",
                            isNative
                                ? "bg-card border-border-subtle/10 hover:border-primary/50 hover:bg-secondary/80"
                                : "bg-card border-border-subtle/10 hover:border-border-subtle/30 hover:bg-secondary/80"
                        )}
                    >
                        <span className={cn(
                            "relative z-10 text-xs font-medium truncate px-2 transition-colors select-none",
                            isNative
                                ? "text-muted-foreground group-hover:text-primary font-mono"
                                : "text-muted-foreground group-hover:text-foreground"
                        )}>
                            {cleanTitle}
                        </span>

                        <div className={cn(
                            "absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none",
                            isNative
                                ? "bg-gradient-to-br from-primary/10 via-transparent to-transparent"
                                : "bg-gradient-to-br from-foreground/5 via-transparent to-transparent"
                        )} />
                    </button>
                );
            })}
        </div>
    );
}
