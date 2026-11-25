"use client";

import { useEffect, useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { CalendarClock, Trash2, Loader2, PlayCircle } from "lucide-react";
import { interactionApi } from "@/lib/api/interaction";
import { contentApi } from "@/lib/api/content";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { InteractionRecord, MovieDetail, VideoSource, Episode } from "@/lib/types";
import { HistoryItem } from "@/components/features/history/HistoryItem";
import { getErrorMessage } from "@/lib/utils";

export default function HistoryPage() {
    const { user } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();
    const [history, setHistory] = useState<InteractionRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [playingId, setPlayingId] = useState<string | null>(null);

    useEffect(() => {
        if (user) {
            interactionApi.getHistory()
                .then(res => setHistory(res.content))
                .catch(console.error)
                .finally(() => setIsLoading(false));
        } else {
            setIsLoading(false);
        }
    }, [user]);

    const groupedHistory = useMemo(() => {
        const groups: Record<string, InteractionRecord[]> = {};
        const now = new Date();
        const today = now.toISOString().split('T')[0];
        const yesterday = new Date(now.setDate(now.getDate() - 1)).toISOString().split('T')[0];

        history.forEach(item => {
            if (!item.lastWatchedAt) return;
            const date = item.lastWatchedAt.split('T')[0];
            let label = date;
            if (date === today) label = "今天";
            else if (date === yesterday) label = "昨天";
            else label = "更早之前";

            if (!groups[label]) groups[label] = [];
            groups[label].push(item);
        });
        return groups;
    }, [history]);

    const handleClearAll = async () => {
        if (window.confirm("确定清空所有历史记录？此操作不可恢复。")) {
            await interactionApi.clearHistory();
            setHistory([]);
            addToast("历史记录已清空", "success");
        }
    };

    const handleDeleteSingle = async (id: string) => {
        setHistory(prev => prev.filter(item => item.id !== id));
        try {
            await interactionApi.deleteRecord(id);
        } catch (e) {
            console.error("Failed to delete record", e);
        }
    };

    const handleSmartPlay = async (record: InteractionRecord) => {
        if (playingId) return;
        setPlayingId(record.id);

        try {
            const movie: MovieDetail | null = await contentApi.getMovieDetail(record.unifiedVideoId);

            if (!movie) throw new Error("影片信息未找到");
            if (!movie.sources || movie.sources.length === 0) {
                throw new Error("该影片暂无可用播放源");
            }

            let foundSource: VideoSource | undefined;
            let foundEpisode: Episode | undefined;
            let matchType = "";

            if (record.lastVideoId) {
                foundSource = movie.sources.find(s => s.id === record.lastVideoId);
                if (foundSource && record.lastEpisodeId) {
                    foundEpisode = foundSource.episodes.find(e => e.id === record.lastEpisodeId);
                    if (foundEpisode) matchType = "EXACT_ID";
                }
            }

            if (!foundEpisode && record.episodeName) {
                if (foundSource) {
                    foundEpisode = findEpisodeByName(foundSource, record.episodeName);
                    if (foundEpisode) matchType = "SAME_SOURCE_SEMANTIC";
                }

                if (!foundEpisode) {
                    for (const source of movie.sources) {
                        const ep = findEpisodeByName(source, record.episodeName);
                        if (ep) {
                            foundSource = source;
                            foundEpisode = ep;
                            matchType = "CROSS_SOURCE_SEMANTIC";
                            break;
                        }
                    }
                }
            }

            if (foundSource && foundEpisode) {
                let targetEpisodeId = foundEpisode.id;
                let startTime = record.progressSeconds;
                let autoNext = false;

                const progress = record.durationSeconds > 0
                    ? (record.progressSeconds / record.durationSeconds)
                    : 0;

                if (progress > 0.90) {
                    const currentIndex = foundSource.episodes.findIndex(e => e.id === foundEpisode!.id);
                    if (currentIndex !== -1 && currentIndex < foundSource.episodes.length - 1) {
                        const nextEp = foundSource.episodes[currentIndex + 1];
                        targetEpisodeId = nextEp.id;
                        startTime = 0;
                        autoNext = true;
                    }
                }

                if (autoNext) {
                    addToast("上集已看完，为您自动播放下一集", "success");
                } else if (matchType !== "EXACT_ID") {
                    addToast(matchType === "SAME_SOURCE_SEMANTIC"
                        ? "资源已更新，自动定位到对应集数"
                        : "原线路已失效，自动切换至备用线路", "info");
                }

                const timeParam = startTime > 0 ? `&t=${startTime}` : "";
                router.push(`/watch/${movie.id}?source=${foundSource.id}&episode=${targetEpisodeId}${timeParam}`);

            } else {
                addToast("无法自动定位历史进度，请手动选择集数", "info");
                router.push(`/movie/${movie.id}`);
            }

        } catch (e: unknown) {
            addToast(getErrorMessage(e) || "无法跳转播放", "error");
            router.push(`/movie/${record.unifiedVideoId}`);
        } finally {
            setTimeout(() => setPlayingId(null), 2000);
        }
    };

    const findEpisodeByName = (source: VideoSource, targetName: string): Episode | undefined => {
        let match = source.episodes.find(e => e.title === targetName);
        if (match) return match;

        const normalize = (s: string) => s.replace(/[第集\s\[\]【】()]/g, "").toLowerCase();
        const cleanTarget = normalize(targetName);

        match = source.episodes.find(e => normalize(e.title) === cleanTarget);
        if (match) return match;

        if (cleanTarget.length > 1) {
            match = source.episodes.find(e => normalize(e.title).includes(cleanTarget));
        }
        return match;
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background pt-32 flex justify-center items-center">
                <div className="flex flex-col items-center gap-4">
                    <Loader2 className="w-8 h-8 animate-spin text-primary" />
                    <p className="text-xs text-muted-foreground font-mono tracking-widest">LOADING HISTORY...</p>
                </div>
            </div>
        );
    }

    const totalRecords = history.length;

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-4xl mx-auto relative z-10">
                <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-6">
                    <div>
                        <motion.h1
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="text-4xl font-bold text-foreground mb-2 flex items-center gap-3"
                        >
                            <CalendarClock className="w-8 h-8 text-primary" />
                            观看历史
                        </motion.h1>
                        <motion.p
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            transition={{ delay: 0.1 }}
                            className="text-muted-foreground text-sm"
                        >
                            共记录 <span className="text-foreground font-bold">{totalRecords}</span> 条观看记录
                        </motion.p>
                    </div>

                    {totalRecords > 0 && (
                        <motion.button
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            onClick={handleClearAll}
                            className="flex items-center gap-2 px-4 py-2 rounded-full bg-secondary/50 hover:bg-destructive/10 text-muted-foreground hover:text-destructive border border-border-subtle/10 hover:border-destructive/20 transition-all text-xs font-bold"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                            清空历史
                        </motion.button>
                    )}
                </div>

                <div className="space-y-12">
                    <AnimatePresence mode="popLayout">
                        {Object.entries(groupedHistory).map(([dateLabel, items], groupIndex) => (
                            items.length > 0 && (
                                <motion.section
                                    key={dateLabel}
                                    layout
                                    initial={{ opacity: 0, y: 20 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0 }}
                                    transition={{ delay: groupIndex * 0.1 }}
                                    className="relative pl-4"
                                >
                                    <div className="sticky top-24 z-20 flex items-center mb-6 -ml-4">
                                        <div className="w-2 h-2 rounded-full bg-primary mr-4 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />
                                        {/* 时间标签：背景需适配双色 */}
                                        <h3 className="text-sm font-bold text-foreground uppercase tracking-widest bg-background/80 backdrop-blur-md px-3 py-1 rounded-full border border-border-subtle/10 shadow-sm">
                                            {dateLabel}
                                        </h3>
                                        <div className="h-px bg-border-subtle/10 flex-1 ml-4" />
                                    </div>

                                    <div className="space-y-6">
                                        {items.map((rec, idx) => (
                                            <HistoryItem
                                                key={rec.id}
                                                rec={rec}
                                                isPlaying={playingId === rec.id}
                                                onPlay={handleSmartPlay}
                                                onDelete={handleDeleteSingle}
                                                index={idx}
                                            />
                                        ))}
                                    </div>
                                </motion.section>
                            )
                        ))}
                    </AnimatePresence>

                    {totalRecords === 0 && (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.9 }}
                            animate={{ opacity: 1, scale: 1 }}
                            className="flex flex-col items-center justify-center py-24 border border-dashed border-border-subtle/10 rounded-3xl bg-secondary/5"
                        >
                            <div className="w-24 h-24 rounded-full bg-secondary border border-border-subtle/10 flex items-center justify-center mb-6 relative group cursor-default">
                                <div className="absolute inset-0 bg-primary/20 blur-[30px] rounded-full opacity-0 group-hover:opacity-100 transition-opacity" />
                                <PlayCircle className="w-10 h-10 text-muted-foreground group-hover:text-primary transition-colors" />
                            </div>
                            <h3 className="text-xl font-bold text-foreground mb-2">暂无观看记录</h3>
                            <p className="text-muted-foreground text-sm max-w-xs text-center mb-8">
                                还没有留下任何观影痕迹。开始探索，点亮您的第一段记忆。
                            </p>
                            <button
                                onClick={() => router.push("/explore")}
                                className="px-8 py-3 rounded-full bg-foreground text-background font-bold hover:bg-foreground/80 transition-colors shadow-lg active:scale-95"
                            >
                                前往探索
                            </button>
                        </motion.div>
                    )}
                </div>
            </div>
        </div>
    );
}