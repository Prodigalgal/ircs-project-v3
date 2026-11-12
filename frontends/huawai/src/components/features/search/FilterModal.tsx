"use client";

import { useState, useMemo, useEffect } from "react";
import { X, Search, Check } from "lucide-react";
import { cn } from "@/lib/utils";

interface FilterModalProps {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    options: string[];
    currentValue: string;
    onSelect: (value: string) => void;
    formatLabel?: (value: string) => string;
}

export function FilterModal({
                                isOpen,
                                onClose,
                                title,
                                options,
                                currentValue,
                                onSelect,
                                formatLabel = (s) => s
                            }: FilterModalProps) {
    const [searchQuery, setSearchQuery] = useState("");

    // 当弹窗打开时，重置搜索，并锁定背景滚动
    useEffect(() => {
        if (isOpen) {
            setSearchQuery("");
            document.body.style.overflow = "hidden";
        } else {
            document.body.style.overflow = "unset";
        }
        return () => {
            document.body.style.overflow = "unset";
        };
    }, [isOpen]);

    // 过滤选项
    const filteredOptions = useMemo(() => {
        if (!searchQuery.trim()) return options;
        return options.filter(opt =>
            formatLabel(opt).toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [options, searchQuery, formatLabel]);

    // 键盘 ESC 关闭支持
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape" && isOpen) onClose();
        };
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6">
            <div
                onClick={onClose}
                className="fixed inset-0 bg-black/60 backdrop-blur-sm animate-in fade-in duration-150"
            />

            <div className="relative w-full max-w-4xl bg-card border border-border-subtle/10 rounded-2xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden animate-in fade-in zoom-in-95 slide-in-from-bottom-2 duration-200">
                <div className="flex-none p-5 border-b border-border-subtle/10 space-y-4 bg-card z-10">
                    <div className="flex items-center justify-between">
                        <h3 className="text-xl font-bold text-foreground flex items-center gap-2">
                            选择{title}
                            <span className="text-xs font-normal text-muted-foreground bg-secondary px-2 py-0.5 rounded-full">
                                共 {options.length} 项
                            </span>
                        </h3>
                        <button
                            onClick={onClose}
                            className="p-2 rounded-full hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors"
                        >
                            <X className="w-5 h-5" />
                        </button>
                    </div>

                    <div className="relative">
                        <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                        <input
                            type="text"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder={`搜索${title}...`}
                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl py-3 pl-10 pr-4 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all"
                            autoFocus
                        />
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto p-5 custom-scrollbar bg-card">
                    {filteredOptions.length > 0 ? (
                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
                            {filteredOptions.map((opt) => {
                                const isSelected = currentValue === opt;
                                return (
                                    <button
                                        key={opt}
                                        onClick={() => {
                                            onSelect(opt);
                                            onClose();
                                        }}
                                        className={cn(
                                            "group relative flex items-center justify-between px-4 py-3 rounded-xl text-sm font-medium transition-all text-left",
                                            isSelected
                                                ? "bg-primary text-primary-foreground shadow-lg shadow-primary/30 ring-1 ring-primary"
                                                : "bg-secondary/30 text-muted-foreground hover:bg-secondary hover:text-foreground border border-transparent hover:border-border-subtle/10"
                                        )}
                                    >
                                        <span className="truncate mr-2">{formatLabel(opt)}</span>
                                        {isSelected && <Check className="w-4 h-4 flex-shrink-0" />}
                                    </button>
                                );
                            })}
                        </div>
                    ) : (
                        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                            <Search className="w-10 h-10 mb-4 opacity-20" />
                            <p>未找到 &#34;{searchQuery}&#34;</p>
                        </div>
                    )}
                </div>

                <div className="flex-none p-3 border-t border-border-subtle/10 bg-secondary/10 text-center">
                    <p className="text-[10px] text-muted-foreground">
                        支持滚动查看 • 点击即选中
                    </p>
                </div>
            </div>
        </div>
    );
}
