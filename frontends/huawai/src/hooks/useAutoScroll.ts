"use client";

import { useRef, useEffect, useCallback } from "react";

interface UseAutoScrollProps {
    itemCount: number;
    enabled?: boolean;
}

export function useAutoScroll({ itemCount, enabled = true }: UseAutoScrollProps) {
    const scrollRef = useRef<HTMLDivElement>(null);

    // 初始化位置
    useEffect(() => {
        const container = scrollRef.current;
        if (container && enabled && itemCount >= 3) {
            const setHeight = container.scrollHeight / 3;
            if (container.scrollTop < 50) container.scrollTop = setHeight;
        }
    }, [itemCount, enabled]);

    // 滚动监听与重置
    const handleScroll = useCallback(() => {
        const container = scrollRef.current;
        if (!container || !enabled || itemCount < 3) return;

        const scrollTop = container.scrollTop;
        const scrollHeight = container.scrollHeight;
        const setHeight = scrollHeight / 3;
        const buffer = 50;

        if (scrollTop < buffer) {
            container.scrollTop = scrollTop + setHeight;
        } else if (scrollTop >= setHeight * 2 - buffer) {
            container.scrollTop = scrollTop - setHeight;
        }
    }, [itemCount, enabled]);

    return { scrollRef, handleScroll };
}