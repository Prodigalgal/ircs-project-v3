import {
    getSitemapMetadata,
    responseXml,
    SITEMAP_REVALIDATE_SECONDS,
    staticSitemapEntries,
    urlSetXml
} from "@/lib/server/sitemapData";

export const revalidate = 300;
export const dynamic = "force-dynamic";

export async function GET(): Promise<Response> {
    const metadata = await getSitemapMetadata();
    return responseXml(urlSetXml(staticSitemapEntries(metadata)), SITEMAP_REVALIDATE_SECONDS);
}
