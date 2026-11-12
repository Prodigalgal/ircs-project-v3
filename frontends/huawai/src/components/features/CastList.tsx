"use client";

import { useState } from "react";
import { ChevronDown, ChevronUp, User } from "lucide-react";
import { CastMember } from "@/lib/types";
import { cn } from "@/lib/utils";

interface CastListProps {
    cast: CastMember[];
}

export function CastList({ cast }: CastListProps) {
    const [isExpanded, setIsExpanded] = useState(false);

    const INITIAL_COUNT = 8;
    const hasMore = cast.length > INITIAL_COUNT;

    return (
        <div className="relative">
            <div
                style={{
                    maxHeight: isExpanded || !hasMore ? "4000px" : "130px",
                }}
                className={cn(
                    "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 overflow-hidden relative transition-[max-height] duration-500 ease-out",
                    !isExpanded && hasMore && "after:absolute after:bottom-0 after:left-0 after:right-0 after:h-24 after:bg-gradient-to-t after:from-background via-background/80 to-transparent after:pointer-events-none after:z-10"
                )}
            >
                {cast.map((actor) => (
                    <div
                        key={actor.id}
                        className="group flex items-center gap-3 px-4 py-3 rounded-lg bg-card/40 border border-border-subtle/10 hover:bg-card/80 hover:border-border-subtle/20 transition-all duration-200 cursor-default"
                    >
                        <div className="p-1.5 rounded-full bg-secondary/50 text-muted-foreground group-hover:text-primary group-hover:bg-primary/10 transition-colors flex-shrink-0">
                            <User className="w-3.5 h-3.5" />
                        </div>

                        <div className="min-w-0">
                            <div className="text-sm font-medium text-foreground group-hover:text-primary transition-colors truncate">
                                {actor.name}
                            </div>
                            <div className="text-xs text-muted-foreground group-hover:text-muted-foreground/80 truncate">
                                {actor.role}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {hasMore && (
                <div className={cn(
                    "flex justify-center transition-all duration-500 ease-out relative z-20",
                    isExpanded ? "mt-6" : "-mt-4"
                )}>
                    <button
                        onClick={() => setIsExpanded(!isExpanded)}
                        className="flex items-center gap-2 px-5 py-2 rounded-full bg-card border border-border-subtle/10 text-xs font-bold text-muted-foreground hover:text-foreground hover:border-primary/50 hover:bg-secondary hover:shadow-[0_0_20px_rgba(99,102,241,0.15)] hover:scale-105 active:scale-95 transition-all shadow-lg backdrop-blur-md group"
                    >
                        {isExpanded ? (
                            <>
                                收起 <ChevronUp className="w-3.5 h-3.5 group-hover:-translate-y-0.5 transition-transform" />
                            </>
                        ) : (
                            <>
                                查看全部 ({cast.length}) <ChevronDown className="w-3.5 h-3.5 group-hover:translate-y-0.5 transition-transform" />
                            </>
                        )}
                    </button>
                </div>
            )}
        </div>
    );
}
