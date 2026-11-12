"use client";

import { useMemo, useState } from "react";
import { FileX, Zap, Layers } from "lucide-react";
import { MovieDetail } from "@/lib/types";
import { SourceRail } from "./SourceRail";
import { EpisodeGrid } from "./EpisodeGrid";
import { VideoDomain } from "@/lib/domain/video";

interface OnlinePanelProps {
    movie: MovieDetail;
    onPlay: (episodeId: string, sourceId: string) => void;
}

export function OnlinePanel({ movie, onPlay }: OnlinePanelProps) {
    const [selectedId, setSelectedId] = useState(movie.sources[0]?.id || "");

    const activeSourceId = useMemo(() => {
        const isValid = movie.sources.some(s => s.id === selectedId);
        return isValid ? selectedId : (movie.sources[0]?.id || "");
    }, [movie.sources, selectedId]);

    const activeSource = useMemo(() =>
            movie.sources.find(s => s.id === activeSourceId),
        [movie.sources, activeSourceId]);

    const episodeGroups = useMemo(() => {
        if (!activeSource) return { native: [], external: [] };
        return VideoDomain.groupEpisodes(activeSource.episodes);
    }, [activeSource]);

    if (!movie.sources || movie.sources.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <FileX className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无在线播放资源</p>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-full bg-card animate-in fade-in duration-300">
            <SourceRail
                sources={movie.sources}
                activeSourceId={activeSourceId} // Uses the safe, derived ID
                onSelect={setSelectedId}        // Updates the user preference
            />

            <div className="p-6 md:p-8 min-h-[300px]">
                {activeSource && activeSource.episodes.length > 0 ? (
                    <div className="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
                        {episodeGroups.native.length > 0 && (
                            <div className="space-y-4">
                                <div className="flex items-center gap-3 select-none">
                                    <div className="p-1.5 rounded-md bg-primary/10 text-primary">
                                        <Zap className="w-4 h-4 fill-current" />
                                    </div>
                                    <span className="text-sm font-bold text-foreground">极速直连</span>
                                    <span className="text-[10px] text-muted-foreground font-mono bg-secondary px-2 py-0.5 rounded border border-border-subtle/10">
                                        HLS / 4K
                                    </span>
                                </div>
                                <EpisodeGrid
                                    episodes={episodeGroups.native}
                                    isNative={true}
                                    onPlay={(epId) => onPlay(epId, activeSourceId)}
                                />
                            </div>
                        )}

                        {episodeGroups.native.length > 0 && episodeGroups.external.length > 0 && (
                            <div className="h-px bg-gradient-to-r from-transparent via-border-subtle/10 to-transparent" />
                        )}

                        {episodeGroups.external.length > 0 && (
                            <div className="space-y-4">
                                <div className="flex items-center gap-3 select-none">
                                    <div className="p-1.5 rounded-md bg-secondary text-muted-foreground">
                                        <Layers className="w-4 h-4" />
                                    </div>
                                    <span className="text-sm font-bold text-muted-foreground">网页解析</span>
                                    <span className="text-[10px] text-muted-foreground/80 font-mono">
                                        第三方播放器
                                    </span>
                                </div>
                                <EpisodeGrid
                                    episodes={episodeGroups.external}
                                    isNative={false}
                                    onPlay={(epId) => onPlay(epId, activeSourceId)}
                                />
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <FileX className="w-8 h-8 mb-3 opacity-30" />
                        <p className="text-sm">该线路暂无资源</p>
                    </div>
                )}
            </div>
        </div>
    );
}
