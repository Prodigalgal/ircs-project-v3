import { MetadataRoute } from 'next';
import { SITE_URL } from "@/lib/seo";

export default function robots(): MetadataRoute.Robots {
    return {
        rules: {
            userAgent: '*',
            allow: '/',
            disallow: [
                '/api/',          // API 路由无需索引
                '/my-list/',      // 私人收藏页
                '/history/',      // 私人观看历史
                '/requests/',     // 私人求片记录页
                '/profile/',      // 个人中心
                '/login/',        // 登录页
                '/signup/',       // 注册页
                '/activate/',     // 激活页
                '/forgot-password/',
                '/reset-password/',
                '/oauth/',
                '/watch/',        // 播放页通常含动态参数，建议只索引详情页(/movie/id)，或者谨慎索引
            ],
        },
        sitemap: `${SITE_URL}/sitemap.xml`,
        host: SITE_URL,
    };
}
