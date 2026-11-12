import React from "react";
import {AlignLeft, Calendar, Clock, Layers, ListVideo, MapPin, Mic2, Monitor, Sparkles, Star, Tag, Trophy} from "lucide-react";
import {MovieDetail} from "@/lib/types";
import {QuantumImage} from "@/components/ui/QuantumImage";
import {MovieActions} from "@/components/features/MovieActions";
import {Badge, ExternalLinkBadge, StatCard} from "./MovieInfoComponents";

interface MovieHeroProps {
    movie: MovieDetail;
}

export function MovieHero({movie}: MovieHeroProps) {
    return (
        <>
            <div className="absolute inset-0 w-full h-[120vh] pointer-events-none z-0">
                <QuantumImage
                    src={movie.posterUrl}
                    alt="ambient"
                    fill
                    className="object-cover opacity-20 blur-[100px] scale-125 saturate-100 dark:opacity-20 opacity-10" // 亮色模式下降低不透明度
                />
                <div className="absolute inset-0 bg-gradient-to-b from-background/80 via-background/95 to-background"/>
                <div className="absolute inset-0 bg-gradient-to-r from-background via-transparent to-background"/>
            </div>

            <div className="relative z-10 pt-32 md:pt-40 px-6 md:px-12 max-w-7xl mx-auto">
                <div className="flex flex-col lg:flex-row gap-12 lg:gap-16 items-start justify-center">

                    {/* 海报区域 (LCP 关键元素) */}
                    <div className="w-full max-w-sm lg:w-[400px] flex-shrink-0 mx-auto lg:mx-0 perspective-1000">
                        <div
                            className="relative aspect-[2/3] rounded-3xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/10 group transform transition-transform duration-500 hover:scale-[1.02] hover:-rotate-1 bg-card">
                            <QuantumImage
                                src={movie.posterUrl}
                                alt={movie.title}
                                fill
                                className="object-cover"
                            />
                            {/* 状态角标 */}
                            {movie.remarks && (
                                <div className="absolute top-4 right-4 z-20">
                                    <span
                                        className="px-3 py-1 rounded-lg bg-black/60 backdrop-blur-md border border-white/10 text-xs font-bold text-white shadow-lg">
                                        {movie.remarks}
                                    </span>
                                </div>
                            )}
                            <div
                                className="absolute inset-0 bg-gradient-to-tr from-white/0 via-white/0 to-white/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"/>
                        </div>
                        <div
                            className="hidden lg:block absolute top-full left-0 right-0 h-24 bg-gradient-to-b from-foreground/10 to-transparent blur-2xl opacity-30 transform scale-y-[-1]"
                            style={{
                                maskImage: 'linear-gradient(to bottom, black, transparent)',
                                WebkitMaskImage: 'linear-gradient(to bottom, black, transparent)'
                            }}
                        />
                    </div>

                    <div className="flex-1 w-full text-center lg:text-left pt-2 space-y-10">
                        {/* 标题与元数据 */}
                        <div className="space-y-6">
                            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3">
                                {movie.releaseYear === new Date().getFullYear().toString() && (
                                    <Badge
                                        className="bg-green-500/10 text-green-500 border-green-500/20 shadow-[0_0_15px_rgba(74,222,128,0.2)]">
                                        <Sparkles className="w-3.5 h-3.5 mr-1.5"/> NEW RELEASE
                                    </Badge>
                                )}
                                {movie.rating >= 8.0 && (
                                    <Badge
                                        className="bg-primary/10 text-primary border-primary/20 shadow-[0_0_15px_rgba(99,102,241,0.2)]">
                                        <Trophy className="w-3.5 h-3.5 mr-1.5"/> TOP RATED
                                    </Badge>
                                )}
                                {movie.categoryName && (
                                    <Badge className="bg-secondary text-muted-foreground border-border-subtle/10">
                                        {movie.categoryName}
                                    </Badge>
                                )}
                                {movie.season && movie.season > 0 && (
                                    <Badge className="bg-purple-500/10 text-purple-500 border-purple-500/20 shadow-[0_0_15px_rgba(168,85,247,0.2)]">
                                        <Layers className="w-3.5 h-3.5 mr-1.5"/> 第 {movie.season} 季
                                    </Badge>
                                )}
                            </div>

                            <div className="space-y-2">
                                <h1 className="text-4xl md:text-6xl lg:text-7xl font-black text-foreground tracking-tighter leading-[1.1] drop-shadow-2xl">
                                    {movie.title}
                                </h1>
                                {movie.subtitle && (
                                    <p className="text-xl md:text-2xl text-foreground/90 font-bold tracking-wide mt-2">
                                        {movie.subtitle}
                                    </p>
                                )}
                                {movie.aliasTitle && (
                                    <p className="text-lg md:text-xl text-muted-foreground font-medium tracking-wide">
                                        {movie.aliasTitle}
                                    </p>
                                )}
                                {movie.tagline && (
                                    <p className="text-lg text-muted-foreground/80 font-serif italic tracking-wide pt-2">
                                        &ldquo;{movie.tagline}&rdquo;
                                    </p>
                                )}
                            </div>

                            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3">
                                {movie.doubanId && (
                                    <ExternalLinkBadge href={`https://movie.douban.com/subject/${movie.doubanId}/`}
                                                       label="豆瓣"
                                                       color="bg-[#007722]/10 text-[#00c93c] border-[#007722]/20"/>
                                )}
                                {movie.tmdbId && (
                                    <ExternalLinkBadge
                                        href={`https://www.themoviedb.org/${movie.categoryName === '电视剧' ? 'tv' : 'movie'}/${movie.tmdbId}`}
                                        label="TMDB" color="bg-[#01b4e4]/10 text-[#00d3ea] border-[#01b4e4]/20"/>
                                )}
                                {movie.imdbId && (
                                    <ExternalLinkBadge href={`https://www.imdb.com/title/${movie.imdbId}/`} label="IMDb"
                                                       color="bg-[#f5c518]/10 text-[#f5c518] border-[#f5c518]/20"/>
                                )}
                            </div>
                        </div>

                        {/* 数据卡片 */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <StatCard
                                label="用户评分"
                                value={movie.rating?.toString() || "N/A"}
                                icon={<Star className="w-5 h-5 fill-amber-400 text-amber-400"/>}
                                subValue="综合评分"
                                highlight
                            />
                            <StatCard
                                label="上映年份"
                                value={movie.releaseYear}
                                icon={<Calendar className="w-5 h-5 text-primary"/>}
                                subValue="首映"
                            />
                            <StatCard
                                label="片长/集数"
                                value={movie.duration || movie.totalEpisodes || "未知"}
                                icon={movie.totalEpisodes ? <ListVideo className="w-5 h-5 text-emerald-400"/> :
                                    <Clock className="w-5 h-5 text-emerald-400"/>}
                                subValue={movie.totalEpisodes ? "连载状态" : "完整版"}
                            />
                            <StatCard
                                label="画质标准"
                                value="4K HDR"
                                icon={<Monitor className="w-5 h-5 text-pink-400"/>}
                                subValue="超清"
                            />
                        </div>

                        {/* 标签云 */}
                        <div
                            className="flex flex-wrap items-center justify-center lg:justify-start gap-x-6 gap-y-3 text-sm text-muted-foreground">
                            {movie.area && (
                                <div className="flex items-center gap-2">
                                    <MapPin className="w-4 h-4 text-muted-foreground/70"/>
                                    <span>{movie.area}</span>
                                </div>
                            )}
                            {movie.language && (
                                <div className="flex items-center gap-2">
                                    <Mic2 className="w-4 h-4 text-muted-foreground/70"/>
                                    <span>{movie.language}</span>
                                </div>
                            )}
                            <div className="flex items-center gap-2">
                                <Tag className="w-4 h-4 text-muted-foreground/70"/>
                                <div className="flex gap-2">
                                    {movie.genres.map(g => (
                                        <span key={g}
                                              className="hover:text-foreground transition-colors cursor-default">{g}</span>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* 操作栏 */}
                        <div className="flex justify-center lg:justify-start pt-2">
                            <MovieActions/>
                        </div>

                        {/* 简介 */}
                        <div
                            className="bg-card/50 border border-border-subtle/10 rounded-2xl p-6 md:p-8 relative overflow-hidden group hover:bg-card/80 transition-colors">
                            <div
                                className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
                                <AlignLeft className="w-24 h-24 text-foreground"/>
                            </div>
                            <h3 className="text-sm font-bold text-muted-foreground uppercase tracking-widest flex items-center gap-2 mb-4 relative z-10">
                                <span className="w-1 h-4 bg-primary rounded-full"/>
                                剧情简介
                            </h3>
                            <p className="text-base md:text-lg text-foreground/90 leading-relaxed font-normal tracking-wide relative z-10">
                                {movie.description}
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
