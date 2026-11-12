"use client";

import { useRef, useEffect, useState, useCallback } from "react";
import { motion, useMotionValue, animate, useMotionValueEvent } from "framer-motion";
import { Server, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { VideoSource } from "@/lib/types";

interface SourceRailProps {
    sources: VideoSource[];
    activeSourceId: string;
    onSelect: (id: string) => void;
}

export function SourceRail({ sources, activeSourceId, onSelect }: SourceRailProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);

    const x = useMotionValue(0);

    const [constraints, setConstraints] = useState({ left: 0, right: 0 });
    const [showLeftArrow, setShowLeftArrow] = useState(false);
    const [showRightArrow, setShowRightArrow] = useState(false);
    const [isDragging, setIsDragging] = useState(false);

    const measureConstraints = useCallback(() => {
        if (!containerRef.current || !contentRef.current) return;

        const containerWidth = containerRef.current.clientWidth;
        const contentWidth = contentRef.current.scrollWidth;

        const minX = Math.min(0, containerWidth - contentWidth);

        setConstraints({ left: minX, right: 0 });

        const currentX = x.get();
        setShowLeftArrow(currentX < -10);
        setShowRightArrow(currentX > minX + 10);
    }, [x]);

    useEffect(() => {
        measureConstraints();
        const handleResize = () => measureConstraints();
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, [measureConstraints]);

    useMotionValueEvent(x, "change", (latest) => {
        setShowLeftArrow(latest < -10);
        setShowRightArrow(latest > constraints.left + 10);
    });

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        const handleWheel = (e: WheelEvent) => {
            if (constraints.left === 0) return;

            const isVertical = Math.abs(e.deltaY) > Math.abs(e.deltaX);
            const delta = isVertical ? e.deltaY : e.deltaX;

            if (Math.abs(delta) < 1) return;

            e.preventDefault();

            const currentX = x.get();
            const distance = delta;

            let targetX = currentX - distance;
            targetX = Math.max(constraints.left, Math.min(constraints.right, targetX));

            animate(x, targetX, {
                type: "tween",
                ease: "easeOut",
                duration: 0.3
            });
        };

        container.addEventListener("wheel", handleWheel, { passive: false });
        return () => container.removeEventListener("wheel", handleWheel);
    }, [constraints, x]);

    const manualScroll = (direction: 'left' | 'right') => {
        const currentX = x.get();
        const offset = containerRef.current ? containerRef.current.clientWidth / 2 : 300;

        let targetX = direction === 'left' ? currentX + offset : currentX - offset;
        targetX = Math.max(constraints.left, Math.min(constraints.right, targetX));

        animate(x, targetX, {
            type: "spring",
            stiffness: 250,
            damping: 25
        });
    };

    return (
        <div
            className="relative border-b border-border-subtle/10 bg-muted/20 group/rail select-none"
            ref={containerRef}
        >
            <div className="absolute inset-0 pointer-events-none z-20 flex justify-between items-center px-2">
                <motion.button
                    initial={false}
                    animate={{
                        opacity: showLeftArrow ? 1 : 0,
                        scale: showLeftArrow ? 1 : 0.8,
                        x: showLeftArrow ? 0 : -10
                    }}
                    onClick={() => manualScroll('left')}
                    className="pointer-events-auto p-2 rounded-full bg-card/80 border border-border-subtle/10 text-foreground backdrop-blur-md hover:bg-primary hover:border-primary hover:text-white transition-colors shadow-lg"
                >
                    <ChevronLeft className="w-4 h-4" />
                </motion.button>

                <motion.button
                    initial={false}
                    animate={{
                        opacity: showRightArrow ? 1 : 0,
                        scale: showRightArrow ? 1 : 0.8,
                        x: showRightArrow ? 0 : 10
                    }}
                    onClick={() => manualScroll('right')}
                    className="pointer-events-auto p-2 rounded-full bg-card/80 border border-border-subtle/10 text-foreground backdrop-blur-md hover:bg-primary hover:border-primary hover:text-white transition-colors shadow-lg"
                >
                    <ChevronRight className="w-4 h-4" />
                </motion.button>
            </div>

            <div
                className="overflow-hidden"
                style={{
                    maskImage: 'linear-gradient(to right, transparent 0%, black 5%, black 95%, transparent 100%)',
                    WebkitMaskImage: 'linear-gradient(to right, transparent 0%, black 5%, black 95%, transparent 100%)'
                }}
            >
                <motion.div
                    ref={contentRef}
                    style={{ x }}
                    drag="x"
                    dragConstraints={constraints}
                    dragElastic={0.1}
                    onDragStart={() => setIsDragging(true)}
                    onDragEnd={() => setTimeout(() => setIsDragging(false), 10)}
                    className="flex items-center py-5 px-6 gap-3 w-max cursor-grab active:cursor-grabbing touch-pan-y"
                >
                    <div className="w-2 flex-shrink-0" />

                    <div className="flex items-center gap-2 text-xs font-bold text-muted-foreground uppercase tracking-widest mr-4 flex-shrink-0 select-none border border-border-subtle/10 px-3 py-1.5 rounded-md bg-background/50">
                        <Server className="w-3 h-3" /> 线路
                    </div>

                    {sources.map((source) => (
                        <button
                            key={source.id}
                            onClick={() => {
                                if (!isDragging) onSelect(source.id);
                            }}
                            className={cn(
                                "flex-shrink-0 px-4 py-2 rounded-lg text-xs font-medium transition-all border whitespace-nowrap relative overflow-hidden group select-none outline-none focus:ring-2 focus:ring-primary/50",
                                activeSourceId === source.id
                                    ? "bg-card border-primary/50 text-primary shadow-[0_0_20px_rgba(99,102,241,0.15)]"
                                    : "bg-background/50 border-border-subtle/10 text-muted-foreground hover:bg-card hover:text-foreground hover:border-border-subtle/20"
                            )}
                        >
                            <span className="relative z-10">{source.name}</span>

                            {activeSourceId === source.id && (
                                <motion.div
                                    layoutId="activeSourceGlow"
                                    className="absolute inset-0 bg-primary/5 z-0"
                                    transition={{ duration: 0.3 }}
                                />
                            )}

                            <div className={cn(
                                "absolute bottom-0 left-0 w-full h-[2px] transition-colors duration-300",
                                activeSourceId === source.id ? "bg-primary" : "bg-transparent group-hover:bg-border-subtle/20"
                            )} />
                        </button>
                    ))}

                    <div className="w-2 flex-shrink-0" />
                </motion.div>
            </div>
        </div>
    );
}
