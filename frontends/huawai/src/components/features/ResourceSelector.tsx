"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Play, Download, Cloud } from "lucide-react";
import { MovieDetail } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";
import { useAuth } from "@/lib/context/AuthContext";

import { OnlinePanel } from "./resource/OnlinePanel";
import { DownloadPanel } from "./resource/DownloadPanel";
import { CloudPanel } from "./resource/CloudPanel";

interface ResourceSelectorProps {
    movie: MovieDetail;
}

type TabType = "online" | "download" | "cloud";

export function ResourceSelector({ movie }: ResourceSelectorProps) {
    const router = useRouter();
    const { addToast } = useToast();
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState<TabType>("online");

    const handlePlay = (episodeId: string, sourceId: string) => {
        if (!user) {
            addToast("请先登录后观看", "error");
            router.push("/login");
            return;
        }
        if (user.status === "PENDING_VERIFICATION") {
            addToast("请先激活账号", "error");
            router.push("/activate");
            return;
        }
        router.push(`/watch/${movie.id}?source=${sourceId}&episode=${episodeId}`);
    };

    return (
        <div id="resources" className="w-full bg-card border border-border-subtle/10 rounded-3xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/5">
            <div className="flex items-center border-b border-border-subtle/10 bg-secondary/5 backdrop-blur-md sticky top-0 z-20">
                {[
                    { id: "online", label: "在线播放", icon: <Play className="w-4 h-4" /> },
                    { id: "download", label: "磁力下载", icon: <Download className="w-4 h-4" /> },
                    { id: "cloud", label: "网盘资源", icon: <Cloud className="w-4 h-4" /> },
                ].map((tab) => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id as TabType)}
                        className={cn(
                            "flex-1 flex items-center justify-center gap-2 py-6 text-sm font-bold tracking-wide transition-all relative outline-none group select-none",
                            activeTab === tab.id
                                ? "text-foreground bg-card"
                                : "text-muted-foreground hover:text-foreground hover:bg-secondary/10"
                        )}
                    >
                        <span className={cn("transition-colors duration-300", activeTab === tab.id ? "text-primary" : "group-hover:text-foreground")}>
                            {tab.icon}
                        </span>
                        {tab.label}
                        <div
                            className={cn(
                                "absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-primary via-purple-500 to-primary shadow-[0_0_15px_rgba(99,102,241,0.6)] transition-opacity duration-200",
                                activeTab === tab.id ? "opacity-100" : "opacity-0"
                            )}
                        />
                    </button>
                ))}
            </div>

            <div className="min-h-[400px] relative">
                {activeTab === "online" && (
                    <OnlinePanel key="online" movie={movie} onPlay={handlePlay} />
                )}
                {activeTab === "download" && (
                    <DownloadPanel key="download" movie={movie} />
                )}
                {activeTab === "cloud" && (
                    <CloudPanel key="cloud" movie={movie} />
                )}
            </div>
        </div>
    );
}
