"use client";

import { ChevronRight, ChevronLeft } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useCarousel } from "@/hooks/useCarousel";
import { SpotlightCard } from "./SpotlightCard";

interface SpotlightSectionProps {
    movies: MovieCard[];
}

const CAROUSEL_INTERVAL = 8000;

export function SpotlightSection({ movies }: SpotlightSectionProps) {
    const {
        currentIndex,
        setIsPaused,
        nextSlide,
        prevSlide,
        goToSlide
    } = useCarousel({
        totalItems: movies.length,
        interval: CAROUSEL_INTERVAL
    });

    const currentMovie = movies[currentIndex];

    if (!currentMovie) return null;

    return (
        <div
            className={cn(
                "lg:col-span-9 relative rounded-3xl overflow-hidden group shadow-2xl h-[550px] lg:h-full select-none",
                "bg-card border border-border-subtle/10"
            )}
            onMouseEnter={() => setIsPaused(true)}
            onMouseLeave={() => setIsPaused(false)}
        >
            <div className="absolute inset-0 border border-transparent group-hover:border-border-subtle/20 rounded-3xl transition-colors duration-500 z-50 pointer-events-none" />

            <div key={currentMovie.id} className="absolute inset-0 w-full h-full bg-card animate-in fade-in duration-700">
                <SpotlightCard
                    movie={currentMovie}
                    index={currentIndex}
                    total={movies.length}
                    duration={CAROUSEL_INTERVAL}
                />
            </div>

            <div className="absolute inset-y-0 left-0 w-28 flex items-center justify-start pl-6 opacity-0 group-hover:opacity-100 transition-opacity duration-500 z-40 pointer-events-none bg-gradient-to-r from-background/80 to-transparent">
                <button
                    onClick={(e) => { e.preventDefault(); prevSlide(); }}
                    className="pointer-events-auto p-3 rounded-full bg-secondary/80 backdrop-blur-xl border border-border-subtle/20 hover:bg-foreground hover:text-background text-foreground transition-all shadow-2xl hover:scale-110 active:scale-95"
                >
                    <ChevronLeft className="w-6 h-6" />
                </button>
            </div>

            <div className="absolute inset-y-0 right-0 w-28 flex items-center justify-end pr-6 opacity-0 group-hover:opacity-100 transition-opacity duration-500 z-40 pointer-events-none bg-gradient-to-l from-background/80 to-transparent">
                <button
                    onClick={(e) => { e.preventDefault(); nextSlide(); }}
                    className="pointer-events-auto p-3 rounded-full bg-secondary/80 backdrop-blur-xl border border-border-subtle/20 hover:bg-foreground hover:text-background text-foreground transition-all shadow-2xl hover:scale-110 active:scale-95"
                >
                    <ChevronRight className="w-6 h-6" />
                </button>
            </div>

            <div className="absolute bottom-6 left-6 md:left-10 z-50 flex gap-2">
                {movies.map((_, idx) => (
                    <button
                        key={idx}
                        onClick={(e) => { e.preventDefault(); goToSlide(idx); }}
                        className="group/dot p-2 -m-2 cursor-pointer"
                    >
                        <div className={cn(
                            "h-1 rounded-full transition-all duration-500 ease-out shadow-sm",
                            idx === currentIndex
                                ? "w-8 bg-foreground shadow-[0_0_8px_currentColor]"
                                : "w-2 bg-foreground/20 group-hover/dot:bg-foreground/40"
                        )} />
                    </button>
                ))}
            </div>
        </div>
    );
}
