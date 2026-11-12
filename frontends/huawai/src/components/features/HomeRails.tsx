"use client";

import { startTransition, useEffect, useState } from "react";
import { CategorySection } from "@/lib/types";
import { contentApi } from "@/lib/api/content";
import { compactHomeData } from "@/lib/domain/homeData";
import { MovieRail } from "@/components/features/MovieRail";

interface HomeRailsProps {
    initialSections: CategorySection[];
}

export function HomeRails({ initialSections }: HomeRailsProps) {
    const [sections, setSections] = useState(initialSections);

    useEffect(() => {
        let cancelled = false;

        const hydrateFullRails = () => {
            contentApi.getHomeData()
                .then(homeData => {
                    if (!cancelled && homeData.sections.length > 0) {
                        startTransition(() => {
                            setSections(compactHomeData(homeData).sections);
                        });
                    }
                })
                .catch(() => {
                    // 首屏已有可用数据，补全失败时保持静默降级。
                });
        };

        if ("requestIdleCallback" in window) {
            const idleHandle = window.requestIdleCallback(hydrateFullRails, { timeout: 3000 });
            return () => {
                cancelled = true;
                window.cancelIdleCallback(idleHandle);
            };
        }

        const timeoutHandle = globalThis.setTimeout(hydrateFullRails, 1200);
        return () => {
            cancelled = true;
            globalThis.clearTimeout(timeoutHandle);
        };
    }, []);

    return (
        <div className="space-y-2 md:space-y-6 mt-4">
            {sections.map((category, index) => (
                <div key={category.id} className={index === 0 ? "pt-2" : ""}>
                    <MovieRail category={category} />
                </div>
            ))}
        </div>
    );
}
