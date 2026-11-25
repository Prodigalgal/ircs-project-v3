"use client";

import { use, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { contentApi } from "@/lib/api/content";
import { VideoPlayer } from "@/components/features/VideoPlayer";
import { WatchSidebar } from "@/components/features/WatchSidebar";
import { GlowButton } from "@/components/ui/GlowButton";
import { AlertCircle, ArrowLeft, Film, RefreshCw } from "lucide-react";
import { motion } from "framer-motion";
import { MovieDetail } from "@/lib/types";
import { useToast } from "@/lib/context/ToastContext";

type Props = {
    params: Promise<{ id: string }>;
};

export default function WatchPage({ params }: Props) {
    const { id: unifiedVideoId } = use(params);
    const searchParams = useSearchParams();
    const router = useRouter();
    const { addToast } = useToast();

    const [isLoading, setIsLoading] = useState(true);
    const [movie, setMovie] = useState<MovieDetail | null>(null);
    const [error, setError] = useState<string | null>(null);

    const sourceId = searchParams.get("source");
    const episodeId = searchParams.get("episode");

    // 解析时间戳 (默认 0)
    const initialTimeStr = searchParams.get("t");
    const initialTime = initialTimeStr ? parseInt(initialTimeStr, 10) : 0;

    useEffect(() => {
        const fetchMovie = async () => {
            try {
                const data = await contentApi.getMovieDetail(unifiedVideoId);
                setMovie(data);

                if (!data || !data.sources || data.sources.length === 0) {
                    setError("该影片暂无可用播放资源");
                    return;
                }

                const targetSource = sourceId ? data.sources.find(s => s.id === sourceId) : null;

                if (!targetSource) {
                    const defaultSource = data.sources[0];
                    const defaultEp = defaultSource.episodes[0];

                    if (defaultSource && defaultEp) {
                        if (sourceId) {
                            addToast("原播放线路已失效，已为您切换至最佳线路", "info");
                        }

                        router.replace(`/watch/${unifiedVideoId}?source=${defaultSource.id}&episode=${defaultEp.id}`);
                    } else {
                        setError("资源解析异常：线路为空");
                    }
                    return;
                }

                const targetEp = episodeId ? targetSource.episodes.find(e => e.id === episodeId) : null;
                if (!targetEp) {
                    const firstEp = targetSource.episodes[0];
                    if (firstEp) {
                        addToast("所选集数已失效，正在播放第一集", "info");
                        router.replace(`/watch/${unifiedVideoId}?source=${targetSource.id}&episode=${firstEp.id}`);
                    } else {
                        setError("该线路暂无集数");
                    }
                    return;
                }

            } catch (err: unknown) {
                console.error(err);
                setError("无法加载影片信息，请检查网络");
            } finally {
                setIsLoading(false);
            }
        };

        fetchMovie();
    }, [unifiedVideoId, sourceId, episodeId, router, addToast]);

    if (isLoading) {
        return (
            <div className="min-h-screen pt-20 flex items-center justify-center bg-[#030305]">
                <div className="flex flex-col items-center gap-4">
                    <div className="relative">
                        <div className="w-12 h-12 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
                        <div className="absolute inset-0 flex items-center justify-center">
                            <RefreshCw className="w-4 h-4 text-indigo-500 animate-pulse" />
                        </div>
                    </div>
                    <span className="text-neutral-500 text-sm animate-pulse font-mono">
                        ANALYZING STREAM...
                    </span>
                </div>
            </div>
        );
    }

    if (error || !movie || !sourceId || !episodeId) {
        return (
            <div className="min-h-screen pt-20 flex flex-col items-center justify-center text-center px-4 bg-[#030305]">
                <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center mb-6 border border-red-500/20">
                    <AlertCircle className="w-10 h-10 text-red-500" />
                </div>
                <h1 className="text-2xl font-bold text-white mb-2">信号中断</h1>
                <p className="text-neutral-400 mb-8 max-w-md">{error || "连接至媒体服务器失败"}</p>
                <GlowButton onClick={() => router.push(`/movie/${unifiedVideoId}`)} icon={<ArrowLeft className="w-4 h-4" />}>
                    返回详情页
                </GlowButton>
            </div>
        );
    }

    const currentSource = movie.sources.find(s => s.id === sourceId);
    const currentEpisode = currentSource?.episodes.find(e => e.id === episodeId);

    if (!currentSource || !currentEpisode) {
        return null;
    }

    return (
        <div className="min-h-screen bg-[#030305] pt-20 flex flex-col relative overflow-hidden">
            <div className="absolute inset-0 -z-10">
                <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-indigo-900/10 to-transparent pointer-events-none" />
            </div>

            <main className="flex-1 flex flex-col items-center px-6 pb-12 w-full max-w-[1600px] mx-auto">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="w-full flex items-center justify-between mb-6 border-b border-white/5 pb-4"
                >
                    <div className="flex items-center gap-4">
                        <button
                            onClick={() => router.push(`/movie/${unifiedVideoId}`)}
                            className="p-2 -ml-2 text-neutral-400 hover:text-white hover:bg-white/5 rounded-full transition-all"
                        >
                            <ArrowLeft className="w-5 h-5" />
                        </button>
                        <div>
                            <h1 className="text-xl md:text-2xl font-bold text-white flex items-center gap-3">
                                {movie.title}
                                <span className="hidden md:inline-flex px-2 py-0.5 rounded text-[10px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                                    {currentSource.name}
                                </span>
                            </h1>
                            <p className="text-sm text-neutral-400 font-mono mt-0.5 flex items-center gap-2">
                                <Film className="w-3 h-3" />
                                正在播放: <span className="text-white">{currentEpisode.title}</span>
                            </p>
                        </div>
                    </div>
                </motion.div>

                <motion.div
                    layout
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="relative w-full aspect-video max-h-[80vh] bg-black rounded-xl overflow-hidden shadow-2xl ring-1 ring-white/10 z-10"
                >
                    <VideoPlayer
                        src={currentEpisode.url}
                        posterUrl={movie.posterUrl}
                        title={currentEpisode.title}
                        unifiedVideoId={unifiedVideoId}
                        videoId={currentSource.id}
                        episodeId={currentEpisode.id}
                        episodeName={currentEpisode.title}
                        initialTime={initialTime}
                    />
                </motion.div>
            </main>

            <WatchSidebar
                movie={movie}
                currentSourceId={sourceId}
                currentEpisodeId={episodeId}
            />
        </div>
    );
}