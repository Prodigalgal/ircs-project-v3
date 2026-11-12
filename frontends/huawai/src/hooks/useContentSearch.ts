"use client";

import { useState, useCallback, useEffect, useMemo, useRef } from "react";
import { usePathname } from "next/navigation";
import { useDebounce } from "@/hooks/useDebounce";
import { contentApi } from "@/lib/api/content";
import { CategoryItem, MovieCard, PortalMetadata } from "@/lib/types";
import { getTotalElements } from "@/lib/pagination";
import { DEFAULT_CATEGORY_SLUG as PORTAL_DEFAULT_CATEGORY_SLUG, normalizePortalCategories } from "@/lib/portalCategories";

// --- Types ---

export interface FilterState {
    category: string;
    sort: string;
    genre: string;
    area: string;
    year: string;
    language: string;
    keyword: string;
}

export interface UseContentSearchProps {
    initialResults: MovieCard[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
    pageSize?: number;
}

// --- Constants ---

export const DEFAULT_VALUE = "全部";
export const DEFAULT_CATEGORY_SLUG = PORTAL_DEFAULT_CATEGORY_SLUG;
export const DEFAULT_SORT = "latest";

export interface FilterGroupConfig {
    key: keyof FilterState;
    label: string;
    options: string[];
}

// --- Hook Implementation ---

export function useContentSearch({
                                     initialResults,
                                     initialTotal,
                                     initialMetadata,
                                     initialParams,
                                     pageSize = 50
                                 }: UseContentSearchProps) {
    const pathname = usePathname();

    // 1. Core Data State
    const [results, setResults] = useState<MovieCard[]>(initialResults);
    const [totalResults, setTotalResults] = useState(initialTotal);
    const [metadata, setMetadata] = useState<PortalMetadata | null>(initialMetadata);
    const [isLoading, setIsLoading] = useState(false);

    // 2. Filter & Query State
    const [filters, setFilters] = useState<FilterState>(initialParams);
    const [query, setQuery] = useState(initialParams.keyword);
    const debouncedQuery = useDebounce(query, 600);

    // 3. Metadata Hydration (Client-side fallback)
    useEffect(() => {
        if (!initialMetadata) {
            contentApi.getMetadata().then(setMetadata).catch(console.error);
        }
    }, [initialMetadata]);

    // 4. Reset Data on Initial Props Change (External Navigation)
    // 当通过 Link 跳转或 URL 变化时，重置本地状态以匹配服务端数据
    useEffect(() => {
        setResults(initialResults);
        setTotalResults(initialTotal);
        setQuery(initialParams.keyword);
        setFilters(initialParams);
    }, [initialResults, initialTotal, initialParams]);

    // 5. Smart Options Generation
    const allCategories: CategoryItem[] = useMemo(() => {
        return [{ name: "全站", slug: DEFAULT_CATEGORY_SLUG }, ...normalizePortalCategories(metadata?.categories)];
    }, [metadata]);

    const yearOptions = useMemo(() => {
        const currentYear = new Date().getFullYear();
        const years = [DEFAULT_VALUE];
        for (let i = currentYear + 1; i >= currentYear - 4; i--) {
            years.push(String(i));
        }
        const currentDecadeStart = Math.floor(currentYear / 10) * 10;
        for (let d = currentDecadeStart - 10; d >= 1990; d -= 10) {
            years.push(`${d}-${d + 9}`);
        }
        years.push("lt1990");
        return years;
    }, []);

    const filterGroups: FilterGroupConfig[] = useMemo(() => ([
        { key: "category", label: "分类", options: allCategories.map(c => c.slug) },
        { key: "genre", label: "题材", options: [DEFAULT_VALUE, ...(metadata?.genres || [])] },
        { key: "area", label: "地区", options: [DEFAULT_VALUE, ...(metadata?.areas || [])] },
        { key: "year", label: "年代", options: yearOptions },
        { key: "language", label: "语言", options: [DEFAULT_VALUE, ...(metadata?.languages || [])] },
    ]), [allCategories, metadata, yearOptions]);

    // 6. Search Execution
    const performSearch = useCallback(async (currentFilters: FilterState, searchQuery: string) => {
        setIsLoading(true);
        try {
            const res = await contentApi.explore({
                page: 0,
                size: pageSize,
                category: currentFilters.category,
                sort: currentFilters.sort,
                keyword: searchQuery,
                genre: currentFilters.genre === DEFAULT_VALUE ? undefined : currentFilters.genre,
                area: currentFilters.area === DEFAULT_VALUE ? undefined : currentFilters.area,
                year: currentFilters.year === DEFAULT_VALUE ? undefined : currentFilters.year,
                language: currentFilters.language === DEFAULT_VALUE ? undefined : currentFilters.language,
            });
            setResults(res.content || []);
            setTotalResults(getTotalElements(res));
        } catch (err) {
            console.error("Search failed", err);
        } finally {
            setIsLoading(false);
        }
    }, [pageSize]);

    // 7. URL Sync & Trigger
    // 关键修复：正确处理路由跳转(Props Change)与用户交互(Interaction)的冲突
    const prevParamsRef = useRef(JSON.stringify(initialParams));

    useEffect(() => {
        const currentParamsStr = JSON.stringify(initialParams);

        // [CRITICAL FIX]: 检测是否是外部导航导致的更新
        // 如果 props (initialParams) 发生了变化，说明是路由跳转。
        // 此时 filters 状态可能还是旧的（因为 setState 是异步的）。
        // 我们必须阻断本次 Effect 的执行，防止用旧的 filters 覆盖新的 URL。
        if (currentParamsStr !== prevParamsRef.current) {
            prevParamsRef.current = currentParamsStr;
            return;
        }

        // 下面是处理用户在当前页面点击筛选器/搜索的逻辑
        const isInitialState =
            debouncedQuery === initialParams.keyword &&
            filters.category === initialParams.category &&
            filters.sort === initialParams.sort &&
            filters.genre === initialParams.genre &&
            filters.area === initialParams.area &&
            filters.year === initialParams.year &&
            filters.language === initialParams.language;

        // 如果状态与初始状态一致（即没有用户交互产生的变更），则不执行搜索和 URL 更新
        if (isInitialState) return;

        // 执行搜索
        performSearch(filters, debouncedQuery);

        // 更新 URL (仅在用户交互时)
        const params = new URLSearchParams();
        if (debouncedQuery) params.set("keyword", debouncedQuery);
        if (filters.category !== DEFAULT_CATEGORY_SLUG) params.set("type", filters.category);
        if (filters.genre !== DEFAULT_VALUE) params.set("genre", filters.genre);
        if (filters.area !== DEFAULT_VALUE) params.set("area", filters.area);
        if (filters.year !== DEFAULT_VALUE) params.set("year", filters.year);
        if (filters.language !== DEFAULT_VALUE) params.set("language", filters.language);
        if (filters.sort !== DEFAULT_SORT) params.set("sort", filters.sort);

        const queryString = params.toString();
        const newUrl = queryString ? `${pathname}?${queryString}` : pathname;
        window.history.replaceState({ ...window.history.state, as: newUrl, url: newUrl }, "", newUrl);

    }, [debouncedQuery, filters, performSearch, pathname, initialParams]);

    // 8. Actions
    const handleFilterChange = (key: string, value: string) => {
        setFilters(prev => ({ ...prev, [key as keyof FilterState]: value }));
    };

    const resetFilters = (overrideDefaults?: Partial<FilterState>) => {
        setQuery("");
        const defaults: FilterState = {
            category: DEFAULT_CATEGORY_SLUG,
            sort: DEFAULT_SORT,
            genre: DEFAULT_VALUE,
            area: DEFAULT_VALUE,
            year: DEFAULT_VALUE,
            language: DEFAULT_VALUE,
            keyword: "",
            ...overrideDefaults
        };
        setFilters(defaults);
        // 重置时也需要更新 URL 状态，否则返回上一页可能有问题
        window.history.replaceState(null, "", pathname);
        performSearch(defaults, "");
    };

    // 9. Helpers
    const getDisplayOption = (key: string, val: string) => {
        if (key === "category") {
            const found = allCategories.find(c => c.slug === val);
            return found ? found.name : val;
        }
        if (key === "year") {
            if (val.includes("-")) return val.replace("-", "至") + "年";
            if (val.startsWith("lt")) return val.replace("lt", "") + "年以前";
            if (val === DEFAULT_VALUE) return val;
            return val + "年";
        }
        return val;
    };

    return {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        metadata,
        allCategories,
        filterGroups,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption,
    };
}
