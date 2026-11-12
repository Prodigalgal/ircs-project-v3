"use client";

import React, { useCallback, useEffect, useState } from "react";
import { cn } from "@/lib/utils";

interface DualRangeSliderProps {
    min: number;
    max: number;
    value: [number, number];
    onChange: (value: [number, number]) => void;
    onCommit: (value: [number, number]) => void;
    className?: string;
}

export function DualRangeSlider({
                                    min,
                                    max,
                                    value,
                                    onChange,
                                    onCommit,
    className
                                }: DualRangeSliderProps) {
    const [localValue, setLocalValue] = useState(value);

    // Sync external value changes
    useEffect(() => {
        setLocalValue(value);
    }, [value]);

    const getPercent = useCallback(
        (val: number) => Math.round(((val - min) / (max - min)) * 100),
        [min, max]
    );

    const handleMinChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const val = Math.min(Number(event.target.value), localValue[1] - 1);
        const newValue: [number, number] = [val, localValue[1]];
        setLocalValue(newValue);
        onChange(newValue);
    };

    const handleMaxChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const val = Math.max(Number(event.target.value), localValue[0] + 1);
        const newValue: [number, number] = [localValue[0], val];
        setLocalValue(newValue);
        onChange(newValue);
    };

    const handleCommit = () => {
        onCommit(localValue);
    };

    return (
        <div className={cn("flex flex-col w-full gap-4 touch-none select-none", className)}>
            <div className="flex items-center justify-between text-sm font-bold font-mono text-muted-foreground px-1">
                <span>{localValue[0]}</span>
                <span>{localValue[1]}</span>
            </div>

            <div className="relative w-full h-6 flex items-center">
                {/* Visual Track */}
                <div className="absolute w-full h-1.5 bg-secondary/50 rounded-full overflow-hidden">
                    <div
                        className="absolute h-full bg-primary transition-all duration-75 ease-out"
                        style={{
                            left: `${getPercent(localValue[0])}%`,
                            width: `${getPercent(localValue[1]) - getPercent(localValue[0])}%`
                        }}
                    />
                </div>

                {/* Range Inputs */}
                <input
                    type="range"
                    min={min}
                    max={max}
                    value={localValue[0]}
                    onChange={handleMinChange}
                    onMouseUp={handleCommit}
                    onTouchEnd={handleCommit}
                    className="absolute w-full h-full appearance-none bg-transparent pointer-events-none z-30 focus:outline-none
                    [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
                    [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white
                    [&::-webkit-slider-thumb]:shadow-[0_0_10px_rgba(0,0,0,0.3)] [&::-webkit-slider-thumb]:appearance-none
                    [&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb]:active:cursor-grabbing
                    [&::-webkit-slider-thumb]:border [&::-webkit-slider-thumb]:border-border-subtle/50
                    [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4
                    [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:border-none
                    [&::-moz-range-thumb]:cursor-grab [&::-moz-range-thumb]:active:cursor-grabbing"
                />
                <input
                    type="range"
                    min={min}
                    max={max}
                    value={localValue[1]}
                    onChange={handleMaxChange}
                    onMouseUp={handleCommit}
                    onTouchEnd={handleCommit}
                    className="absolute w-full h-full appearance-none bg-transparent pointer-events-none z-40 focus:outline-none
                    [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
                    [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white
                    [&::-webkit-slider-thumb]:shadow-[0_0_10px_rgba(0,0,0,0.3)] [&::-webkit-slider-thumb]:appearance-none
                    [&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb]:active:cursor-grabbing
                    [&::-webkit-slider-thumb]:border [&::-webkit-slider-thumb]:border-border-subtle/50
                    [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4
                    [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:border-none
                    [&::-moz-range-thumb]:cursor-grab [&::-moz-range-thumb]:active:cursor-grabbing"
                />
            </div>
        </div>
    );
}
