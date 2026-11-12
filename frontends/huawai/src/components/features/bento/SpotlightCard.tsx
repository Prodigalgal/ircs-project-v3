"use client";

import Link from "next/link";
import { Play, Star, Calendar, Tag, Plus, Check, Loader2 } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { GlowButton } from "@/components/ui/GlowButton";
import React from "react";
import { cn } from "@/lib/utils";
import { useMediaInteraction } from "@/hooks/useMediaInteraction";

interface SpotlightCardProps {
    movie: MovieCard;
    index: number;
    total: number;
    duration?: number;
}

export function SpotlightCard({ movie, duration = 8000 }: SpotlightCardProps) {
    const { isFavorite, isLoading: isLoadingFav, toggleFavorite } = useMediaInteraction(movie.id);

    return (
        <Link href={`/movie/${movie.id}`} prefetch={false} className="block w-full h-full">
            <div
                className="relative w-full h-full overflow-hidden bg-background group"
            >
                <div
                    className="absolute inset-0 -z-30 pointer-events-none scale-[1.05]"
                >
                    <QuantumImage
                        src={movie.posterUrl}
                        alt=""
                        fill
                        className="object-cover blur-[60px] brightness-[0.4] dark:brightness-[0.3] saturate-[1.2]"
                    />
                </div>

                <div className="absolute inset-0 bg-gradient-to-t from-background via-background/80 to-transparent z-0" />
                <div className="absolute inset-0 bg-gradient-to-r from-background via-background/90 to-transparent z-0" />

                <div className="absolute inset-0 z-10 grid grid-cols-1 md:grid-cols-12 gap-6 p-6 md:p-10 lg:p-12 items-end">

                    <div className="md:col-span-7 lg:col-span-7 flex flex-col justify-end h-full pb-4 order-2 md:order-1 relative z-20 pointer-events-none">
                        <div className="animate-in fade-in slide-in-from-bottom-3 duration-500">
                            <div className="flex items-center gap-4 mb-4">
                                <div className="flex items-center gap-2 bg-secondary/20 backdrop-blur-md border border-border-subtle/10 px-3 py-1 rounded-full shadow-sm">
                                    <span className="relative flex h-2 w-2">
                                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                                      <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                                    </span>
                                    <span className="text-[10px] font-bold text-foreground uppercase tracking-widest">Featured</span>
                                </div>
                            </div>

                            <div className="h-[88px] md:h-[128px] lg:h-[140px] flex flex-col justify-end mb-4">
                                <h2
                                    className="text-4xl md:text-6xl lg:text-7xl font-black text-foreground leading-[1.1] tracking-tighter drop-shadow-lg line-clamp-2"
                                >
                                    {movie.title}
                                </h2>
                                {movie.subtitle && (
                                    <p
                                        className="text-lg md:text-2xl text-foreground/80 font-bold tracking-wide mt-2 line-clamp-1"
                                    >
                                        {movie.subtitle}
                                    </p>
                                )}
                            </div>

                            <div className="flex flex-wrap items-center gap-3 mb-6">
                                {movie.rating > 0 && (
                                    <Badge icon={<Star className="w-3 h-3 fill-amber-400 text-amber-400" />} text={String(movie.rating)} className="bg-amber-500/10 border-amber-500/20 text-amber-500 dark:text-amber-200" />
                                )}
                                {movie.season && movie.season > 0 && (
                                    <Badge icon={<span className="text-[10px] font-black">S{movie.season}</span>} text={`第${movie.season}季`} className="bg-purple-500/10 border-purple-500/20 text-purple-500" />
                                )}
                                <Badge icon={<Calendar className="w-3 h-3" />} text={String(movie.releaseYear)} />
                                <Badge icon={<Tag className="w-3 h-3" />} text={movie.categoryName} />
                                {movie.genres?.slice(0, 2).map(g => (
                                    <Badge key={g} text={g} className="text-muted-foreground bg-secondary/50" />
                                ))}
                            </div>

                            <div className="h-[64px] md:h-[80px] mb-8 overflow-hidden hidden md:block">
                                <p
                                    className="text-sm md:text-lg text-muted-foreground leading-relaxed font-normal line-clamp-2 md:line-clamp-3 text-ellipsis drop-shadow-sm max-w-3xl"
                                >
                                    {movie.description}
                                </p>
                            </div>

                            <div className="flex items-center gap-4 pointer-events-auto">
                                <GlowButton variant="primary" icon={<Play className="w-5 h-5 fill-current" />} className="px-8 py-4 text-base font-bold rounded-full hover:scale-105 shadow-xl">
                                    立即观看
                                </GlowButton>

                                <button
                                    onClick={toggleFavorite}
                                    disabled={isLoadingFav}
                                    className={cn(
                                        "p-4 rounded-full border transition-all backdrop-blur-md group/btn relative overflow-hidden",
                                        isFavorite
                                            ? "bg-green-500 text-white border-green-500 hover:bg-green-600"
                                            : "bg-secondary/50 text-foreground border-border-subtle/20 hover:bg-foreground hover:text-background"
                                    )}
                                    title={isFavorite ? "取消收藏" : "加入待看清单"}
                                >
                                    {isLoadingFav ? (
                                        <Loader2 className="w-5 h-5 animate-spin" />
                                    ) : isFavorite ? (
                                        <Check className="w-5 h-5" />
                                    ) : (
                                        <Plus className="w-5 h-5 group-hover/btn:rotate-90 transition-transform" />
                                    )}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="md:col-span-5 lg:col-span-5 h-full flex items-center justify-center md:justify-center order-1 md:order-2 perspective-1000">
                        <div
                            className="relative w-[200px] md:w-[260px] lg:w-[320px] aspect-[2/3] transform-gpu transition-transform duration-500 ease-out group-hover:scale-[1.015]"
                        >
                            <div className="absolute inset-0 bg-primary/20 blur-[40px] rounded-full translate-y-10 -z-10" />

                            <div className="w-full h-full rounded-xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/10 relative bg-neutral-900">
                                <QuantumImage
                                    src={movie.posterUrl}
                                    alt={movie.title}
                                    fill
                                    className="object-cover"
                                />
                                <div className="absolute inset-0 bg-gradient-to-tr from-black/20 to-transparent z-10" />
                            </div>
                        </div>
                    </div>
                </div>

                <div className="absolute bottom-0 left-0 h-1 bg-border/10 w-full z-20">
                    <div
                        key={movie.id}
                        style={{ animation: `spotlight-progress ${duration}ms linear forwards` }}
                        className="h-full bg-foreground/40 origin-left"
                    />
                </div>
            </div>
        </Link>
    );
}

function Badge({ icon, text, className }: { icon?: React.ReactNode, text: string, className?: string }) {
    return (
        <div className={cn(
            "flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold backdrop-blur-sm transition-colors",
            className || "bg-secondary/30 border-border-subtle/10 text-foreground"
        )}>
            {icon}
            <span>{text}</span>
        </div>
    );
}
