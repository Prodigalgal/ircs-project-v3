import {
    BookOpen,
    Clapperboard,
    Dumbbell,
    FileQuestion,
    FileVideo,
    Ghost,
    Mic2,
    Music2,
    Newspaper,
    ShieldAlert,
    Smartphone,
    Tv,
    type LucideIcon
} from "lucide-react";
import type { CategoryItem } from "@/lib/types";

export const DEFAULT_CATEGORY_SLUG = "all";

export const FALLBACK_CATEGORIES: CategoryItem[] = [
    { slug: "movie", name: "电影" },
    { slug: "series", name: "剧集" },
    { slug: "short-drama", name: "短剧" },
    { slug: "anime", name: "动漫" },
    { slug: "variety", name: "综艺" },
    { slug: "documentary", name: "纪录片" },
    { slug: "sports", name: "体育赛事" },
    { slug: "news", name: "新闻资讯" },
    { slug: "education", name: "教育知识" },
    { slug: "music", name: "音乐演出" },
    { slug: "other", name: "其他" }
];

const CATEGORY_ORDER = [
    "movie",
    "series",
    "short-drama",
    "anime",
    "variety",
    "documentary",
    "sports",
    "news",
    "education",
    "music",
    "adult",
    "other"
];

const CATEGORY_DISPLAY_NAMES: Record<string, string> = {
    movie: "电影",
    series: "剧集",
    "short-drama": "短剧",
    anime: "动漫",
    variety: "综艺",
    documentary: "纪录片",
    sports: "体育赛事",
    news: "新闻资讯",
    education: "教育知识",
    music: "音乐演出",
    adult: "成人",
    other: "其他"
};

export const CATEGORY_NAV_STYLES: Record<string, {
    icon: LucideIcon;
    eng: string;
    color: string;
    hoverBorder: string;
    glow: string;
}> = {
    movie: {
        icon: Clapperboard,
        eng: "Movies",
        color: "from-blue-500/20 to-cyan-500/20",
        hoverBorder: "group-hover:border-blue-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(59,130,246,0.2)]"
    },
    series: {
        icon: Tv,
        eng: "Series",
        color: "from-purple-500/20 to-pink-500/20",
        hoverBorder: "group-hover:border-purple-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(168,85,247,0.2)]"
    },
    "short-drama": {
        icon: Smartphone,
        eng: "Shorts",
        color: "from-amber-500/20 to-orange-500/20",
        hoverBorder: "group-hover:border-amber-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(245,158,11,0.2)]"
    },
    variety: {
        icon: Mic2,
        eng: "Variety",
        color: "from-green-500/20 to-emerald-500/20",
        hoverBorder: "group-hover:border-green-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(34,197,94,0.2)]"
    },
    anime: {
        icon: Ghost,
        eng: "Anime",
        color: "from-rose-500/20 to-red-500/20",
        hoverBorder: "group-hover:border-rose-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(244,63,94,0.2)]"
    },
    documentary: {
        icon: FileVideo,
        eng: "Docs",
        color: "from-teal-500/20 to-sky-500/20",
        hoverBorder: "group-hover:border-teal-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(20,184,166,0.2)]"
    },
    sports: {
        icon: Dumbbell,
        eng: "Sports",
        color: "from-lime-500/20 to-green-500/20",
        hoverBorder: "group-hover:border-lime-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(132,204,22,0.2)]"
    },
    news: {
        icon: Newspaper,
        eng: "News",
        color: "from-sky-500/20 to-blue-500/20",
        hoverBorder: "group-hover:border-sky-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(14,165,233,0.2)]"
    },
    education: {
        icon: BookOpen,
        eng: "Learn",
        color: "from-violet-500/20 to-indigo-500/20",
        hoverBorder: "group-hover:border-violet-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(139,92,246,0.2)]"
    },
    music: {
        icon: Music2,
        eng: "Music",
        color: "from-fuchsia-500/20 to-pink-500/20",
        hoverBorder: "group-hover:border-fuchsia-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(217,70,239,0.2)]"
    },
    adult: {
        icon: ShieldAlert,
        eng: "Adult",
        color: "from-red-500/20 to-rose-500/20",
        hoverBorder: "group-hover:border-red-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(239,68,68,0.2)]"
    },
    other: {
        icon: FileQuestion,
        eng: "Other",
        color: "from-zinc-500/20 to-slate-500/20",
        hoverBorder: "group-hover:border-zinc-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(113,113,122,0.2)]"
    }
};

export function categoryDisplayName(slug: string): string {
    return CATEGORY_DISPLAY_NAMES[slug] || slug;
}

export function normalizePortalCategories(categories?: CategoryItem[] | null): CategoryItem[] {
    const source = categories && categories.length > 0 ? categories : FALLBACK_CATEGORIES;
    const bySlug = new Map<string, CategoryItem>();
    for (const category of source) {
        if (category?.slug) {
            bySlug.set(category.slug, category);
        }
    }

    const ordered: CategoryItem[] = [];
    for (const slug of CATEGORY_ORDER) {
        const category = bySlug.get(slug);
        if (category) {
            ordered.push(category);
            bySlug.delete(slug);
        }
    }
    ordered.push(...bySlug.values());
    return ordered;
}
