import { SITE_URL } from "@/lib/seo";
import {
    getMovieSitemapSummary,
    responseXml,
    sitemapIndexXml,
    SITEMAP_REVALIDATE_SECONDS
} from "@/lib/server/sitemapData";

export const revalidate = 300;
export const dynamic = "force-dynamic";

export async function GET(): Promise<Response> {
    const movieSummary = await getMovieSitemapSummary();
    const entries = [
        { loc: `${SITE_URL}/sitemaps/static.xml` },
        { loc: `${SITE_URL}/sitemaps/latest.xml`, lastmod: movieSummary.latestModified },
        ...Array.from({ length: movieSummary.pageCount }, (_, page) => ({
            loc: `${SITE_URL}/sitemaps/movies/${page}`,
            ...(page === 0 && movieSummary.latestModified ? { lastmod: movieSummary.latestModified } : {})
        }))
    ];
    return responseXml(sitemapIndexXml(entries), SITEMAP_REVALIDATE_SECONDS);
}
