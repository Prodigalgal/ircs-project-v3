import { cache } from "react";
import { unstable_cache } from "next/cache";
import { contentApi } from "@/lib/api/content";
import { HomeData } from "@/lib/types";

const SSR_HEADERS = {
    "X-Source-Component": "Huawai-SSR"
};

export const EMPTY_HOME_DATA: HomeData = {
    spotlight: [],
    trending: [],
    sections: []
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
    async () => {
        try {
            return await contentApi.getHomeData(SSR_HEADERS);
        } catch (error) {
            console.warn("[PortalData] Falling back to empty home data during home fetch", error);
            return EMPTY_HOME_DATA;
        }
    },
    ["portal-home-data"],
    { revalidate: 60 }
);

const loadMovieDetail = unstable_cache(
    async (id: string) => {
        return contentApi.getMovieDetail(id, SSR_HEADERS);
    },
    ["portal-movie-detail"],
    { revalidate: 900 }
);

export const getCachedPortalMetadata = cache(loadPortalMetadata);

export const getCachedHomeData = cache(loadPortalHomeData);

export const getMovieDetailForRequest = cache(async (id: string) => {
    return loadMovieDetail(id);
});
