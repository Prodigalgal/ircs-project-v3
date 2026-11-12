"use client";

import { useMemo, useState } from "react";
import { ChevronRight, ChevronLeft, Layers, PlayCircle, Server } from "lucide-react";
import { Episode, MovieDetail } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";
import { VideoDomain } from "@/lib/domain/video";

interface WatchSidebarProps {
    movie: MovieDetail;
    currentSourceId: string;
    currentEpisodeId: string;
}

const EpisodeButton = ({
                           ep,
                           isNative,
                           isPlaying,
                           onClick
                       }: {
    ep: Episode,
    isNative: boolean,
    isPlaying: boolean,
    onClick: () => void
}) => {
    return (
        <button
            key={ep.id}
            onClick={onClick}
            className={cn(
                "relative aspect-square rounded-lg flex flex-col items-center justify-center p-2 border transition-all group overflow-hidden",
                isPlaying
                    ? "bg-primary/20 border-primary/50 text-primary"
                    : "bg-secondary/30 border-border-subtle/10 text-muted-foreground hover:bg-secondary hover:border-border-subtle/30 hover:text-foreground"
            )}
        >
            <span className="relative z-10 text-xs font-medium truncate w-full text-center">
                {ep.title.replace(/第|集/g, '')}
            </span>
            {isPlaying && (
                <div className="absolute inset-0 flex items-center justify-center opacity-20">
                    <PlayCircle className="w-8 h-8" />
                </div>
            )}
            {isPlaying && (
                <div className="absolute bottom-1 w-1 h-1 bg-primary rounded-full animate-pulse" />
            )}
            {isNative && !isPlaying && (
                <div className="absolute top-1 right-1 w-1 h-1 bg-primary/50 rounded-full" />
            )}
        </button>
    );
};

// --- 主组件 ---

export function WatchSidebar({ movie, currentSourceId, currentEpisodeId }: WatchSidebarProps) {
    const router = useRouter();
    const [isOpen, setIsOpen] = useState(true);
    const [activeSourceId, setActiveSourceId] = useState(currentSourceId);

    const activeSource = movie.sources.find(s => s.id === activeSourceId) || movie.sources[0];

    // Use Domain Logic
    const episodeGroups = useMemo(() => {
        if (!activeSource) return { native: [], external: [] };
        return VideoDomain.groupEpisodes(activeSource.episodes);
    }, [activeSource]);

    const handleEpisodeClick = (epId: string) => {
        router.push(`/watch/${movie.id}?source=${activeSourceId}&episode=${epId}`);
    };

    return (
        <>
            <button
                onClick={() => setIsOpen(true)}
                aria-hidden={isOpen}
                tabIndex={isOpen ? -1 : 0}
                className={cn(
                    "fixed right-0 top-1/2 -translate-y-1/2 z-40 bg-card/80 backdrop-blur-md border-l border-y border-border-subtle/10 p-3 rounded-l-xl text-foreground hover:bg-primary hover:border-primary hover:text-white transition-all shadow-lg",
                    isOpen ? "opacity-0 translate-x-5 pointer-events-none" : "opacity-100 translate-x-0"
                )}
            >
                <ChevronLeft className="w-5 h-5" />
            </button>

            <div
                className={cn(
                    "fixed top-20 bottom-4 right-4 z-40 w-80 flex flex-col pointer-events-none transition-transform duration-300 ease-out",
                    isOpen ? "translate-x-0" : "translate-x-[calc(100%+1rem)]"
                )}
            >
                {/* 背景色使用 bg-card/95，确保在亮色下也是实体背景 */}
                <div className="flex-1 flex flex-col bg-card/95 backdrop-blur-xl border border-border-subtle/10 rounded-2xl shadow-2xl overflow-hidden pointer-events-auto">

                    <div className="flex items-center justify-between p-4 border-b border-border-subtle/10 bg-secondary/5">
                        <div className="flex items-center gap-2 text-sm font-bold text-foreground">
                            <Layers className="w-4 h-4 text-primary" />
                            选集列表
                        </div>
                        <button
                            onClick={() => setIsOpen(false)}
                            className="p-1 text-muted-foreground hover:text-foreground transition-colors"
                        >
                            <ChevronRight className="w-5 h-5" />
                        </button>
                    </div>

                    <div className="p-3 bg-secondary/10">
                        <div className="flex items-center gap-2 mb-2 text-[10px] font-bold text-muted-foreground uppercase tracking-widest px-1">
                            <Server className="w-3 h-3" /> 播放源
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {movie.sources.map((source) => (
                                <button
                                    key={source.id}
                                    onClick={() => setActiveSourceId(source.id)}
                                    className={cn(
                                        "px-3 py-1.5 rounded-lg text-xs font-medium transition-all border",
                                        activeSourceId === source.id
                                            ? "bg-primary border-primary text-white shadow-lg shadow-primary/30"
                                            : "bg-secondary/30 border-border-subtle/10 text-muted-foreground hover:bg-secondary hover:text-foreground"
                                    )}
                                >
                                    {source.name}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-6">
                        {/* 直连分组 */}
                        {episodeGroups.native.length > 0 && (
                            <div>
                                <div className="flex items-center gap-1.5 mb-2 px-1">
                                    <PlayCircle className="w-3 h-3 text-primary" />
                                    <span className="text-xs font-bold text-muted-foreground">极速播放</span>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                    {episodeGroups.native.map(ep => (
                                        <EpisodeButton
                                            key={ep.id}
                                            ep={ep}
                                            isNative={true}
                                            isPlaying={activeSourceId === currentSourceId && ep.id === currentEpisodeId}
                                            onClick={() => handleEpisodeClick(ep.id)}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* 分割线（如果两个都有） */}
                        {episodeGroups.native.length > 0 && episodeGroups.external.length > 0 && (
                            <div className="h-px bg-border-subtle/10 mx-2" />
                        )}

                        {/* 外链分组 */}
                        {episodeGroups.external.length > 0 && (
                            <div>
                                <div className="flex items-center gap-1.5 mb-2 px-1">
                                    <Layers className="w-3 h-3 text-muted-foreground" />
                                    <span className="text-xs font-bold text-muted-foreground">网页播放</span>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                    {episodeGroups.external.map(ep => (
                                        <EpisodeButton
                                            key={ep.id}
                                            ep={ep}
                                            isNative={false}
                                            isPlaying={activeSourceId === currentSourceId && ep.id === currentEpisodeId}
                                            onClick={() => handleEpisodeClick(ep.id)}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="p-3 border-t border-border-subtle/10 bg-secondary/5 text-[10px] text-muted-foreground text-center">
                        {movie.title} • {activeSource?.name}
                    </div>
                </div>
            </div>
        </>
    );
}
