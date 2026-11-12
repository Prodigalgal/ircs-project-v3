"use client";

import Link from "next/link";
import { Star, Play } from "lucide-react";
import { MovieCard as MovieCardType } from "@/lib/types";
import { cn } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";

interface MovieCardProps {
    movie: MovieCardType;
    className?: string;
    index?: number;
}

export function MovieCard({ movie, className, index = 0 }: MovieCardProps) {
    const seoAlt = `${movie.title} ${movie.season ? `S${movie.season}` : ''} - ${movie.releaseYear} ${movie.categoryName || ''}`;

    return (
        <div
            className={cn(
                "group relative aspect-[2/3] rounded-lg overflow-hidden density-card select-none",
                "animate-in fade-in slide-in-from-bottom-3 duration-500 content-visibility-auto",
                className
            )}
            style={{ animationDelay: `${Math.min(index, 12) * 20}ms` }}
        >
            <Link
                href={`/movie/${movie.id}`}
                prefetch={false}
                className="block w-full h-full relative"
                title={`观看 ${movie.title}`}
            >
                <QuantumImage
                    src={movie.posterUrl}
                    alt={seoAlt}
                    fill
                    sizes="(max-width: 640px) 33vw, (max-width: 1024px) 20vw, 160px"
                    className="object-cover transition-transform duration-500 group-hover:scale-105"
                />

                {/* Season Badge (Top Left) */}
                {movie.season && movie.season > 0 && (
                    <div className="absolute top-1.5 left-1.5 z-30">
                        <span className="px-1.5 py-0.5 rounded text-[9px] font-black text-white bg-indigo-600/90 backdrop-blur-sm border border-indigo-500/20 shadow-sm tracking-tight font-mono">
                            S{movie.season}
                        </span>
                    </div>
                )}

                {/* Remarks Badge (Top Right) */}
                {movie.remarks && (
                    <div className="absolute top-1.5 right-1.5 z-30">
                        <span className="px-1.5 py-0.5 rounded text-[9px] font-bold text-white bg-black/60 backdrop-blur-sm border border-white/10 shadow-sm tracking-tight">
                            {movie.remarks}
                        </span>
                    </div>
                )}

                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/40 to-transparent opacity-90 z-20" />

                <div className="absolute inset-0 p-3 flex flex-col justify-end z-30">
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 scale-90 group-hover:scale-100 transition-all duration-300 ease-out">
                        <div className="w-10 h-10 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center border border-white/20 shadow-xl">
                            <Play className="w-4 h-4 text-white fill-white ml-0.5" />
                        </div>
                    </div>

                    <div className="transform translate-y-1 group-hover:translate-y-0 transition-transform duration-300 ease-out">
                        <h3 className="text-white font-bold text-sm leading-tight line-clamp-1 mb-1 drop-shadow-sm group-hover:text-indigo-300 transition-colors">
                            {movie.title}
                        </h3>

                        {movie.subtitle ? (
                            <p className="text-[10px] text-neutral-300 line-clamp-1 mb-1.5 font-medium">
                                {movie.subtitle}
                            </p>
                        ) : movie.aliasTitle ? (
                            <p className="text-[10px] text-neutral-400 line-clamp-1 mb-1.5 hidden sm:block">
                                {movie.aliasTitle}
                            </p>
                        ) : null}

                        <div className="flex items-center justify-between text-[10px] text-neutral-300 font-medium">
                            <div className="flex items-center gap-2">
                                {movie.rating > 0 ? (
                                    <span className="flex items-center gap-0.5 text-amber-400 font-bold">
                                        <Star className="w-2.5 h-2.5 fill-current" />
                                        {movie.rating}
                                    </span>
                                ) : (
                                    <span className="text-neutral-500">暂无评分</span>
                                )}
                            </div>

                            <div className="flex items-center gap-2">
                                <span>{movie.releaseYear}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="absolute inset-0 border border-border-subtle/10 group-hover:border-border-subtle/30 rounded-lg transition-colors duration-300 z-40 pointer-events-none" />
            </Link>
        </div>
    );
}
