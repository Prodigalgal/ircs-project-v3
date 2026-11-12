"use client";

import { useState } from "react";
import { Search as SearchIcon, SlidersHorizontal, Trash2, ChevronDown, Loader2 } from "lucide-react";
import dynamic from 'next/dynamic';

import { MovieCard as MovieCardType, PortalMetadata } from "@/lib/types";
import { cn } from "@/lib/utils";
import { MovieCard } from "@/components/features/MovieCard";
import { DualRangeSlider } from "@/components/ui/DualRangeSlider";

// Hook
import { useContentSearch, FilterState, DEFAULT_VALUE, DEFAULT_CATEGORY_SLUG, DEFAULT_SORT } from "@/hooks/useContentSearch";

const FilterModal = dynamic(
    () => import('@/components/features/search/FilterModal').then(mod => mod.FilterModal),
    { ssr: false }
);

interface SearchClientProps {
    initialResults: MovieCardType[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
}

export function SearchClient(props: SearchClientProps) {
    const {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        filterGroups,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption
    } = useContentSearch(props);

    // UI State
    const [showFilters, setShowFilters] = useState(true);
    const [activeModalGroup, setActiveModalGroup] = useState<string | null>(null);

    const activeGroupConfig = activeModalGroup ? filterGroups.find(g => g.key === activeModalGroup) : null;
    const isDefaultState = !query &&
        filters.category === DEFAULT_CATEGORY_SLUG &&
        filters.sort === DEFAULT_SORT &&
        filters.genre === DEFAULT_VALUE &&
        filters.area === DEFAULT_VALUE &&
        filters.year === DEFAULT_VALUE &&
        filters.language === DEFAULT_VALUE;

    // Helper: Parse range value for slider
    const parseRangeValue = (val: string, min: number, max: number): [number, number] => {
        if (!val || val === DEFAULT_VALUE || val === "all") return [min, max];

        if (val.includes("-")) {
            const [s, e] = val.split("-").map(Number);
            return [s || min, e || max];
        }

        // Handle "lt1990" legacy format
        if (val.startsWith("lt")) {
            const limit = Number(val.replace("lt", ""));
            return [min, limit - 1];
        }

        const single = Number(val);
        if (!isNaN(single) && single > 0) {
            return [single, single];
        }

        return [min, max];
    };

    return (
        <div className="w-full flex flex-col gap-8">
            {/* --- 顶部搜索栏 --- */}
            <div className="relative group max-w-5xl mx-auto w-full z-30">
                <SearchIcon className="absolute left-0 top-1/2 -translate-y-1/2 w-6 h-6 text-neutral-500 transition-colors group-focus-within:text-white" />
                <input
                    type="text"
                    placeholder="搜索电影、剧集、动漫... (支持 4K/HDR 关键词)"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    className="w-full bg-transparent border-b border-white/10 py-4 pl-10 text-xl md:text-3xl font-bold text-white placeholder:text-neutral-700 focus:outline-none focus:border-indigo-500 transition-colors"
                />

                <div className="absolute right-0 top-1/2 -translate-y-1/2 flex items-center gap-4">
                    {!isDefaultState && (
                        <button
                            onClick={() => resetFilters()}
                            className="flex items-center gap-2 text-xs md:text-sm text-neutral-500 hover:text-red-400 transition-colors"
                        >
                            <Trash2 className="w-4 h-4" />
                            <span className="hidden md:inline">重置</span>
                        </button>
                    )}
                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className={cn(
                            "flex items-center gap-2 text-xs md:text-sm px-4 py-2 rounded-full border transition-all select-none",
                            showFilters
                                ? "bg-indigo-500/10 text-indigo-400 border-indigo-500/30"
                                : "bg-white/5 text-neutral-500 border-white/10 hover:text-white"
                        )}
                    >
                        <SlidersHorizontal className="w-4 h-4" />
                        筛选
                    </button>
                </div>
            </div>

            {/* --- 筛选面板 --- */}
            {showFilters && (
                <div className="w-full max-w-7xl mx-auto overflow-hidden animate-in fade-in slide-in-from-top-1 duration-200">
                    <div className="bg-[#0a0a0c] border border-white/5 rounded-2xl p-6 space-y-5">
                        {filterGroups.map((group) => {
                            // Special Handling for Year: Use Slider
                            if (group.key === "year") {
                                const currentYear = new Date().getFullYear() + 1;
                                const minYear = 1900;
                                const maxYear = currentYear;
                                const [currentStart, currentEnd] = parseRangeValue(
                                    filters.year,
                                    minYear,
                                    maxYear
                                );

                                return (
                                    <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start sm:items-center">
                                        <span className="text-[10px] md:text-xs font-bold text-neutral-500 uppercase tracking-wider w-12 pt-1.5 sm:pt-0 shrink-0">
                                            {group.label}
                                        </span>
                                        <div className="flex-1 w-full max-w-xl px-2">
                                            <DualRangeSlider
                                                min={minYear}
                                                max={maxYear}
                                                value={[currentStart, currentEnd]}
                                                onChange={() => {}} // 仅仅是视觉更新，不实时触发搜索
                                                onCommit={(val) => {
                                                    if (val[0] === minYear && val[1] === maxYear) {
                                                        handleFilterChange(group.key, DEFAULT_VALUE);
                                                    } else if (val[0] === val[1]) {
                                                        handleFilterChange(group.key, String(val[0]));
                                                    } else {
                                                        handleFilterChange(group.key, `${val[0]}-${val[1]}`);
                                                    }
                                                }}
                                            />
                                        </div>
                                    </div>
                                );
                            }

                            // Standard Buttons for other filters
                            const VISIBLE_LIMIT = 12;
                            const hasMore = group.options.length > VISIBLE_LIMIT;
                            const visibleOptions = group.options.slice(0, VISIBLE_LIMIT);

                            return (
                                <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start sm:items-center">
                                    <span className="text-[10px] md:text-xs font-bold text-neutral-500 uppercase tracking-wider w-12 pt-1.5 sm:pt-0 shrink-0">
                                        {group.label}
                                    </span>
                                    <div className="flex flex-wrap gap-2 flex-1">
                                        {visibleOptions.map((opt) => {
                                            const isSelected = filters[group.key as keyof FilterState] === opt;
                                            return (
                                                <button
                                                    key={opt}
                                                    onClick={() => handleFilterChange(group.key, opt)}
                                                    className={cn(
                                                        "px-3 py-1.5 rounded-lg text-xs transition-all border select-none",
                                                        isSelected
                                                            ? "bg-indigo-600 border-indigo-500 text-white shadow-lg shadow-indigo-500/20"
                                                            : "bg-white/5 border-white/5 text-neutral-400 hover:bg-white/10 hover:text-white hover:border-white/10"
                                                    )}
                                                >
                                                    {getDisplayOption(group.key, opt)}
                                                </button>
                                            );
                                        })}
                                        {hasMore && (
                                            <button
                                                onClick={() => setActiveModalGroup(group.key)}
                                                className="px-3 py-1.5 rounded-lg text-xs bg-white/5 border border-white/5 text-indigo-400 hover:bg-white/10 hover:text-white hover:border-white/20 transition-all flex items-center gap-1"
                                            >
                                                更多 <ChevronDown className="w-3 h-3" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* --- 结果展示区 --- */}
            <div className="flex-1 max-w-7xl mx-auto w-full flex flex-col gap-6 relative min-h-[400px]">

                {/* 结果统计与排序 */}
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-white/5 pb-4">
                    <p className="text-sm text-neutral-500 font-mono flex items-center gap-2">
                        {isLoading ? (
                            <span className="flex items-center gap-2 text-indigo-400 animate-pulse">
                                <Loader2 className="w-3 h-3 animate-spin" /> SEARCHING...
                            </span>
                        ) : (
                            <span>RESULT: <span className="text-white font-bold">{totalResults}</span> ITEMS</span>
                        )}
                    </p>
                    <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                        {["latest", "rating", "trending"].map(sortKey => (
                            <button
                                key={sortKey}
                                onClick={() => handleFilterChange("sort", sortKey)}
                                className={cn(
                                    "text-xs px-3 py-1.5 rounded-md transition-all font-bold",
                                    filters.sort === sortKey
                                        ? "bg-white/10 text-white shadow-sm"
                                        : "text-neutral-500 hover:text-neutral-300 hover:bg-white/5"
                                )}
                            >
                                {sortKey === "latest" ? "最新" : sortKey === "rating" ? "评分" : "热门"}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Grid 列表 */}
                {results.length > 0 ? (
                    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-3 md:gap-4 md:gap-y-8 animate-in fade-in duration-200">
                        {results.map((movie, idx) => (
                            <div key={movie.id} className="animate-in fade-in zoom-in-95 duration-200">
                                <MovieCard movie={movie} index={idx} />
                            </div>
                        ))}
                    </div>
                ) : (
                    !isLoading && (
                        <div className="flex flex-col items-center justify-center py-20 text-neutral-600 border border-dashed border-white/5 rounded-2xl bg-white/[0.01] animate-in fade-in duration-200">
                            <SearchIcon className="w-12 h-12 mb-4 opacity-20" />
                            <p className="text-lg font-medium text-neutral-400">没有找到匹配内容</p>
                            <p className="text-sm opacity-60 mt-2">
                                尝试搜索 &#34;4K&#34;、&#34;Netflix&#34; 或调整筛选条件
                            </p>
                        </div>
                    )
                )}
            </div>

            {activeGroupConfig && (
                <FilterModal
                    isOpen={!!activeModalGroup}
                    onClose={() => setActiveModalGroup(null)}
                    title={activeGroupConfig.label}
                    options={activeGroupConfig.options}
                    currentValue={filters[activeGroupConfig.key as keyof FilterState] || ""}
                    onSelect={(val) => handleFilterChange(activeGroupConfig.key, val)}
                    formatLabel={(val) => getDisplayOption(activeGroupConfig.key, val)}
                />
            )}
        </div>
    );
}
