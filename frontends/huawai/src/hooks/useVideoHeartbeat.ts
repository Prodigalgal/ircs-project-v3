"use client";

import { useEffect, useCallback } from "react";
import { useAuth } from "@/lib/context/AuthContext";
import { interactionApi } from "@/lib/api/interaction";

interface PlaybackState {
    currentTime: number;
    duration: number;
    isPlaying: boolean;
}

interface UseVideoHeartbeatProps {
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    /**
     * A callback that returns the current state of the player.
     * This avoids stale closures in setInterval.
     */
    getPlaybackState: () => PlaybackState;
    /**
     * How often to report progress in milliseconds. Default 15000 (15s).
     */
    intervalMs?: number;
}

export function useVideoHeartbeat({
                                      unifiedVideoId,
                                      videoId,
                                      episodeId,
                                      episodeName,
                                      getPlaybackState,
                                      intervalMs = 15000
                                  }: UseVideoHeartbeatProps) {
    const { user, isStealthMode } = useAuth();

    const report = useCallback((useKeepAlive = false) => {
        // 1. Check Auth & Stealth Mode
        if (!user || isStealthMode) return;

        // 2. Validate Metadata
        if (!unifiedVideoId || !videoId || !episodeName) return;

        // 3. Get Realtime State
        const { currentTime, duration, isPlaying } = getPlaybackState();

        // 4. Validate State (Don't report if paused and not forcing keepAlive, or if invalid duration)
        // Note: For keepAlive (tab close), we report even if paused to save exact spot.
        // For interval, we usually only report if playing.
        if (!useKeepAlive && !isPlaying) return;
        if (duration <= 0) return;

        // 5. API Call
        interactionApi.reportProgress(
            unifiedVideoId,
            videoId,
            episodeId,
            episodeName,
            Math.floor(currentTime),
            Math.floor(duration),
            useKeepAlive
        ).catch(console.error);
    }, [user, isStealthMode, unifiedVideoId, videoId, episodeId, episodeName, getPlaybackState]);

    // Setup Interval
    useEffect(() => {
        const timer = setInterval(() => {
            if (document.visibilityState === 'visible') {
                report(false);
            }
        }, intervalMs);

        return () => clearInterval(timer);
    }, [report, intervalMs]);

    // Setup BeforeUnload
    useEffect(() => {
        const handleBeforeUnload = () => report(true);
        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [report]);

    // Return manual report function if needed (e.g. on Pause event)
    return { report };
}
