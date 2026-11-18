import { cache } from "react";
import { unstable_cache } from "next/cache";
import { contentApi } from "@/lib/api/content";

const SSR_HEADERS = {
    "X-Source-Component": "Huawai-SSR"
};

const loadPortalMetadata = unstable_cache(
    async () => {
        try {
            return await contentApi.getMetadata(SSR_HEADERS);
        } catch (error) {
            console.warn("[PortalData] Falling back to static categories during metadata fetch", error);
            return null;
        }
    },
    ["portal-metadata"],
    { revalidate: 300 }
);

const loadPortalHomeData = unstable_cache(
    async () => contentApi.getHomeData(SSR_HEADERS),
    ["portal-home-data"],
    { revalidate: 60 }
);

export const getCachedPortalMetadata = cache(loadPortalMetadata);

export const getCachedHomeData = cache(loadPortalHomeData);

// The detail fetch already revalidates successful responses. Keep missing results scoped to this render.
export const getMovieDetailForRequest = cache(async (id: string) => {
    return contentApi.getMovieDetail(id, SSR_HEADERS);
});
