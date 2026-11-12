import { absoluteUrl, SITE_URL } from "@/lib/seo";
import { FALLBACK_CATEGORIES } from "@/lib/portalCategories";
import { CategoryItem, PageResponse, PortalMetadata } from "@/lib/types";

export const SITEMAP_REVALIDATE_SECONDS = 300;
export const MOVIE_SITEMAP_PAGE_SIZE = 1000;
export const LATEST_SITEMAP_SIZE = 200;

export const SITEMAP_API_BASE = process.env.API_INTERNAL_URL
    || (process.env.BACKEND_URL ? `${process.env.BACKEND_URL}/api/portal` : `${SITE_URL}/api/portal`);

export interface SitemapMovie {
    id: string;
    title: string;
    posterUrl?: string | null;
    lastModified?: string | null;
}

export interface SitemapEntry {
    loc: string;
    lastmod?: string;
    changefreq?: "always" | "hourly" | "daily" | "weekly" | "monthly" | "yearly" | "never";
    priority?: number;
    images?: string[];
}

export interface SitemapIndexEntry {
    loc: string;
    lastmod?: string;
}

export interface MovieSitemapSummary {
    pageCount: number;
    latestModified?: string;
}

export async function getSitemapMetadata(): Promise<PortalMetadata> {
    try {
        return await getSitemapJson<PortalMetadata>("/metadata", SITEMAP_REVALIDATE_SECONDS);
    } catch (error) {
        console.warn("[Sitemap] Falling back to static metadata", error);
        return {
            categories: FALLBACK_CATEGORIES,
            genres: [],
            areas: [],
            languages: [],
            years: []
        };
    }
}

export async function getSitemapMoviePage(page: number, size = MOVIE_SITEMAP_PAGE_SIZE): Promise<PageResponse<SitemapMovie>> {
    const safePage = Math.max(0, page);
    const safeSize = Math.min(Math.max(size, 1), MOVIE_SITEMAP_PAGE_SIZE);
    try {
        return await getSitemapJson<PageResponse<SitemapMovie>>(
            `/sitemap/movies?page=${safePage}&size=${safeSize}`,
            SITEMAP_REVALIDATE_SECONDS
        );
    } catch (error) {
        console.warn(`[Sitemap] Falling back to empty movie page ${safePage}`, error);
        return emptyPage(safePage, safeSize);
    }
}

export async function getMovieSitemapSummary(): Promise<MovieSitemapSummary> {
    try {
        const firstPage = await getSitemapMoviePage(0, 1);
        const total = pageTotalElements(firstPage);
        return {
            pageCount: Math.max(1, Math.ceil(total / MOVIE_SITEMAP_PAGE_SIZE)),
            latestModified: pageContent(firstPage)[0]?.lastModified || undefined
        };
    } catch (error) {
        console.warn("[Sitemap] Falling back to single movie sitemap page", error);
        return { pageCount: 1 };
    }
}

export function staticSitemapEntries(metadata: PortalMetadata): SitemapEntry[] {
    const categories = metadata.categories?.length ? metadata.categories : FALLBACK_CATEGORIES;
    const entries: SitemapEntry[] = [
        { loc: `${SITE_URL}`, changefreq: "daily", priority: 1 },
        { loc: `${SITE_URL}/explore`, changefreq: "daily", priority: 1 },
        { loc: `${SITE_URL}/feedback`, changefreq: "monthly", priority: 0.4 }
    ];

    for (const category of categories) {
        entries.push(categoryEntry(category));
    }
    for (const genre of metadata.genres || []) {
        entries.push({
            loc: `${SITE_URL}/explore?genre=${encodeURIComponent(genre)}`,
            changefreq: "weekly",
            priority: 0.7
        });
    }
    return entries;
}

export function movieSitemapEntries(movies: SitemapMovie[]): SitemapEntry[] {
    return movies
        .filter(movie => movie.id)
        .map(movie => {
            const image = absoluteUrl(movie.posterUrl);
            const lastmod = sitemapDate(movie.lastModified);
            return {
                loc: `${SITE_URL}/movie/${movie.id}`,
                ...(lastmod ? { lastmod } : {}),
                changefreq: "weekly" as const,
                priority: 0.8,
                ...(image ? { images: [image] } : {})
            };
        });
}

export function sitemapIndexXml(entries: SitemapIndexEntry[]): string {
    const body = entries.map(entry => {
        const entryLastmod = sitemapDate(entry.lastmod);
        const lastmod = entryLastmod ? `<lastmod>${escapeXml(entryLastmod)}</lastmod>` : "";
        return `<sitemap><loc>${escapeXml(entry.loc)}</loc>${lastmod}</sitemap>`;
    }).join("");
    return xmlResponse(`<?xml version="1.0" encoding="UTF-8"?>\n<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${body}</sitemapindex>\n`);
}

export function urlSetXml(entries: SitemapEntry[]): string {
    const hasImages = entries.some(entry => entry.images?.length);
    const imageNamespace = hasImages ? ' xmlns:image="http://www.google.com/schemas/sitemap-image/1.1"' : "";
    const body = entries.map(entry => {
        const entryLastmod = sitemapDate(entry.lastmod);
        const lastmod = entryLastmod ? `<lastmod>${escapeXml(entryLastmod)}</lastmod>` : "";
        const changefreq = entry.changefreq ? `<changefreq>${entry.changefreq}</changefreq>` : "";
        const priority = typeof entry.priority === "number" ? `<priority>${entry.priority.toFixed(1)}</priority>` : "";
        const images = (entry.images || [])
            .map(image => `<image:image><image:loc>${escapeXml(image)}</image:loc></image:image>`)
            .join("");
        return `<url><loc>${escapeXml(entry.loc)}</loc>${lastmod}${changefreq}${priority}${images}</url>`;
    }).join("");
    return xmlResponse(`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"${imageNamespace}>${body}</urlset>\n`);
}

export function responseXml(xml: string, maxAge = SITEMAP_REVALIDATE_SECONDS): Response {
    return new Response(xml, {
        headers: {
            "Content-Type": "application/xml; charset=utf-8",
            "Cache-Control": `public, max-age=0, s-maxage=${maxAge}, stale-while-revalidate=${maxAge}, stale-if-error=86400`
        }
    });
}

export function latestRssXml(movies: SitemapMovie[], now = new Date()): string {
    const items = movies.map(movie => {
        const loc = `${SITE_URL}/movie/${movie.id}`;
        const lastmod = sitemapDate(movie.lastModified, now) || now.toISOString();
        return [
            "<item>",
            `<title>${escapeXml(movie.title || "未命名影片")}</title>`,
            `<link>${escapeXml(loc)}</link>`,
            `<guid isPermaLink="true">${escapeXml(loc)}</guid>`,
            `<pubDate>${escapeXml(new Date(lastmod).toUTCString())}</pubDate>`,
            "</item>"
        ].join("");
    }).join("");
    return xmlResponse([
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<rss version="2.0">',
        "<channel>",
        `<title>${escapeXml("画外 Huawai 最新资源")}</title>`,
        `<link>${escapeXml(SITE_URL)}</link>`,
        `<description>${escapeXml("画外 Huawai 最新上架公开影片")}</description>`,
        `<lastBuildDate>${escapeXml(now.toUTCString())}</lastBuildDate>`,
        items,
        "</channel>",
        "</rss>"
    ].join(""));
}

export function pageTotalElements<T>(page: PageResponse<T>): number {
    return page.page?.totalElements ?? page.totalElements ?? page.content?.length ?? 0;
}

export function pageContent<T>(page: PageResponse<T>): T[] {
    return page.content || [];
}

function emptyPage<T>(page: number, size: number): PageResponse<T> {
    return {
        content: [],
        page: {
            number: page,
            size,
            totalElements: 0,
            totalPages: 0,
            first: page === 0,
            last: true,
            numberOfElements: 0,
            empty: true
        },
        totalElements: 0,
        totalPages: 0,
        size,
        number: page,
        first: page === 0,
        last: true,
        empty: true
    };
}

async function getSitemapJson<T>(path: string, revalidate: number): Promise<T> {
    const response = await fetch(`${SITEMAP_API_BASE}${path}`, {
        headers: { "Content-Type": "application/json" },
        next: { revalidate }
    });
    if (!response.ok) {
        throw new Error(`Sitemap fetch failed: ${response.status} ${path}`);
    }
    return response.json();
}

function categoryEntry(category: CategoryItem): SitemapEntry {
    return {
        loc: `${SITE_URL}/explore?type=${encodeURIComponent(category.slug)}`,
        changefreq: "daily",
        priority: 0.9
    };
}

function validDate(value?: string | null): value is string {
    if (!value) {
        return false;
    }
    return !Number.isNaN(Date.parse(value));
}

function sitemapDate(value?: string | null, now = new Date()): string | undefined {
    if (!validDate(value)) {
        return undefined;
    }

    const date = new Date(value);
    if (date.getTime() > now.getTime()) {
        return now.toISOString();
    }
    return date.toISOString();
}

function escapeXml(value: string): string {
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&apos;");
}

function xmlResponse(xml: string): string {
    return xml;
}
