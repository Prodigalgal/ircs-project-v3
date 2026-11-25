"use client";

import '@vidstack/react/player/styles/default/theme.css';
import '@vidstack/react/player/styles/default/layouts/video.css';

import { useRef } from 'react';
import {
    MediaPlayer,
    MediaProvider,
    MediaPlayerInstance,
    isHLSProvider,
    type MediaProviderAdapter
} from '@vidstack/react';
import { DefaultVideoLayout, defaultLayoutIcons } from '@vidstack/react/player/layouts/default';
import Hls from 'hls.js';
import { useToast } from '@/lib/context/ToastContext';
import { formatDuration } from '@/lib/utils';
import { useVideoHeartbeat } from '@/hooks/useVideoHeartbeat';

const CHINESE_TRANSLATIONS = {
    'Play': '播放',
    'Pause': '暂停',
    'Replay': '重播',
    'Mute': '静音',
    'Unmute': '取消静音',
    'Volume': '音量',
    'Enter Fullscreen': '进入全屏',
    'Exit Fullscreen': '退出全屏',
    'Settings': '设置',
    'Captions': '字幕',
    'Off': '关闭',
    'Quality': '画质',
    'Speed': '速度',
    'Normal': '正常',
    'Auto': '自动',
    'Loop': '循环播放',
    'AirPlay': '隔空播放',
    'Google Cast': '投屏',
    'Seek Backward': '快退',
    'Seek Forward': '快进',
    'Mini Player': '画中画',
    'Live': '直播',
    'Audio': '音轨',
    'Chapters': '章节',
};

interface VidstackPlayerProps {
    src: string;
    title: string;
    poster?: string;
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    initialTime?: number;
    onEnded?: () => void;
}

export function VidstackPlayer({
                                   src,
                                   title,
                                   poster,
                                   unifiedVideoId,
                                   videoId,
                                   episodeId,
                                   episodeName,
                                   initialTime = 0,
                                   onEnded
                               }: VidstackPlayerProps) {
    const playerRef = useRef<MediaPlayerInstance>(null);
    const { addToast } = useToast();
    const restoredSrcRef = useRef<string | null>(null);

    // Use Abstraction Hook
    const { report } = useVideoHeartbeat({
        unifiedVideoId,
        videoId,
        episodeId,
        episodeName,
        getPlaybackState: () => {
            const player = playerRef.current;
            if (!player) return { currentTime: 0, duration: 0, isPlaying: false };
            return {
                currentTime: player.currentTime,
                duration: player.duration,
                isPlaying: !player.paused
            };
        }
    });

    const handleCanPlay = () => {
        if (restoredSrcRef.current !== src && initialTime > 0 && playerRef.current) {
            const player = playerRef.current;
            if (initialTime < player.duration) {
                player.currentTime = initialTime;
                if (initialTime > 60 && initialTime < player.duration * 0.95) {
                    addToast(`已为您恢复至 ${formatDuration(Math.floor(initialTime / 60))} 处`, "info");
                }
            }
            restoredSrcRef.current = src;
        }
    };

    const onProviderChange = (provider: MediaProviderAdapter | null) => {
        if (isHLSProvider(provider)) {
            provider.library = Hls;
            if (!provider.config) provider.config = {};
            provider.config = {
                ...provider.config,
                xhrSetup: (xhr) => {
                    xhr.withCredentials = false;
                },
                lowLatencyMode: false,
                maxBufferHole: 2.0,
                maxMaxBufferLength: 60,
                manifestLoadingMaxRetry: 4,
                manifestLoadingTimeOut: 20000,
            };
        }
    };

    return (
        <MediaPlayer
            ref={playerRef}
            title={title}
            src={src}
            poster={poster}
            aspectRatio="16/9"
            autoPlay={true}
            onEnded={onEnded}
            onCanPlay={handleCanPlay}
            onProviderChange={onProviderChange}
            crossOrigin="anonymous"
            playsInline
            // Manual report on Pause
            onPause={() => report(false)}
            load="eager"
            streamType="on-demand"
            className="w-full h-full bg-black rounded-xl overflow-hidden shadow-2xl ring-1 ring-white/10"
        >
            <MediaProvider />
            <DefaultVideoLayout
                icons={defaultLayoutIcons}
                translations={CHINESE_TRANSLATIONS}
            />
        </MediaPlayer>
    );
}
