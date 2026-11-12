"use client";

import { useEffect, useState } from "react";
import { MovieCard } from "@/lib/types";
import { contentApi } from "@/lib/api/content";
import { MovieRail } from "@/components/features/MovieRail";

interface RelatedMoviesRailProps {
    movieId: string;
    movieTitle: string;
    genre?: string;
    category?: string;
}

export function RelatedMoviesRail({ movieId, movieTitle, genre, category }: RelatedMoviesRailProps) {
    const [movies, setMovies] = useState<MovieCard[]>([]);

    useEffect(() => {
        let cancelled = false;
        contentApi.explore({
            page: 0,
            size: 12,
            genre: genre || undefined,
            category: category || undefined
        })
            .then(result => {
                if (cancelled) {
                    return;
                }
                setMovies((result.content || []).filter(movie => movie.id !== movieId));
            })
            .catch(() => {
                if (!cancelled) {
                    setMovies([]);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [category, genre, movieId]);

    if (movies.length === 0) {
        return null;
    }

    return (
        <div className="max-w-[1600px] mx-auto px-0 md:px-4 mb-16 relative z-10">
            <div className="px-6 md:px-8 flex items-center gap-3 mb-2">
                <div className="w-1.5 h-6 bg-emerald-500 rounded-full shadow-[0_0_15px_#10b981]" />
                <h2 className="text-2xl font-bold text-foreground tracking-wide">
                    喜欢《{movieTitle}》的人也爱看
                </h2>
            </div>
            <MovieRail category={{ id: "related", title: "", movies }} />
        </div>
    );
}
