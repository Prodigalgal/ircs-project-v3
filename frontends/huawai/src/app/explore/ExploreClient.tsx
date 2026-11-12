"use client";

import { useState } from "react";
import { useAuth } from "@/lib/context/AuthContext";
import { MovieCard as MovieCardType, PortalMetadata } from "@/lib/types";
import dynamic from 'next/dynamic';

import { ExploreHeader } from "@/components/features/explore/ExploreHeader";
import { ExploreControls } from "@/components/features/explore/ExploreControls";
import { ExploreGrid } from "@/components/features/explore/ExploreGrid";

// Hook
import { useContentSearch, FilterState, DEFAULT_VALUE } from "@/hooks/useContentSearch";

const FilterModal = dynamic(
    () => import('@/components/features/search/FilterModal').then(mod => mod.FilterModal),
    { ssr: false }
);

const ExploreFilterPanel = dynamic(
    () => import('@/components/features/explore/ExploreFilterPanel').then(mod => mod.ExploreFilterPanel),
    { ssr: false }
);

interface ExploreClientProps {
    initialResults: MovieCardType[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
}

export default function ExploreClient(props: ExploreClientProps) {
    const { user } = useAuth();
    const canSearch = user?.status === "ACTIVE";

    // 使用核心抽象 Hook
    const {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        filterGroups,
        allCategories,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption
    } = useContentSearch({
        ...props,
        pageSize: 70 // Explore 页面单页加载更多
    });

    // UI State
    const [showFilters, setShowFilters] = useState(false);
    const [activeModalGroup, setActiveModalGroup] = useState<string | null>(null);

    const activeGroupConfig = activeModalGroup ? filterGroups.find(g => g.key === activeModalGroup) : null;
    const currentCategoryName = allCategories.find(c => c.slug === filters.category)?.name || "全站";

    const hasAdvancedFilters =
        filters.genre !== DEFAULT_VALUE ||
        filters.area !== DEFAULT_VALUE ||
        filters.year !== DEFAULT_VALUE ||
        filters.language !== DEFAULT_VALUE;

    // [Filter Logic]: Exclude 'category' from the expand panel, as it is handled by ExploreControls (Tabs).
    const panelFilterGroups = filterGroups.filter(g => g.key !== 'category');

    return (
        <div className="flex-none max-w-[1800px] mx-auto w-full z-20 space-y-6">
            <div className="mb-8 space-y-6">
                <ExploreHeader
                    canSearch={canSearch}
                    query={query}
                    setQuery={setQuery}
                    currentCategoryName={currentCategoryName}
                />

                <ExploreControls
                    showFilters={showFilters}
                    setShowFilters={setShowFilters}
                    hasAdvancedFilters={hasAdvancedFilters}
                    categories={allCategories}
                    currentCategorySlug={filters.category}
                    onCategoryChange={(slug) => handleFilterChange("category", slug)}
                    isLoading={isLoading}
                    totalResults={totalResults}
                    currentSort={filters.sort}
                    onSortChange={(val) => handleFilterChange("sort", val)}
                />

                {showFilters && (
                    <ExploreFilterPanel
                        isOpen={showFilters}
                        filterGroups={panelFilterGroups}
                        getCurrentValue={(key) => filters[key as keyof FilterState] || ""}
                        onFilterChange={handleFilterChange}
                        onShowMore={(key) => setActiveModalGroup(key)}
                        onClearAll={() => resetFilters()}
                    />
                )}
            </div>

            <ExploreGrid
                isLoading={isLoading}
                results={results}
                query={query}
                onClearAll={() => resetFilters()}
            />

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
