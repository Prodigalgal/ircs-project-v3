"use client";

import { motion, useMotionValue, PanInfo } from "framer-motion";
import { Play, X, Film, Disc, Loader2, CheckCircle2, Clock, Trash2 } from "lucide-react";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { cn, formatDuration } from "@/lib/utils";
import { InteractionRecord } from "@/lib/types";
import { useState } from "react";

interface HistoryItemProps {
    rec: InteractionRecord;
    isPlaying: boolean;
    onPlay: (rec: InteractionRecord) => void;
    onDelete: (id: string) => void;
    index: number;
}

export function HistoryItem({ rec, isPlaying, onPlay, onDelete, index }: HistoryItemProps) {
    const x = useMotionValue(0);
    const [isHovered, setIsHovered] = useState(false);
    const [isDragging, setIsDragging] = useState(false);

    const progressPercent = rec.durationSeconds > 0
        ? Math.min(100, Math.round((rec.progressSeconds / rec.durationSeconds) * 100))
        : 0;

    const remaining = rec.durationSeconds - rec.progressSeconds;
    const isFinished = progressPercent > 90;

    const handleDragEnd = (_: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
        if (info.offset.x < -100) {
            onDelete(rec.id);
        }
        setTimeout(() => setIsDragging(false), 150);
    };

    const handleClick = (e: React.MouseEvent) => {
        if (isDragging) {
            e.preventDefault();
            e.stopPropagation();
            return;
        }
        onPlay(rec);
    };

    return (
        <motion.div
            layout
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -50, height: 0, marginBottom: 0 }}
            transition={{ duration: 0.3, delay: index * 0.05 }}
            className="relative pl-8 group"
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            {/* 时间轴竖线：使用语义化 border-subtle/10 */}
            <div className="absolute left-0 top-0 bottom-0 w-px bg-gradient-to-b from-border-subtle/20 via-border-subtle/10 to-transparent group-last:bottom-auto group-last:h-1/2" />

            {/* Timeline Dot */}
            <div className={cn(
                "absolute left-[-4px] top-8 w-[9px] h-[9px] rounded-full border-2 transition-all duration-300 z-10",
                isFinished
                    ? "bg-background border-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]"
                    : isHovered
                        ? "bg-primary border-primary shadow-[0_0_10px_rgba(99,102,241,0.8)] scale-125"
                        : "bg-background border-border-subtle/30"
            )} />

            <motion.div
                style={{ x }}
                drag="x"
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={{ left: 0.5, right: 0 }}
                onDragStart={() => setIsDragging(true)}
                onDragEnd={handleDragEnd}
                onClick={handleClick}
                className="relative overflow-hidden rounded-2xl bg-card border border-border-subtle/10 hover:border-border-subtle/30 transition-all cursor-pointer group/card active:cursor-grabbing shadow-sm hover:shadow-md"
            >
                <div className={cn(
                    "absolute inset-0 opacity-0 transition-opacity duration-500 pointer-events-none",
                    isPlaying ? "opacity-100 bg-primary/5" : "group-hover/card:opacity-100 bg-secondary/30"
                )} />

                <div className="flex gap-4 p-4 relative z-10 pointer-events-none">
                    {/* Thumbnail */}
                    <div className="relative w-32 md:w-40 aspect-video rounded-lg overflow-hidden flex-shrink-0 bg-neutral-900 shadow-lg ring-1 ring-border-subtle/10 group-hover/card:ring-border-subtle/20 transition-all">
                        <QuantumImage src={rec.coverImageUrl} alt={rec.title} fill className="object-cover transition-transform duration-700 group-hover/card:scale-110" />

                        <div className={cn(
                            "absolute inset-0 flex items-center justify-center transition-all duration-300 bg-black/40 backdrop-blur-[2px]",
                            isPlaying || isHovered ? "opacity-100" : "opacity-0"
                        )}>
                            <div className={cn(
                                "w-10 h-10 rounded-full flex items-center justify-center transition-all duration-300 shadow-2xl border border-white/10",
                                isPlaying
                                    ? "bg-primary text-white scale-110 shadow-[0_0_20px_rgba(99,102,241,0.5)]"
                                    : "bg-white/20 text-white group-hover/card:bg-white group-hover/card:text-black group-hover/card:scale-110"
                            )}>
                                {isPlaying ? <Loader2 className="w-5 h-5 animate-spin" /> : <Play className="w-5 h-5 fill-current" />}
                            </div>
                        </div>

                        <div className="absolute bottom-0 left-0 right-0 h-1 bg-black/50">
                            <motion.div
                                initial={{ width: 0 }}
                                animate={{ width: `${progressPercent}%` }}
                                transition={{ duration: 1, ease: "circOut" }}
                                className={cn(
                                    "h-full shadow-[0_0_10px_currentColor]",
                                    isFinished ? "bg-green-500 text-green-500" : "bg-primary text-primary"
                                )}
                            />
                        </div>
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0 flex flex-col justify-center py-1">
                        <div className="flex justify-between items-start mb-1">
                            <h3 className={cn(
                                "text-base md:text-lg font-bold truncate pr-8 transition-colors",
                                isPlaying ? "text-primary" : "text-foreground group-hover/card:text-primary/80"
                            )}>
                                {rec.title}
                            </h3>

                            <button
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    onDelete(rec.id);
                                }}
                                className="hidden md:flex p-2 -mt-2 -mr-2 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-full transition-all opacity-0 group-hover/card:opacity-100 pointer-events-auto"
                                title="删除记录"
                                onPointerDown={(e) => e.stopPropagation()}
                            >
                                <X className="w-4 h-4" />
                            </button>
                        </div>

                        <div className="flex items-center gap-2 mb-3">
                            {rec.episodeName ? (
                                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[10px] font-medium bg-secondary border border-border-subtle/10 text-muted-foreground">
                                    <Film className="w-3 h-3" />
                                    {rec.episodeName}
                                </span>
                            ) : (
                                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[10px] font-medium bg-secondary border border-border-subtle/10 text-muted-foreground">
                                    <Disc className="w-3 h-3" />
                                    正片
                                </span>
                            )}
                            <span className="text-[10px] text-muted-foreground/70 font-mono">
                                {rec.lastWatchedAt.split('T')[0].replace(/-/g, '.')}
                            </span>
                        </div>

                        <div className="mt-auto flex items-center gap-3">
                            {isFinished ? (
                                <span className="flex items-center gap-1.5 text-xs text-green-500 font-bold bg-green-500/10 px-2 py-0.5 rounded border border-green-500/20">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> 已看完
                                </span>
                            ) : (
                                <div className="flex items-center gap-2 text-xs font-mono text-muted-foreground">
                                    <Clock className="w-3.5 h-3.5" />
                                    <span className="text-muted-foreground/80">剩余 {formatDuration(Math.floor(remaining / 60))}</span>
                                    <span className="w-1 h-1 rounded-full bg-border" />
                                    <span className="text-primary font-bold">{progressPercent}%</span>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                <div className="absolute inset-y-0 right-0 w-24 bg-destructive/10 flex items-center justify-center -z-10">
                    <Trash2 className="w-6 h-6 text-destructive" />
                </div>
            </motion.div>
        </motion.div>
    );
}