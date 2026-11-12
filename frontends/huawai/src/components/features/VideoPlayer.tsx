"use client";

import dynamic from "next/dynamic";
import { useMemo } from "react";
import { IframePlayer } from "./player/IframePlayer";
import { VideoFormat } from "@/lib/types";
import { VideoDomain } from "@/lib/domain/video";

const VidstackPlayer = dynamic(
    () => import("./player/VidstackPlayer").then((module) => module.VidstackPlayer),
    {
        ssr: false,
        loading: () => (
            <div className="w-full h-full bg-black rounded-xl flex items-center justify-center text-neutral-500 border border-white/10">
                <span className="text-sm">播放器加载中</span>
            </div>
        )
    }
);

interface VideoPlayerProps {
    src?: string;       // 实际播放地址
    posterUrl?: string; // 封面图
    unifiedVideoId?: string; // 聚合 ID
    videoId?: string;   // 具体的源视频 ID
    episodeId?: string; // 具体的集数 ID
    episodeName?: string; // 集数名称
    title?: string;     // 视频标题
    initialTime?: number; // 初始播放时间 (秒)
}

export function VideoPlayer({
                                src,
                                posterUrl,
                                unifiedVideoId,
                                videoId,
                                episodeId,
                                episodeName,
                                title = "Video",
                                initialTime = 0
                            }: VideoPlayerProps) {

    // 1. 策略分发：检测格式 (Delegated to Domain Service)
    const format: VideoFormat = useMemo(() => {
        return VideoDomain.detectFormat(src || "");
    }, [src]);

    // 2. 空状态处理
    if (!src) {
        return (
            <div className="w-full h-full bg-black rounded-xl flex items-center justify-center text-neutral-500 border border-white/10">
                <span className="text-sm">暂无播放资源</span>
            </div>
        );
    }

    // 3. 渲染具体的播放器内核
    if (format === 'hls' || format === 'mp4') {
        return (
            <VidstackPlayer
                src={src}
                poster={posterUrl}
                title={title}
                unifiedVideoId={unifiedVideoId}
                videoId={videoId}
                episodeId={episodeId}
                episodeName={episodeName}
                initialTime={initialTime}
            />
        );
    }

    return (
        <IframePlayer
            src={src}
            title={title}
            unifiedVideoId={unifiedVideoId}
            videoId={videoId}
            episodeId={episodeId}
            episodeName={episodeName}
            initialTime={initialTime}
        />
    );
}
