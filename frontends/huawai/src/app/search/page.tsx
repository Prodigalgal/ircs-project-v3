import {Suspense} from "react";
import {Metadata} from "next";
import {contentApi} from "@/lib/api/content";
import {SearchClient} from "@/components/features/search/SearchClient";
import {PortalMetadata} from "@/lib/types";
import {SITE_NAME} from "@/lib/seo";
import {Loader2} from "lucide-react";
import {getTotalElements} from "@/lib/pagination";
import { getCachedPortalMetadata } from "@/lib/server/portalData";

type Props = {
    searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

export async function generateMetadata({searchParams}: Props): Promise<Metadata> {
    const params = await searchParams;
    const keyword = typeof params.keyword === 'string' ? decodeURIComponent(params.keyword) : '';

    if (!keyword) {
        return {
            title: `资源搜索 - ${SITE_NAME}`,
            description: "搜索全网4K HDR电影、剧集、动漫资源。",
            alternates: {
                canonical: "/search"
            },
            robots: {
                index: false,
                follow: true,
            }
        };
    }

    return {
        title: `${keyword} - ${SITE_NAME}搜索`,
        description: `在 ${SITE_NAME} 搜索“${keyword}”相关影视内容。`,
        openGraph: {
            title: `${keyword} - ${SITE_NAME}搜索`,
            description: `发现关于 ${keyword} 的影视内容。`,
        },
        alternates: {
            canonical: "/search",
        },
        robots: {
            index: false,
            follow: true,
            "max-image-preview": "large",
        }
    };
}

export default async function SearchPage({searchParams}: Props) {
    const params = await searchParams;

    // 解析参数
    const keyword = (params.keyword as string) || "";
    const category = (params.type as string) || "all";
    const sort = (params.sort as string) || "latest";
    const genre = (params.genre as string) || "全部";
    const area = (params.area as string) || "全部";
    const year = (params.year as string) || "全部";
    const language = (params.language as string) || "全部";

    // 服务端并行请求：元数据 + 初始搜索结果
    // 这样 HTML 出来就是有内容的，爬虫极其喜欢
    const [metadata, searchData] = await Promise.all([
        getCachedPortalMetadata(),
        contentApi.explore({
            page: 0,
            size: 30, // 首屏多加载一点，增加页面文本量
            category,
            sort,
            keyword,
            genre,
            area,
            year,
            language
        }).catch(() => ({content: [], totalElements: 0}))
    ]);

    const initialResults = searchData.content || [];

    return (
        <div className="min-h-screen bg-[#030305] pt-28 pb-20 px-4 md:px-8 flex flex-col">
            {/*
                将交互逻辑剥离到 Client Component (SearchClient)，
                但初始数据 (initialResults) 由服务端通过 Props 注入。
                这叫 "Hydration"，是 Next.js SEO 的精髓。
            */}
            <Suspense fallback={
                <div className="w-full h-64 flex items-center justify-center">
                    <Loader2 className="w-8 h-8 animate-spin text-neutral-500"/>
                </div>
            }>
                <SearchClient
                    initialResults={initialResults}
                    initialTotal={getTotalElements(searchData)}
                    initialMetadata={metadata as PortalMetadata}
                    initialParams={{category, sort, genre, area, year, language, keyword}}
                />
            </Suspense>

            {/* 仅在有搜索结果且有关键词时展示可点击的相关检索入口。 */}
            {keyword && initialResults.length > 0 && (
                <div className="mt-20 pt-10 border-t border-white/5 max-w-7xl mx-auto w-full">
                    <h3 className="text-sm font-bold text-neutral-600 mb-4 uppercase tracking-widest">
                        &#34;{keyword}&#34; 相关扩展检索
                    </h3>
                    <div className="flex flex-wrap gap-3">
                        {/* 生成 10-20 个长尾词链接 */}
                        {["4K下载", "迅雷资源", "免费在线", "剧情解析", "蓝光原盘", "国语配音"].map(suffix => (
                            <a
                                key={suffix}
                                href={`/topic/${encodeURIComponent(keyword)}-${encodeURIComponent(suffix)}`}
                                className="text-xs text-neutral-500 hover:text-indigo-400 transition-colors"
                            >
                                {keyword}{suffix}
                            </a>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
