import type { MovieCard, TrendSourceStatus } from "@/lib/types";
import { SpotlightSection } from "./bento/SpotlightSection";
import { TrendingSection } from "./bento/TrendingSection";

interface FeaturedBentoProps {
    spotlights: MovieCard[];
    trending: MovieCard[];
    trendSources?: TrendSourceStatus[];
}

export function FeaturedBento({ spotlights, trending, trendSources }: FeaturedBentoProps) {
    return (
        <section className="w-full pt-24 md:pt-32 px-4 md:px-8 pb-6 max-w-[1800px] mx-auto">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:h-[620px]">
                <SpotlightSection movies={spotlights} />
                <TrendingSection movies={trending} sources={trendSources} />
            </div>
        </section>
    );
}
