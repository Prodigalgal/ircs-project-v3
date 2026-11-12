"use client";

import { SlidersHorizontal } from "lucide-react";
import { cn } from "@/lib/utils";
import { CategoryItem } from "@/lib/types";

interface ExploreControlsProps {
    showFilters: boolean;
    setShowFilters: (show: boolean) => void;
    hasAdvancedFilters: boolean;

    categories: CategoryItem[];
    currentCategorySlug: string;
    onCategoryChange: (slug: string) => void;

    isLoading: boolean;
    totalResults: number;

    currentSort: string;
    onSortChange: (sort: string) => void;
}

export function ExploreControls({
                                    showFilters,
                                    setShowFilters,
                                    hasAdvancedFilters,
                                    categories,
                                    currentCategorySlug,
                                    onCategoryChange,
                                    isLoading,
                                    totalResults,
                                    currentSort,
                                    onSortChange
                                }: ExploreControlsProps) {
    return (
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border-subtle/10 pb-6">
            <div className="flex flex-wrap items-center gap-4">
                <button
                    onClick={() => setShowFilters(!showFilters)}
                    className={cn(
                        "flex items-center justify-center gap-2 text-sm px-5 py-2.5 rounded-full border transition-all font-medium min-w-[100px]",
                        showFilters || hasAdvancedFilters
                            ? "bg-foreground text-background border-foreground shadow-lg shadow-foreground/10"
                            : "bg-secondary text-muted-foreground border-border-subtle/10 hover:text-foreground hover:bg-secondary/80 hover:border-border-subtle/20"
                    )}
                >
                    <SlidersHorizontal className="w-4 h-4" />
                    <span>筛选</span>
                    {hasAdvancedFilters && (
                        <span className="flex h-1.5 w-1.5 rounded-full bg-primary ring-2 ring-background" />
                    )}
                </button>

                <div className="h-6 w-px bg-border-subtle/10 hidden md:block" />

                <div className="flex flex-wrap gap-2">
                    {categories.length > 0 ? (
                        categories.map(cat => (
                            <button
                                key={cat.slug}
                                type="button"
                                onClick={() => onCategoryChange(cat.slug)}
                                className={cn(
                                    "px-4 py-2 rounded-lg text-xs font-bold transition-all border select-none",
                                    currentCategorySlug === cat.slug
                                        ? "bg-secondary text-foreground border-border-subtle/10 shadow-inner"
                                        : "text-muted-foreground border-transparent hover:text-foreground hover:bg-secondary/50"
                                )}
                            >
                                {cat.name}
                            </button>
                        ))
                    ) : (
                        Array.from({ length: 5 }).map((_, i) => (
                            <div key={i} className="h-8 w-16 bg-secondary/30 rounded-lg animate-pulse" />
                        ))
                    )}
                </div>
            </div>

            <div className="flex items-center justify-between md:justify-end gap-4 w-full md:w-auto">
                 <span className="text-xs font-mono text-muted-foreground uppercase tracking-widest hidden sm:inline-block">
                    {isLoading ? (
                        <span className="animate-pulse">Searching...</span>
                    ) : (
                        <span>{totalResults} ITEMS FOUND</span>
                    )}
                </span>

                <div className="flex gap-1 bg-secondary/30 p-1 rounded-lg border border-border-subtle/5">
                    {["latest", "rating", "trending"].map(sortKey => (
                        <button
                            key={sortKey}
                            type="button"
                            onClick={() => onSortChange(sortKey)}
                            className={cn(
                                "text-[10px] px-3 py-1.5 rounded-md transition-all font-bold",
                                currentSort === sortKey
                                    ? "bg-background text-foreground shadow-sm"
                                    : "text-muted-foreground hover:text-foreground hover:bg-background/50"
                            )}
                        >
                            {sortKey === "latest" ? "最新" : sortKey === "rating" ? "高分" : "热门"}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}
