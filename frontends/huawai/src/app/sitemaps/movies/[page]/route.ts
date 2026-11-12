import {
    getSitemapMoviePage,
    movieSitemapEntries,
    MOVIE_SITEMAP_PAGE_SIZE,
    pageContent,
    responseXml,
    SITEMAP_REVALIDATE_SECONDS,
    urlSetXml
} from "@/lib/server/sitemapData";

export const revalidate = 300;
export const dynamic = "force-dynamic";

export async function GET(request: Request): Promise<Response> {
    const page = pageFromPath(request.url);
    const movies = await getSitemapMoviePage(page, MOVIE_SITEMAP_PAGE_SIZE);
    return responseXml(urlSetXml(movieSitemapEntries(pageContent(movies))), SITEMAP_REVALIDATE_SECONDS);
}

function pageFromPath(url: string): number {
    const segment = new URL(url).pathname.split("/").filter(Boolean).at(-1);
    const page = segment ? Number.parseInt(segment, 10) : 0;
    return Number.isFinite(page) && page >= 0 ? page : 0;
}
