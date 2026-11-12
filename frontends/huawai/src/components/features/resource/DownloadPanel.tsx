"use client";

import { useState } from "react";
import { Magnet, Copy, Check, HardDrive } from "lucide-react";
import { MovieDetail, MagnetLink } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";

export function DownloadPanel({ movie }: { movie: MovieDetail }) {
    const { addToast } = useToast();
    const [copiedId, setCopiedId] = useState<string | null>(null);

    const handleCopy = async (text: string, id: string) => {
        try {
            await navigator.clipboard.writeText(text);
            setCopiedId(id);
            addToast("磁力链已复制", "success");
            setTimeout(() => setCopiedId(null), 2000);
        } catch {
            addToast("复制失败", "error");
        }
    };

    if (!movie.magnets || movie.magnets.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <HardDrive className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无下载资源</p>
            </div>
        );
    }

    return (
        <div className="p-6 md:p-8 space-y-3 animate-in fade-in duration-300">
            {movie.magnets.map((magnet: MagnetLink, idx) => {
                const uniqueId = magnet.id || `magnet-idx-${idx}`;
                return (
                    <div
                        key={uniqueId}
                        style={{ animationDelay: `${idx * 50}ms` }}
                        className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 bg-secondary/30 border border-border-subtle/10 rounded-xl hover:border-border-subtle/30 hover:bg-secondary/50 transition-all group animate-in fade-in slide-in-from-bottom-1 duration-200"
                    >
                        <div className="flex-1 min-w-0">
                            <div className="flex items-start gap-3 mb-2">
                            <span className="mt-0.5 p-1 rounded bg-green-500/10 text-green-500 border border-green-500/20">
                                <Magnet className="w-3.5 h-3.5" />
                            </span>
                                <h4 className="text-foreground/90 font-medium text-sm leading-relaxed break-all font-mono selection:bg-green-500/30">
                                    {magnet.title}
                                </h4>
                            </div>
                            <div className="flex items-center gap-3 text-xs text-muted-foreground ml-8">
                                <span className="text-muted-foreground/80 font-mono font-bold">{magnet.size}</span>
                                <span className="w-1 h-1 rounded-full bg-muted-foreground" />
                                <span>{magnet.uploadDate || magnet.date}</span>
                                <div className="hidden md:flex gap-2 ml-auto md:ml-2">
                                    {magnet.tags?.map((tag: string) => (
                                        <span key={tag} className="px-1.5 py-0.5 rounded bg-background border border-border-subtle/10 text-[10px] text-muted-foreground">
                                        {tag}
                                    </span>
                                    ))}
                                </div>
                            </div>
                        </div>
                        <button
                            onClick={() => handleCopy(magnet.link, uniqueId)}
                            className={cn(
                                "flex-shrink-0 flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg text-xs font-bold transition-all border ml-8 md:ml-0",
                                copiedId === uniqueId
                                    ? "bg-green-500/10 text-green-500 border-green-500/20"
                                    : "bg-background text-muted-foreground border-border-subtle/10 hover:bg-secondary hover:text-foreground group-hover:border-border-subtle/20"
                            )}
                        >
                            {copiedId === uniqueId ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                            {copiedId === uniqueId ? "已复制" : "复制链接"}
                        </button>
                    </div>
                );
            })}
        </div>
    );
}
