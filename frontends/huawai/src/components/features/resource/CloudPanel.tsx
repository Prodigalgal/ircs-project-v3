"use client";

import { useState } from "react";
import { Cloud, Copy, Check, ExternalLink } from "lucide-react";
import { MovieDetail, CloudLink } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";

export function CloudPanel({ movie }: { movie: MovieDetail }) {
    const { addToast } = useToast();
    const [copiedId, setCopiedId] = useState<string | null>(null);

    const handleCopy = async (text: string, id: string) => {
        try {
            await navigator.clipboard.writeText(text);
            setCopiedId(id);
            addToast("提取码已复制", "success");
            setTimeout(() => setCopiedId(null), 2000);
        } catch {
            addToast("复制失败", "error");
        }
    };

    if (!movie.cloudLinks || movie.cloudLinks.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <Cloud className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无网盘资源</p>
            </div>
        );
    }

    return (
        <div className="p-6 md:p-8 grid grid-cols-1 md:grid-cols-2 gap-4 animate-in fade-in duration-300">
            {movie.cloudLinks.map((link: CloudLink, idx) => {
                const uniqueId = link.id || `cloud-idx-${idx}`;
                return (
                    <div
                        key={uniqueId}
                        style={{ animationDelay: `${idx * 50}ms` }}
                        className="p-5 bg-secondary/30 border border-border-subtle/10 rounded-2xl hover:border-border-subtle/30 hover:bg-secondary/50 transition-all flex items-center justify-between group animate-in fade-in zoom-in-95 duration-200"
                    >
                        <div className="flex items-center gap-4 min-w-0">
                            <div className="w-12 h-12 rounded-xl bg-primary/10 border border-primary/10 flex items-center justify-center text-primary group-hover:scale-110 transition-transform">
                                <Cloud className="w-6 h-6" />
                            </div>
                            <div className="min-w-0">
                                <div className="text-foreground font-bold text-sm truncate max-w-[150px]" title={link.title}>
                                    {link.title}
                                </div>
                                <div className="text-xs text-muted-foreground mt-1 flex items-center gap-2">
                                    <span className="font-medium text-muted-foreground/80">{link.provider}</span>
                                    {link.code && (
                                        <span className="bg-background/50 px-1.5 py-0.5 rounded font-mono text-[10px] border border-border-subtle/10">
                                            码: <span className="text-foreground select-all">{link.code}</span>
                                        </span>
                                    )}
                                </div>
                            </div>
                        </div>

                        <div className="flex gap-2 pl-2">
                            {link.code && (
                                <button
                                    onClick={() => handleCopy(link.code!, `code-${uniqueId}`)}
                                    className={cn(
                                        "p-2.5 rounded-xl text-xs font-bold transition-all border",
                                        copiedId === `code-${uniqueId}`
                                            ? "bg-green-500/10 text-green-500 border-green-500/20"
                                            : "bg-background text-muted-foreground border-border-subtle/10 hover:bg-secondary hover:text-foreground"
                                    )}
                                    title="复制提取码"
                                >
                                    {copiedId === `code-${uniqueId}` ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </button>
                            )}
                            <a
                                href={link.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-foreground text-background hover:bg-foreground/80 text-xs font-bold transition-all shadow-md hover:shadow-lg"
                            >
                                打开
                                <ExternalLink className="w-3 h-3" />
                            </a>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
