"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { MovieCard } from "@/components/features/MovieCard";
import { Heart, LayoutGrid, List, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { interactionApi } from "@/lib/api/interaction";
import { useAuth } from "@/lib/context/AuthContext";
import { InteractionRecord, MovieCard as MovieCardType } from "@/lib/types";

export default function MyListPage() {
    const router = useRouter();
    const { user, isLoading: isAuthLoading } = useAuth();
    const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
    const [favorites, setFavorites] = useState<InteractionRecord[]>([]);
    const [loadedUserId, setLoadedUserId] = useState<string | null>(null);

    useEffect(() => {
        if (!user) return;

        let isCurrent = true;

        interactionApi.getFavorites()
            .then(res => {
                if (isCurrent) {
                    setFavorites(res.content);
                    setLoadedUserId(user.id);
                }
            })
            .catch((error) => {
                console.error(error);
                if (isCurrent) {
                    setFavorites([]);
                    setLoadedUserId(user.id);
                }
            });

        return () => {
            isCurrent = false;
        };
    }, [user]);

    const visibleFavorites = user ? favorites : [];
    const isLoading = isAuthLoading || Boolean(user && loadedUserId !== user.id);

    const adaptToMovieCard = (rec: InteractionRecord): MovieCardType => ({
        id: rec.unifiedVideoId,
        title: rec.title,
        posterUrl: rec.coverImageUrl,
        rating: rec.score,
        releaseYear: "",
        categoryName: "",
        area: "未知",
    });

    const handleRemove = async (unifiedVideoId: string) => {
        try {
            await interactionApi.toggleFavorite(unifiedVideoId);
            setFavorites(prev => prev.filter(f => f.unifiedVideoId !== unifiedVideoId));
        } catch (e) {
            console.error(e);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background pt-32 flex justify-center">
                <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6">
            <div className="max-w-7xl mx-auto">
                <div className="flex items-center justify-between mb-12">
                    <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-gradient-to-tr from-pink-600 to-rose-600 rounded-xl flex items-center justify-center shadow-lg shadow-pink-900/20">
                            <Heart className="w-6 h-6 text-white fill-current" />
                        </div>
                        <div>
                            <h1 className="text-3xl font-bold text-foreground">我的收藏</h1>
                            <p className="text-muted-foreground text-sm mt-1">
                                {visibleFavorites.length} 部已收藏影片
                            </p>
                        </div>
                    </div>

                    <div className="flex bg-secondary rounded-lg p-1 border border-border-subtle/10">
                        <button
                            onClick={() => setViewMode("grid")}
                            className={cn(
                                "p-2 rounded-md transition-all",
                                viewMode === "grid" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
                            )}
                        >
                            <LayoutGrid className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => setViewMode("list")}
                            className={cn(
                                "p-2 rounded-md transition-all",
                                viewMode === "list" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
                            )}
                        >
                            <List className="w-4 h-4" />
                        </button>
                    </div>
                </div>

                {visibleFavorites.length > 0 ? (
                    <div className={cn(
                        "grid gap-6",
                        viewMode === "grid"
                            ? "grid-cols-2 md:grid-cols-4 lg:grid-cols-5"
                            : "grid-cols-1"
                    )}>
                        {visibleFavorites.map((rec, idx) => {
                            const movie = adaptToMovieCard(rec);
                            return viewMode === "grid" ? (
                                <MovieCard key={rec.id} movie={movie} index={idx} />
                            ) : (
                                <div key={rec.id} className="flex items-center gap-6 p-4 bg-card border border-border-subtle/10 rounded-xl hover:bg-secondary/50 transition-colors group cursor-pointer shadow-sm"
                                     onClick={() => router.push(`/movie/${movie.id}`)}
                                >
                                    <div className="w-20 md:w-24 aspect-[2/3] relative rounded-lg overflow-hidden bg-neutral-900 border border-border-subtle/10 flex-shrink-0">
                                        <QuantumImage src={rec.coverImageUrl} fill className="object-cover" alt={rec.title}/>
                                    </div>
                                    <div className="flex-1">
                                        <h3 className="text-xl font-bold text-foreground mb-2">{rec.title}</h3>
                                        <div className="flex items-center gap-3 text-sm text-muted-foreground">
                                            <span className="text-amber-500 font-bold">{rec.score}</span>
                                        </div>
                                    </div>
                                    <div className="mr-4">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleRemove(rec.unifiedVideoId); }}
                                            className="px-4 py-2 text-sm font-medium text-muted-foreground border border-border rounded-full hover:border-destructive hover:text-destructive hover:bg-destructive/5 transition-all"
                                        >
                                            取消收藏
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-32 text-center border border-dashed border-border-subtle/10 rounded-3xl bg-secondary/5">
                        <div className="w-20 h-20 bg-secondary rounded-full flex items-center justify-center mb-6">
                            <Heart className="w-8 h-8 text-muted-foreground" />
                        </div>
                        <h3 className="text-xl font-bold text-foreground mb-2">收藏夹空空如也</h3>
                        <p className="text-muted-foreground max-w-sm mb-8">
                            当您看到感兴趣的内容时，点击“收藏”图标即可在这里找到它们。
                        </p>
                        <a href="/explore" className="px-6 py-3 bg-foreground text-background rounded-full font-bold hover:bg-foreground/80 transition-colors">
                            去探索内容
                        </a>
                    </div>
                )}
            </div>
        </div>
    );
}
