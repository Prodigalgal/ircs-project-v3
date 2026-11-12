import React from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Metadata } from "next";
import { Clapperboard, Hash } from "lucide-react";

// Components
import { CastList } from "@/components/features/CastList";
import { ResourceSelector } from "@/components/features/ResourceSelector";
import { MovieHero } from "@/components/features/movie/MovieHero";
import { MovieStructuredData } from "@/components/features/movie/MovieStructuredData";
import { RelatedMoviesRail } from "@/components/features/movie/RelatedMoviesRail";

// Libs & APIs
import { getMovieDetailForRequest } from "@/lib/server/portalData";
import { MovieDetail } from "@/lib/types";
import { cleanSeoText, SITE_NAME, SITE_URL } from "@/lib/seo";

type Props = {
    params: Promise<{ id: string }>;
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidMovieId(id: string) {
    return UUID_PATTERN.test(id);
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
    const { id } = await params;
    if (!isValidMovieId(id)) {
        return {
            title: "页面未找到 - 画外 Huawai",
            robots: "noindex"
        };
    }

    try {
        const movie = await getMovieDetailForRequest(id);
        if (!movie) return { title: "页面未找到 - 画外 Huawai" };

        const hasMagnet = movie.magnets && movie.magnets.length > 0;
        const hasCloud = movie.cloudLinks && movie.cloudLinks.length > 0;
        const hasOnline = movie.sources && movie.sources.length > 0;
        const isSeries = movie.categoryName === '剧集' || movie.categoryName === '动漫';

        const availabilityLabels: string[] = [];

        if (hasOnline) availabilityLabels.push("在线播放");
        if (hasCloud) availabilityLabels.push("网盘资源");
        if (hasMagnet) availabilityLabels.push("磁力下载");

        let statusText = "";
        if (isSeries) {
            statusText = movie.remarks ? ` ${movie.remarks}` : " 全集";
        } else {
            statusText = " 完整版";
        }

        const titleSuffix = availabilityLabels.length > 0 ? ` - ${availabilityLabels.join("/")}` : "";
        const title = `${movie.title}${statusText}${titleSuffix} - ${SITE_NAME}`;

        const castNames = movie.cast?.slice(0, 4).map(c => c.name).join("/");
        const cleanDesc = cleanSeoText(movie.description, 120) || `${movie.title}剧情介绍`;
        const mediaType = isSeries ? "剧集" : "影片";
        const castText = castNames ? `由 ${castNames} 等参与，` : "";
        const availabilityText = availabilityLabels.length > 0
            ? `当前收录${availabilityLabels.join("、")}。`
            : "";
        const description = `${SITE_NAME} 收录《${movie.title}》${statusText.trim()}。${mediaType}${castText}剧情简介：${cleanDesc}。${availabilityText}`;

        const ogParams = new URLSearchParams();
        ogParams.set('title', movie.title);
        ogParams.set('rating', movie.rating?.toString() || '');
        ogParams.set('year', movie.releaseYear);
        if (movie.posterUrl) ogParams.set('poster', movie.posterUrl);
        const ogImageUrl = `${SITE_URL}/api/og?${ogParams.toString()}`;

        return {
            title: title,
            description: description,
            openGraph: {
                title: title,
                description: description,
                type: 'video.movie',
                url: `${SITE_URL}/movie/${id}`,
                images: [
                    {
                        url: ogImageUrl,
                        width: 1200,
                        height: 630,
                        alt: `${movie.title} 高清海报`
                    }
                ],
                siteName: SITE_NAME,
                locale: "zh_CN",
            },
            twitter: {
                card: "summary_large_image",
                title: title,
                description: description,
                images: [ogImageUrl],
            },
            alternates: {
                canonical: `${SITE_URL}/movie/${id}`,
            },
            other: {
                "applicable-device": "pc,mobile",
                "renderer": "webkit"
            }
        };
    } catch (e) {
        console.error("Metadata generation error:", e);
        return {
            title: `${SITE_NAME} - 4K HDR 在线流媒体`,
            robots: "noindex"
        };
    }
}

export default async function MovieDetailPage({ params }: Props) {
    const { id } = await params;
    if (!isValidMovieId(id)) {
        return notFound();
    }

    let movie: MovieDetail | null = null;
    try {
        movie = await getMovieDetailForRequest(id);
    } catch (e) {
        console.error("Movie fetch error:", e);
        return notFound();
    }

    if (!movie) return notFound();

    return (
        <div className="min-h-screen bg-background pb-32 relative overflow-hidden">
            <MovieStructuredData movie={movie} />

            <MovieHero movie={movie} />

            <div className="max-w-7xl mx-auto px-6 md:px-12 mt-24 mb-16 relative z-10">
                <div className="flex items-center gap-3 mb-8">
                    <div className="w-1.5 h-6 bg-primary rounded-full shadow-[0_0_15px_rgba(99,102,241,0.5)]" />
                    <h2 className="text-2xl font-bold text-foreground tracking-wide">主演阵容</h2>
                </div>
                <CastList cast={movie.cast} />
            </div>

            <div className="relative z-10 bg-card/40 backdrop-blur-sm pt-16 pb-24 border-t border-border-subtle/10 mt-12 mb-12">
                <div className="max-w-7xl mx-auto px-6 md:px-12">
                    <div className="flex items-center gap-3 mb-10">
                        <div className="p-2 rounded-xl bg-primary/10 text-primary">
                            <Clapperboard className="w-6 h-6" />
                        </div>
                        <h2 className="text-2xl font-bold text-foreground">
                            {movie.title} 在线观看 & 下载
                        </h2>
                    </div>
                    <ResourceSelector movie={movie} />
                </div>
            </div>

            <RelatedMoviesRail
                movieId={movie.id}
                movieTitle={movie.title}
                genre={movie.genres?.[0] || ""}
                category={movie.categoryName === "剧集" ? "series" : "movie"}
            />

            <div className="max-w-7xl mx-auto px-6 md:px-12 mb-20 relative z-10">
                <div className="flex items-center gap-2 mb-6 opacity-70">
                    <Hash className="w-4 h-4 text-muted-foreground" />
                    <h3 className="text-sm font-bold text-muted-foreground uppercase tracking-widest">
                        {movie.title} 更多相关检索
                    </h3>
                </div>
                <div className="flex flex-wrap gap-3">
                    <TagLink href={`/explore?year=${movie.releaseYear}`} label={`${movie.releaseYear}年高分影视`} />
                    <TagLink href={`/explore?area=${movie.area}`} label={`${movie.area}热门电影`} />
                    {movie.genres?.map(genre => (
                        <TagLink key={genre} href={`/explore?genre=${encodeURIComponent(genre)}`} label={`${genre}片下载`} />
                    ))}
                    <TagLink href={`/explore?type=${movie.categoryName === '剧集' ? 'series' : 'movie'}`} label={`${movie.categoryName}排行榜`} />
                    <TagLink href="/explore?sort=rating" label="豆瓣高分榜" />
                    <TagLink href="/explore?sort=trending" label="最近热播" />
                </div>
            </div>
        </div>
    );
}

function TagLink({ href, label }: { href: string; label: string }) {
    return (
        <Link
            href={href}
            title={label}
            className="px-4 py-2 rounded-full bg-secondary/50 border border-border-subtle/10 text-xs text-muted-foreground hover:text-foreground hover:bg-secondary hover:border-border-subtle/20 transition-colors"
        >
            {label}
        </Link>
    );
}
