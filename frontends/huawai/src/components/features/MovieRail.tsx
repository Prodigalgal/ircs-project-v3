"use client";

import { CategorySection } from "@/lib/types";
import { MovieCard } from "./MovieCard";
import { memo, useCallback, useMemo, useRef } from "react";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { cn } from "@/lib/utils";

interface MovieRailProps {
    category: CategorySection;
}

function MovieRailComponent({ category }: MovieRailProps) {
    const containerRef = useRef<HTMLDivElement>(null);

    const movies = useMemo(() => category.movies || [], [category.movies]);

    const scroll = useCallback((direction: "left" | "right") => {
        const container = containerRef.current;
        if (!container) return;

        const scrollAmount = container.clientWidth * 0.82;
        container.scrollBy({
            left: direction === "left" ? -scrollAmount : scrollAmount,
            behavior: "smooth"
        });
    }, []);

    if (movies.length === 0) return null;

    return (
        <div className="space-y-4 py-8 relative group/rail overflow-hidden select-none">
            {/* Header Area */}
            <div className="px-6 md:px-10 flex items-center justify-between mb-2">
                <div className="flex items-center gap-3 animate-in fade-in slide-in-from-left-2 duration-500">
                    <div className="w-1 h-5 bg-primary rounded-full shadow-[0_0_12px_rgba(99,102,241,0.5)]" />
                    <h2 className="text-xl font-bold text-foreground tracking-tight text-shadow-sm">
                        {category.title}
                    </h2>
                </div>

                <div className="hidden md:block text-[10px] font-mono text-muted-foreground tracking-widest uppercase">
                    Rail-ID: {category.id.substring(0, 4)}
                </div>
            </div>

            {/* Scroll Container */}
            <div className="relative group/content">
                <div
                    ref={containerRef}
                    className={cn(
                        "flex gap-3 md:gap-4 px-6 md:px-10 touch-pan-x relative z-10 py-2",
                        "overflow-x-auto overflow-y-hidden scroll-smooth snap-x snap-mandatory scrollbar-none"
                    )}
                    style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
                >
                    {movies.map((movie, idx) => (
                        <div key={movie.id} className="w-[130px] sm:w-[160px] md:w-[190px] flex-none snap-start">
                            <MovieCard movie={movie} index={idx} />
                        </div>
                    ))}
                </div>

                {/*
                   视觉边界 - 使用 from-background 适配双色主题
                */}
                <div
                    className={cn(
                        "absolute left-0 top-0 bottom-0 w-32 z-30 pointer-events-none flex items-center justify-start pl-4 transition-opacity duration-500",
                        "bg-gradient-to-r from-background via-background/90 to-transparent",
                        "opacity-0 group-hover/rail:opacity-100"
                    )}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            scroll("left");
                        }}
                        className="pointer-events-auto p-3 rounded-full bg-card/80 backdrop-blur-md border border-border-subtle/10 text-foreground hover:bg-foreground hover:text-background transition-all shadow-xl hover:scale-110 active:scale-95 hidden md:flex"
                        aria-label="Previous"
                    >
                        <ChevronLeft className="w-5 h-5" />
                    </button>
                </div>

                {/* Right Boundary */}
                <div
                    className={cn(
                        "absolute right-0 top-0 bottom-0 w-32 z-30 pointer-events-none flex items-center justify-end pr-4 transition-opacity duration-500",
                        "bg-gradient-to-l from-background via-background/90 to-transparent",
                        "opacity-0 group-hover/rail:opacity-100"
                    )}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            scroll("right");
                        }}
                        className="pointer-events-auto p-3 rounded-full bg-card/80 backdrop-blur-md border border-border-subtle/10 text-foreground hover:bg-foreground hover:text-background transition-all shadow-xl hover:scale-110 active:scale-95 hidden md:flex"
                        aria-label="Next"
                    >
                        <ChevronRight className="w-5 h-5" />
                    </button>
                </div>
            </div>
        </div>
    );
}

export const MovieRail = memo(MovieRailComponent);
