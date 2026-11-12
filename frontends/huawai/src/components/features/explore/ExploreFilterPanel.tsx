"use client";

import { motion, AnimatePresence } from "framer-motion";
import { SlidersHorizontal, ChevronDown, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { DualRangeSlider } from "@/components/ui/DualRangeSlider";

export interface FilterGroup {
    key: string;
    label: string;
    options: string[];
    type?: "select" | "range";
    min?: number;
    max?: number;
}

interface ExploreFilterPanelProps {
    isOpen: boolean;
    filterGroups: FilterGroup[];
    getCurrentValue: (key: string) => string;
    onFilterChange: (key: string, value: string) => void;
    onShowMore: (groupKey: string) => void;
    onClearAll: () => void;
}

export function ExploreFilterPanel({
                                       isOpen,
                                       filterGroups,
                                       getCurrentValue,
                                       onFilterChange,
                                       onShowMore,
                                       onClearAll
                                   }: ExploreFilterPanelProps) {

    const parseRangeValue = (val: string, min: number, max: number): [number, number] => {
        if (!val || val === "全部") return [min, max];

        if (val.includes("-")) {
            const [s, e] = val.split("-").map(Number);
            return [s || min, e || max];
        }

        const single = Number(val);
        if (!isNaN(single) && single > 0) {
            return [single, single];
        }

        return [min, max];
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="overflow-hidden"
                >
                    <div className="bg-card border border-border-subtle/10 rounded-2xl p-6 space-y-8 relative overflow-hidden shadow-sm">
                        <div className="absolute top-0 right-0 p-4 opacity-10 pointer-events-none">
                            <SlidersHorizontal className="w-24 h-24 text-muted-foreground" />
                        </div>

                        {filterGroups.map((group) => {
                            if (group.key === "year") {
                                const currentYear = new Date().getFullYear() + 1;
                                const minYear = 1900;
                                const maxYear = currentYear;
                                const [currentStart, currentEnd] = parseRangeValue(
                                    getCurrentValue(group.key),
                                    minYear,
                                    maxYear
                                );

                                return (
                                    <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start">
                                        <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider w-16 pt-2 shrink-0">
                                            {group.label}
                                        </span>
                                        <div className="flex-1 w-full max-w-2xl pt-1 pr-4">
                                            <DualRangeSlider
                                                min={minYear}
                                                max={maxYear}
                                                value={[currentStart, currentEnd]}
                                                onChange={() => {}}
                                                onCommit={(val) => {
                                                    if (val[0] === minYear && val[1] === maxYear) {
                                                        onFilterChange(group.key, "全部");
                                                    }
                                                    else if (val[0] === val[1]) {
                                                        onFilterChange(group.key, String(val[0]));
                                                    }
                                                    else {
                                                        onFilterChange(group.key, `${val[0]}-${val[1]}`);
                                                    }
                                                }}
                                            />
                                        </div>
                                    </div>
                                );
                            }

                            const VISIBLE_LIMIT = 12;
                            const options = group.options;
                            const hasMore = options.length > VISIBLE_LIMIT;
                            const visibleOptions = options.slice(0, VISIBLE_LIMIT);
                            const currentVal = getCurrentValue(group.key);

                            return (
                                <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start">
                                    <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider w-16 pt-2 shrink-0">
                                        {group.label}
                                    </span>
                                    <div className="flex flex-wrap gap-2 flex-1">
                                        {visibleOptions.map((opt) => {
                                            const isSelected = currentVal === opt;
                                            return (
                                                <button
                                                    key={opt}
                                                    onClick={() => onFilterChange(group.key, opt)}
                                                    className={cn(
                                                        "px-3 py-1.5 rounded-lg text-xs transition-all border",
                                                        isSelected
                                                            ? "bg-primary border-primary text-primary-foreground shadow-lg shadow-primary/30"
                                                            : "bg-secondary/30 border-border-subtle/5 text-muted-foreground hover:bg-secondary hover:text-foreground hover:border-border-subtle/10"
                                                    )}
                                                >
                                                    {opt}
                                                </button>
                                            );
                                        })}
                                        {hasMore && (
                                            <button
                                                onClick={() => onShowMore(group.key)}
                                                className="px-3 py-1.5 rounded-lg text-xs bg-secondary/30 border border-border-subtle/5 text-primary hover:bg-secondary hover:text-foreground transition-all flex items-center gap-1"
                                            >
                                                更多 <ChevronDown className="w-3 h-3" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}

                        <div className="pt-4 border-t border-border-subtle/10 flex justify-end">
                            <button
                                onClick={onClearAll}
                                className="flex items-center gap-2 text-xs font-bold text-destructive hover:text-destructive/80 transition-colors px-4 py-2 hover:bg-destructive/5 rounded-lg"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                                重置所有条件
                            </button>
                        </div>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    );
}