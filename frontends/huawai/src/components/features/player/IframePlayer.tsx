"use client";

import { useState } from "react";
import { Loader2, ExternalLink } from "lucide-react";
import { useVideoHeartbeat } from "@/hooks/useVideoHeartbeat";

interface IframePlayerProps {
    src: string;
    title: string;
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    initialTime?: number;
}

export function IframePlayer({
                                 src,
                                 title,
                                 unifiedVideoId,
                                 videoId,
                                 episodeId,
                                 episodeName,
                                 initialTime = 0
                             }: IframePlayerProps) {
    const [loading, setLoading] = useState(true);

    let finalSrc = src;
    if (initialTime > 0) {
        finalSrc += finalSrc.includes('?') ? `&seek=${initialTime}` : `?seek=${initialTime}`;
    }
    finalSrc += finalSrc.includes('?') ? `&autoplay=1` : `?autoplay=1`;

    // Use Abstraction Hook
    useVideoHeartbeat({
        unifiedVideoId,
        videoId,
        episodeId,
        episodeName,
        getPlaybackState: () => ({
            currentTime: 0,
            duration: 1, // Dummy duration to pass check
            isPlaying: true // Assume playing if iframe is loaded
        })
    });

    return (
        <div className="relative w-full h-full bg-black rounded-xl overflow-hidden border border-white/5 shadow-2xl">
            {loading && (
                <div className="absolute inset-0 flex flex-col items-center justify-center bg-neutral-900 z-10">
                    <Loader2 className="w-8 h-8 animate-spin text-indigo-500 mb-2" />
                    <span className="text-xs text-neutral-500 animate-pulse">正在加载第三方资源...</span>
                </div>
            )}

            <iframe
                src={finalSrc}
                title={title}
                className="w-full h-full border-none"
                allowFullScreen
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                sandbox="allow-scripts allow-same-origin allow-forms allow-presentation"
                onLoad={() => setLoading(false)}
            />

            <div className="absolute bottom-0 left-0 right-0 bg-black/60 backdrop-blur-sm py-1 px-3 text-[10px] text-neutral-500 flex justify-between items-center opacity-0 hover:opacity-100 transition-opacity pointer-events-none">
                <span>外部播放源: {new URL(src).hostname}</span>
                <span className="flex items-center gap-1">
                    如无法播放，请尝试 <a href={src} target="_blank" rel="noopener noreferrer" className="text-indigo-400 hover:underline pointer-events-auto flex items-center gap-0.5">新窗口打开 <ExternalLink className="w-2 h-2"/></a>
                </span>
            </div>
        </div>
    );
}