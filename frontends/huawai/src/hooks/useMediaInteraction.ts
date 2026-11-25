"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { interactionApi } from "@/lib/api/interaction";
import { getErrorMessage } from "@/lib/utils";

interface UseMediaInteractionResult {
    isFavorite: boolean;
    isLoading: boolean;
    toggleFavorite: (e?: React.MouseEvent) => Promise<void>;
    checkFavoriteStatus: () => Promise<void>;
}

export function useMediaInteraction(unifiedVideoId: string): UseMediaInteractionResult {
    const { user } = useAuth();
    const { addToast } = useToast();
    const router = useRouter();

    const [isFavorite, setIsFavorite] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [hasChecked, setHasChecked] = useState(false);

    // Check status on mount if user exists
    const checkFavoriteStatus = useCallback(async () => {
        if (!user || !unifiedVideoId) return;
        try {
            const status = await interactionApi.checkFavorite(unifiedVideoId);
            setIsFavorite(status);
            setHasChecked(true);
        } catch (error) {
            console.error("Failed to check favorite status", error);
            setHasChecked(true);
        }
    }, [user, unifiedVideoId]);

    useEffect(() => {
        if (user && unifiedVideoId && !hasChecked) {
            checkFavoriteStatus();
        }
    }, [user, unifiedVideoId, hasChecked, checkFavoriteStatus]);

    const toggleFavorite = async (e?: React.MouseEvent) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }

        if (!user) {
            addToast("请先登录以收藏影片", "error");
            router.push("/login");
            return;
        }

        if (isLoading) return;

        setIsLoading(true);
        try {
            const newState = await interactionApi.toggleFavorite(unifiedVideoId);
            setIsFavorite(newState);
            addToast(newState ? "已加入收藏" : "已取消收藏", newState ? "success" : "info");
        } catch (err) {
            addToast(getErrorMessage(err) || "操作失败，请重试", "error");
        } finally {
            setIsLoading(false);
        }
    };

    return {
        isFavorite,
        isLoading,
        toggleFavorite,
        checkFavoriteStatus
    };
}