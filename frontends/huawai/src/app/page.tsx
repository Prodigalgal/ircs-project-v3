import { FeaturedBento } from "@/components/features/FeaturedBento";
import { HomeRails } from "@/components/features/HomeRails";
import { CategoryNav } from "@/components/features/CategoryNav";
import { SITE_NAME, BRAND_SLOGAN } from "@/lib/seo";
import { getCachedHomeData, getCachedPortalMetadata } from "@/lib/server/portalData";
import { compactInitialHomeData } from "@/lib/domain/homeData";

export const revalidate = 60;

export default async function Home() {
    const [homeData, metadata] = await Promise.all([
        getCachedHomeData(),
        getCachedPortalMetadata()
    ]);
    const data = compactInitialHomeData(homeData);

    return (
        <div className="flex flex-col min-h-screen bg-background pb-20">
            <h1 className="sr-only">
                {SITE_NAME} - {BRAND_SLOGAN} - 4K HDR 在线流媒体
            </h1>

            <div className="relative z-10">
                <FeaturedBento
                    spotlights={data.spotlight}
                    trending={data.trending}
                />
            </div>

            <div className="flex flex-col gap-8 relative z-20 mt-6 md:mt-10">
                <div className="px-2 max-w-[1600px] mx-auto w-full">
                    <CategoryNav categories={metadata?.categories} />
                </div>

                <HomeRails initialSections={data.sections} />
            </div>

            <div className="h-20 bg-gradient-to-t from-background to-transparent pointer-events-none" />
        </div>
    );
}
