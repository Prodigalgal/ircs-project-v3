import { MovieDetail } from "@/lib/types";

export const SITE_URL = normalizeSiteUrl(process.env.NEXT_PUBLIC_SITE_URL || "https://huawai.mnnu.eu.org");
export const SITE_NAME = "画外 Huawai";
export const BRAND_SLOGAN = "画里是故事，画外是生活";

function normalizeSiteUrl(value: string): string {
    return value.replace(/\/+$/, "");
}

export function absoluteUrl(value?: string | null): string | undefined {
    if (!value) {
        return undefined;
    }
    if (/^https?:\/\//i.test(value)) {
        return value;
    }
    return `${SITE_URL}${value.startsWith("/") ? value : `/${value}`}`;
}

export function cleanSeoText(value?: string | null, maxLength = 160): string {
    if (!value) {
        return "";
    }
    return value
        .replace(/<[^>]*>/g, " ")
        .replace(/[\r\n\t]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .slice(0, maxLength);
}

function validYearDate(year?: string): string | undefined {
    if (!year || !/^\d{4}$/.test(year)) {
        return undefined;
    }
    return `${year}-01-01`;
}

export function generateBrandSchema() {
    return {
        "@context": "https://schema.org",
        "@type": "Organization",
        "name": SITE_NAME,
        "url": SITE_URL,
        "logo": `${SITE_URL}/window.svg`,
        "alternateName": ["Huawai", "画外", "画外影视", "Huawai Streaming"],
        "description": "画外 Huawai 是一个专注于高保真 4K HDR 内容的垂直流媒体平台，提供沉浸式观影体验。",
    };
}

export function generateBreadcrumbSchema(items: { name: string; url: string }[]) {
    return {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": items.map((item, index) => ({
            "@type": "ListItem",
            "position": index + 1,
            "name": item.name,
            "item": item.url.startsWith("http") ? item.url : `${SITE_URL}${item.url}`
        }))
    };
}

export function generateMovieSchema(movie: MovieDetail) {
    const isSeries = movie.categoryName?.includes("剧") || movie.categoryName === "动漫";
    const dateModified = movie.lastTrendAt || undefined;
    const image = absoluteUrl(movie.posterUrl);
    const cast = (movie.cast || []).map((member) => ({
        "@type": "Person",
        "name": member.name,
        "jobTitle": member.role
    }));
    const actors = cast.filter((member) => member.jobTitle === "演员");
    const directors = cast.filter((member) => member.jobTitle === "导演");
    const description = cleanSeoText(movie.description, 300);

    return {
        "@context": "https://schema.org",
        "@type": isSeries ? "TVSeries" : "Movie",
        "name": movie.title,
        "alternateName": [movie.aliasTitle, movie.subtitle].filter(Boolean),
        "url": `${SITE_URL}/movie/${movie.id}`,
        ...(image ? { "image": image } : {}),
        ...(description ? { "description": description } : {}),
        ...(validYearDate(movie.releaseYear) ? { "datePublished": validYearDate(movie.releaseYear) } : {}),
        "dateModified": dateModified,
        "inLanguage": "zh-CN",
        "countryOfOrigin": movie.area,
        "provider": {
            "@type": "Organization",
            "name": SITE_NAME,
            "sameAs": SITE_URL
        },
        ...(actors.length > 0 ? { "actor": actors } : {}),
        ...(directors.length > 0 ? { "director": directors } : {}),
        "genre": movie.genres || []
    };
}

export function generateWebsiteSchema() {
    return {
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": SITE_NAME,
        "alternateName": "Huawai Streaming",
        "url": SITE_URL,
        "potentialAction": {
            "@type": "SearchAction",
            "target": {
                "@type": "EntryPoint",
                "urlTemplate": `${SITE_URL}/search?keyword={search_term_string}`
            },
            "query-input": "required name=search_term_string"
        }
    };
}
