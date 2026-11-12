import {
    getSitemapMoviePage,
    latestRssXml,
    LATEST_SITEMAP_SIZE,
    pageContent,
    responseXml,
    SITEMAP_REVALIDATE_SECONDS
} from "@/lib/server/sitemapData";

export const revalidate = 300;
export const dynamic = "force-dynamic";

export async function GET(): Promise<Response> {
    const page = await getSitemapMoviePage(0, LATEST_SITEMAP_SIZE);
    return responseXml(latestRssXml(pageContent(page)), SITEMAP_REVALIDATE_SECONDS);
}
