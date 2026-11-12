"use client";

import { Search as SearchIcon, X, Telescope } from "lucide-react";

interface ExploreHeaderProps {
    canSearch: boolean;
    query: string;
    setQuery: (val: string) => void;
    currentCategoryName: string;
}

export function ExploreHeader({
                                  canSearch,
                                  query,
                                  setQuery,
                                  currentCategoryName
                              }: ExploreHeaderProps) {
    if (canSearch) {
        return (
            <div className="relative group max-w-3xl animate-in fade-in slide-in-from-top-2 duration-500">
                <SearchIcon className="absolute left-0 top-1/2 -translate-y-1/2 w-6 h-6 text-muted-foreground transition-colors group-focus-within:text-primary" />
                <input
                    type="text"
                    placeholder={`在${currentCategoryName}中搜索...`}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    className="w-full bg-transparent border-b border-border-subtle/10 py-4 pl-10 text-xl md:text-3xl font-bold text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary transition-colors"
                    autoFocus={false}
                />
                {query && (
                    <button
                        onClick={() => setQuery("")}
                        className="absolute right-0 top-1/2 -translate-y-1/2 p-2 text-muted-foreground hover:text-foreground transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                )}
            </div>
        );
    }

    return (
        <div className="py-4 flex items-center gap-3 animate-in fade-in slide-in-from-top-2 duration-500">
            <div className="p-2 rounded-xl bg-secondary border border-border-subtle/10 text-muted-foreground">
                <Telescope className="w-8 h-8" />
            </div>
            <div>
                <h1 className="text-3xl font-bold text-foreground tracking-tight">
                    浏览 {currentCategoryName}
                </h1>
                <p className="text-xs text-muted-foreground mt-1">
                    登录并激活账号以解锁全文搜索功能
                </p>
            </div>
        </div>
    );
}