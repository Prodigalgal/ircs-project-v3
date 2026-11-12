import { Suspense } from "react";
import { contentApi } from "@/lib/api/content";
import ExploreClient from "./ExploreClient";
import { Loader2 } from "lucide-react";
import { MovieCard, PortalMetadata } from "@/lib/types";
import { Metadata } from "next";
import { getTotalElements } from "@/lib/pagination";
import { categoryDisplayName } from "@/lib/portalCategories";
import { getCachedPortalMetadata } from "@/lib/server/portalData";
import { SITE_NAME } from "@/lib/seo";

type SearchParams = Promise<{ [key: string]: string | string[] | undefined }>;

type Props = {
    searchParams: SearchParams;
};

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
    const params = await searchParams;

    const keyword = typeof params.keyword === 'string' ? params.keyword : '';
    const category = typeof params.type === 'string' && params.type !== 'all' ? params.type : '';
    const genre = typeof params.genre === 'string' && params.genre !== '全部' ? params.genre : '';
    const year = typeof params.year === 'string' && params.year !== '全部' ? params.year : '';
    const area = typeof params.area === 'string' && params.area !== '全部' ? params.area : '';

    const titleParts = [];
    if (keyword) titleParts.push(`"${keyword}"的搜索结果`);
    if (year) titleParts.push(`${year}年`);
    if (area) titleParts.push(area);
    if (genre) titleParts.push(genre);
    if (category) titleParts.push(categoryDisplayName(category));
    if (titleParts.length === 0) titleParts.push("探索");

    const title = titleParts.join(" ");
    const canonical = exploreCanonical({
        category,
        genre,
        year,
        area,
        keyword,
    });

    return {
        title: title,
        description: `在画外探索${titleParts.join("、")}。提供海量4K、HDR影视资源，沉浸式观影体验。`,
        openGraph: {
            title: `${title} - ${SITE_NAME}`,
            description: `发现更多 ${title} 相关的精彩内容。`
        },
        alternates: {
            canonical,
        },
        robots: {
            index: !keyword,
            follow: true,
        }
    };
}

export default async function ExplorePage(props: { searchParams: SearchParams }) {
    const searchParams = await props.searchParams;

    const category = (searchParams.type as string) || "all";
    const sort = (searchParams.sort as string) || "latest";
    const genre = (searchParams.genre as string) || "全部";
    const area = (searchParams.area as string) || "全部";
    const year = (searchParams.year as string) || "全部";
    const language = (searchParams.language as string) || "全部";
    const keyword = (searchParams.keyword as string) || "";

    const [metadata, searchData] = await Promise.all([
        getCachedPortalMetadata(),
        contentApi.explore({
            page: 0,
            size: 35,
            category,
            sort,
            keyword,
            genre,
            area,
            year,
            language
        }).catch(() => ({ content: [], totalElements: 0 }))
    ]);

    return (
        <div className="min-h-screen bg-background pt-28 pb-20 px-4 md:px-8 flex flex-col">
            <Suspense fallback={
                <div className="min-h-screen bg-background pt-32 flex justify-center">
                    <Loader2 className="w-10 h-10 animate-spin text-muted-foreground" />
                </div>
            }>
                <ExploreClient
                    initialResults={(searchData.content || []).map(compactExploreMovie)}
                    initialTotal={getTotalElements(searchData)}
                    initialMetadata={metadata as PortalMetadata}
                    initialParams={{ category, sort, genre, area, year, language, keyword }}
                />
            </Suspense>
        </div>
    );
}

function compactExploreMovie(movie: MovieCard): MovieCard {
    return {
        id: movie.id,
        title: movie.title,
        aliasTitle: movie.aliasTitle,
        posterUrl: movie.posterUrl,
        rating: movie.rating,
        releaseYear: movie.releaseYear,
        categoryName: movie.categoryName,
        area: movie.area,
        remarks: movie.remarks,
        season: movie.season,
        subtitle: movie.subtitle
    };
}

function exploreCanonical(params: {
    category?: string;
    genre?: string;
    year?: string;
    area?: string;
    keyword?: string;
}): string {
    if (params.keyword) {
        return "/explore";
    }
    const searchParams = new URLSearchParams();
    if (params.category) {
        searchParams.set("type", params.category);
    } else if (params.genre) {
        searchParams.set("genre", params.genre);
    } else if (params.year) {
        searchParams.set("year", params.year);
    } else if (params.area) {
        searchParams.set("area", params.area);
    }
    const query = searchParams.toString();
    return query ? `/explore?${query}` : "/explore";
}
