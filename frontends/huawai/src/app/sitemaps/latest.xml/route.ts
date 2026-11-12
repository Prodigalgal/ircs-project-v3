import {
    getSitemapMoviePage,
    LATEST_SITEMAP_SIZE,
    movieSitemapEntries,
    pageContent,
    responseXml,
    SITEMAP_REVALIDATE_SECONDS,
    urlSetXml
} from "@/lib/server/sitemapData";

export const revalidate = 300;
export const dynamic = "force-dynamic";

export async function GET(): Promise<Response> {
    const page = await getSitemapMoviePage(0, LATEST_SITEMAP_SIZE);
    return responseXml(urlSetXml(movieSitemapEntries(pageContent(page))), SITEMAP_REVALIDATE_SECONDS);
}
