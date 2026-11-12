"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import type { CategoryItem } from "@/lib/types";
import { CATEGORY_NAV_STYLES, categoryDisplayName, normalizePortalCategories } from "@/lib/portalCategories";
import { usePortalCategories } from "@/hooks/usePortalCategories";

interface CategoryNavProps {
    categories?: CategoryItem[] | null;
}

const DEFAULT_STYLE = CATEGORY_NAV_STYLES.documentary;

export function CategoryNav({ categories }: CategoryNavProps) {
    const hydratedCategories = usePortalCategories(categories);
    const visibleCategories = normalizePortalCategories(hydratedCategories);

    return (
        <section className="px-4 md:px-6 w-full">
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-4">
                {visibleCategories.map((cat, idx) => {
                    const style = CATEGORY_NAV_STYLES[cat.slug] || DEFAULT_STYLE;
                    const Icon = style.icon;
                    return (
                    <Link key={cat.slug} href={`/explore?type=${cat.slug}`} prefetch={false} className="block h-full">
                        <div
                            className={cn(
                                "group relative h-24 md:h-28 rounded-2xl overflow-hidden border transition-all duration-300",
                                "animate-in fade-in slide-in-from-bottom-3 hover:-translate-y-0.5 hover:scale-[1.02] active:scale-[0.98]",
                                "bg-card border-border-subtle/10 hover:bg-card/80",
                                style.hoverBorder,
                                style.glow
                            )}
                            style={{ animationDelay: `${idx * 50}ms` }}
                        >
                            <div className={cn(
                                "absolute inset-0 bg-gradient-to-br opacity-0 group-hover:opacity-100 transition-opacity duration-500",
                                style.color
                            )} />

                            <div className="absolute inset-0 bg-secondary/5 group-hover:bg-transparent transition-colors" />

                            <div className="relative z-10 h-full flex flex-col justify-between p-4">
                                <div className="flex justify-between items-start">
                                    <div className="p-2 rounded-lg bg-secondary text-muted-foreground group-hover:text-foreground group-hover:bg-background/80 transition-colors">
                                        <Icon className="w-5 h-5" />
                                    </div>
                                    <ArrowRight className="w-4 h-4 text-muted-foreground/50 group-hover:text-foreground -translate-x-2 opacity-0 group-hover:translate-x-0 group-hover:opacity-100 transition-all duration-300" />
                                </div>

                                <div>
                                    <h3 className="text-base font-bold text-foreground transition-colors">
                                        {categoryDisplayName(cat.slug)}
                                    </h3>
                                    <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider group-hover:text-foreground/70 transition-colors">
                                        {style.eng}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </Link>
                    );
                })}
            </div>
        </section>
    );
}
