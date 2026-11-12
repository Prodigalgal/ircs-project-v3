"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { GlowButton } from "@/components/ui/GlowButton";
import { Search, AlertTriangle, ArrowRight } from "lucide-react";
import { contentApi } from "@/lib/api/content";
import { MovieCard as MovieCardType } from "@/lib/types";
import { MovieCard } from "@/components/features/MovieCard";

export default function NotFound() {
    const pathname = usePathname();
    const [recommendations, setRecommendations] = useState<MovieCardType[]>([]);

    const guessKeyword = useMemo(() => {
        if (!pathname) return "";
        const parts = pathname.split('/').filter(Boolean);
        const lastPart = parts[parts.length - 1];
        if (lastPart && lastPart.length > 1) {
            try {
                return decodeURIComponent(lastPart).replace(/[-_]/g, ' ');
            } catch {
                return "";
            }
        }
        return "";
    }, [pathname]);

    useEffect(() => {
        contentApi.explore({ sort: "trending", size: 6 })
            .then(res => setRecommendations(res.content || []))
            .catch(() => {});
    }, []);

    return (
        <div className="min-h-screen bg-background pt-20 pb-20 px-6 flex flex-col items-center relative overflow-hidden">
            <div className="absolute inset-0 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] opacity-10 pointer-events-none" />

            <div className="relative z-10 max-w-5xl w-full flex flex-col items-center text-center">
                <div className="w-24 h-24 bg-red-500/10 rounded-3xl flex items-center justify-center mb-8 border border-red-500/20 shadow-[0_0_50px_rgba(239,68,68,0.2)]">
                    <AlertTriangle className="w-10 h-10 text-red-500" />
                </div>

                <h1 className="text-5xl md:text-7xl font-black text-foreground tracking-tighter mb-6">
                    404 <span className="text-muted-foreground">Lost</span>
                </h1>

                <p className="text-lg text-muted-foreground max-w-lg mb-10">
                    您访问的坐标 <code className="bg-secondary px-2 py-0.5 rounded text-foreground font-mono text-sm border border-border-subtle/10">{pathname}</code> 似乎是一片虚空。
                    {guessKeyword && (
                        <span>
                            但我们猜测您可能在寻找 <span className="text-primary font-bold">“{guessKeyword}”</span>？
                        </span>
                    )}
                </p>

                {/* 强制引导搜索 */}
                <div className="w-full max-w-md mb-16">
                    <Link href={`/search?keyword=${guessKeyword}`}>
                        <GlowButton className="w-full py-4 text-lg" icon={<Search className="w-5 h-5"/>}>
                            {guessKeyword ? `搜索 "${guessKeyword}"` : "搜索全站资源"}
                        </GlowButton>
                    </Link>
                </div>

                {/* 智能推荐 (挽留流量的核心) */}
                {recommendations.length > 0 && (
                    <div className="w-full text-left">
                        <div className="flex items-center gap-3 mb-6 border-b border-border-subtle/10 pb-4">
                            <div className="w-1 h-5 bg-emerald-500 rounded-full" />
                            <h3 className="text-lg font-bold text-foreground">热门资源推荐</h3>
                            <span className="text-xs text-muted-foreground ml-auto">大家都在看</span>
                        </div>

                        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
                            {recommendations.map((movie, idx) => (
                                <MovieCard key={movie.id} movie={movie} index={idx} />
                            ))}
                        </div>
                    </div>
                )}

                <div className="mt-12">
                    <Link href="/" className="text-sm font-bold text-muted-foreground hover:text-foreground flex items-center gap-2 transition-colors">
                        返回首页 <ArrowRight className="w-4 h-4" />
                    </Link>
                </div>
            </div>
        </div>
    );
}
