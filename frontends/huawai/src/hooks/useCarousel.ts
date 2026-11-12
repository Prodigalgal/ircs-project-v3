"use client";

import { useState, useEffect, useCallback, useRef } from "react";

interface UseCarouselProps {
    totalItems: number;
    interval?: number;
    autoPlay?: boolean;
}

export function useCarousel({ totalItems, interval = 6000, autoPlay = true }: UseCarouselProps) {
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);

    const nextSlideRef = useRef<() => void>(() => {});

    const nextSlide = useCallback(() => {
        setCurrentIndex((prev) => (prev + 1) % totalItems);
    }, [totalItems]);

    const prevSlide = useCallback(() => {
        setCurrentIndex((prev) => (prev - 1 + totalItems) % totalItems);
    }, [totalItems]);

    const goToSlide = useCallback((index: number) => {
        setCurrentIndex(index);
    }, []);

    useEffect(() => {
        nextSlideRef.current = nextSlide;
    }, [nextSlide]);

    useEffect(() => {
        if (isPaused || !autoPlay) return;

        const timer = setInterval(() => {
            nextSlideRef.current();
        }, interval);

        return () => clearInterval(timer);
    }, [isPaused, autoPlay, interval]);

    return {
        currentIndex,
        isPaused,
        setIsPaused,
        nextSlide,
        prevSlide,
        goToSlide
    };
}