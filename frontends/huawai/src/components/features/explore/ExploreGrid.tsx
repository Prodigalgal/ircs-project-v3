"use client";

import { Search } from "lucide-react";
import { MovieCard } from "@/components/features/MovieCard";
import { MovieCard as MovieCardType } from "@/lib/types";

interface ExploreGridProps {
    isLoading: boolean;
    results: MovieCardType[];
    query: string;
    onClearAll: () => void;
}

export function ExploreGrid({ isLoading, results, query, onClearAll }: ExploreGridProps) {
    // 骨架屏：密度也需要匹配新的 Grid
    if (isLoading) {
        return (
            <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-3 md:gap-4">
                {Array.from({ length: 21 }).map((_, i) => (
                    <div key={i} className="aspect-[2/3] bg-white/5 rounded-lg border border-white/5 animate-pulse" />
                ))}
            </div>
        );
    }

    return (
        <div
            className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-x-3 gap-y-6 md:gap-x-4 md:gap-y-8"
        >
            {results.length > 0 ? (
                results.map((movie, idx) => (
                    <MovieCard key={movie.id} movie={movie} index={idx} />
                ))
            ) : (
                <div className="col-span-full py-32 flex flex-col items-center justify-center text-center animate-in fade-in duration-700 border border-dashed border-white/5 rounded-3xl bg-white/[0.01]">
                    <div className="w-20 h-20 bg-gradient-to-tr from-white/5 to-white/10 rounded-full flex items-center justify-center mb-6 border border-white/5">
                        <Search className="w-8 h-8 text-neutral-600" />
                    </div>
                    <h3 className="text-lg font-bold text-white mb-2">信号丢失</h3>
                    <p className="text-neutral-500 max-w-xs mx-auto mb-8 text-sm">
                        {query ? `未侦测到与 "${query}" 匹配的信号。` : "当前坐标系下无内容。"}
                    </p>
                    <button
                        onClick={onClearAll}
                        className="px-6 py-2.5 bg-white text-black text-xs font-bold rounded-full hover:bg-neutral-200 transition-colors"
                    >
                        重置雷达
                    </button>
                </div>
            )}
        </div>
    );
}
