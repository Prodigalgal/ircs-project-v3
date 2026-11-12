import {Metadata} from "next";
import Link from "next/link";
import {contentApi} from "@/lib/api/content";
import {MovieCard} from "@/components/features/MovieCard";
import {SITE_NAME} from "@/lib/seo";

type Props = {
    params: Promise<{ slug: string[] }>;
};

function parseSlug(slugs: string[]) {
    const coreKeyword = decodeURIComponent(slugs[0]);
    const modifiers = slugs.slice(1).map(s => decodeURIComponent(s));
    return {coreKeyword, modifiers};
}

export async function generateMetadata({params}: Props): Promise<Metadata> {
    const {slug} = await params;
    const {coreKeyword, modifiers} = parseSlug(slug);

    const modifierStr = modifiers.length > 0 ? ` ${modifiers.join(" ")}` : "";
    const title = `${coreKeyword}${modifierStr} 相关内容 - ${SITE_NAME}`;
    const hasResults = await hasTopicResults(coreKeyword);

    return {
        title: title,
        description: `在 ${SITE_NAME} 浏览与《${coreKeyword}》相关的影视内容${modifierStr ? `：${modifierStr}` : ""}。`,
        alternates: {
            canonical: `/topic/${slug.map((segment) => encodeURIComponent(segment)).join("/")}`
        },
        robots: {
            index: hasResults,
            follow: true
        }
    };
}

export default async function TopicPage({params}: Props) {
    const {slug} = await params;
    const {coreKeyword, modifiers} = parseSlug(slug);
    const searchData = await contentApi.explore({
        keyword: coreKeyword,
        size: 12
    }).catch(() => ({content: []}));

    const movies = searchData.content || [];
    const hasResults = movies.length > 0;

    let displayMovies = movies;

    if (!hasResults) {
        const trendingData = await contentApi.explore({sort: "trending", size: 12});
        displayMovies = trendingData.content || [];
    }

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6">
            <div className="max-w-7xl mx-auto">
                <header className="mb-12 border-b border-border-subtle/10 pb-8">
                    <div className="flex items-center gap-3 mb-4">
                        <span
                            className="px-3 py-1 bg-primary/10 text-primary border border-primary/20 rounded-full text-xs font-bold uppercase tracking-wider">
                            Topic Collection
                        </span>
                        <h1 className="text-3xl md:text-5xl font-black text-foreground tracking-tight">
                            {coreKeyword} <span
                            className="text-muted-foreground font-serif italic">{modifiers.join(" ")}</span>
                        </h1>
                    </div>

                    <div className="max-w-3xl text-muted-foreground leading-relaxed space-y-4">
                        <p>
                            欢迎访问 <strong>{SITE_NAME}</strong> 的《{coreKeyword}》专属资源页。
                            {hasResults ? (
                                `我们已为您聚合了全网关于“${coreKeyword}”的高清资源，支持${modifiers.includes('下载') ? '高速下载' : '在线播放'}。
                                本站采用先进的 P2P 加速技术，确保您在观看${modifiers.join("、")}版本时流畅无卡顿。`
                            ) : (
                                `虽然暂时未收录完全匹配“${coreKeyword}”的精准资源，但根据您的兴趣，我们为您推荐以下同类高分影视。`
                            )}
                        </p>

                    </div>
                </header>

                {/* 2. 资源列表区 */}
                <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-6">
                    {displayMovies.map((movie, idx) => (
                        <MovieCard key={movie.id} movie={movie} index={idx}/>
                    ))}
                </div>

                {/* 3. 底部内链扩展 (Link Wheel) */}
                <div className="mt-20 p-8 bg-card border border-border-subtle/10 rounded-2xl shadow-sm">
                    <h3 className="text-lg font-bold text-foreground mb-6">更多相关专题</h3>
                    <div className="flex flex-wrap gap-4">
                        {[
                            `${coreKeyword}-1080p`,
                            `${coreKeyword}-蓝光原盘`,
                            `${coreKeyword}-导演剪辑版`,
                            `${coreKeyword}-剧情解析`,
                            `${coreKeyword}-演员表`
                        ].map(tag => (
                            <Link
                                key={tag}
                                href={`/topic/${tag.split('-').join('/')}`}
                                className="text-sm text-muted-foreground hover:text-primary transition-colors border-b border-transparent hover:border-primary/50 pb-0.5"
                            >
                                {tag.replace('-', ' ')}
                            </Link>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}

async function hasTopicResults(coreKeyword: string): Promise<boolean> {
    const searchData = await contentApi.explore({
        keyword: coreKeyword,
        size: 1
    }).catch(() => ({content: []}));
    return (searchData.content || []).length > 0;
}
