// src/app/layout.tsx

import type { Metadata } from "next";
import "./globals.css";
import { Navbar } from "@/components/layout/Navbar";
import { OptionalFontLoader } from "@/components/layout/OptionalFontLoader";
import { ToastProvider } from "@/lib/context/ToastContext";
import { AuthProvider } from "@/lib/context/AuthContext";
import { ThemeProvider } from "@/lib/context/ThemeContext";
import { Toaster } from "@/components/ui/Toaster";
import { RouteGuard } from "@/components/layout/RouteGuard";
import { CinematicGrain } from "@/components/ui/CinematicGrain";
import { LazyCommandMenu } from "@/components/features/LazyCommandMenu";
import React from "react";
import { generateWebsiteSchema, generateBrandSchema, SITE_URL, SITE_NAME, BRAND_SLOGAN } from "@/lib/seo";
import { cn } from "@/lib/utils";
import { getCachedPortalMetadata } from "@/lib/server/portalData";

const defaultOgImage = `/api/og?title=${encodeURIComponent(SITE_NAME)}&rating=&year=`;

export const metadata: Metadata = {
    metadataBase: new URL(SITE_URL),
    title: {
        default: `${SITE_NAME} - 4K HDR 沉浸式影视平台`,
        template: `%s | ${SITE_NAME}`
    },
    description: `${BRAND_SLOGAN}。画外 Huawai 是新一代高保真流媒体平台，拒绝算法噪音，专注提供 4K HDR、杜比音效的电影、剧集与动漫。在这里，找回纯粹的观影感动。`,
    icons: {
        icon: "/window.svg",
        shortcut: "/window.svg",
        apple: "/window.svg",
    },
    verification: {
        google: "BK8DhjzL0bOviLoKorgTUCJJQDZk0q6cBBGrS8Knbqc"
    },
    openGraph: {
        type: "website",
        locale: "zh_CN",
        url: SITE_URL,
        siteName: SITE_NAME,
        title: `${SITE_NAME} - ${BRAND_SLOGAN}`,
        description: "探索电影、剧集、动漫的画外之音。4K HDR 极速直连。",
        images: [
            {
                url: defaultOgImage,
                width: 1200,
                height: 630,
                alt: `${SITE_NAME} 品牌封面`
            }
        ]
    },
    twitter: {
        card: "summary_large_image",
        title: SITE_NAME,
        description: BRAND_SLOGAN,
        images: [defaultOgImage],
    },
    alternates: {
        canonical: "/",
        types: {
            "application/rss+xml": `${SITE_URL}/feed.xml`
        }
    }
};

export default async function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    const websiteSchema = generateWebsiteSchema();
    const brandSchema = generateBrandSchema();
    const portalMetadata = await getCachedPortalMetadata();

    return (
        <html lang="zh-CN" className="dark" suppressHydrationWarning>
        <body
            className="antialiased bg-background text-foreground font-sans overflow-x-hidden"
        >
        <script
            type="application/ld+json"
            dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteSchema) }}
        />
        <script
            type="application/ld+json"
            dangerouslySetInnerHTML={{ __html: JSON.stringify(brandSchema) }}
        />

        <CinematicGrain />
        <OptionalFontLoader />

        <ThemeProvider>
            <ToastProvider>
                <AuthProvider>
                    <RouteGuard>
                        <div className={cn(
                            "min-h-screen flex flex-col max-w-[1600px] mx-auto",
                            "bg-background shadow-2xl my-0 lg:my-4 lg:rounded-[32px] overflow-hidden border border-border-subtle/10 relative"
                        )}>

                            <React.Suspense fallback={null}>
                                <Navbar categories={portalMetadata?.categories} />
                            </React.Suspense>

                            <main className="flex-1 flex flex-col relative z-0">
                                <React.Suspense fallback={null}>
                                    {children}
                                </React.Suspense>
                            </main>

                            <footer className="border-t border-border-subtle/10 py-12 text-center bg-card/30 relative z-10">
                                <div className="flex items-center justify-center gap-2 mb-4 opacity-50">
                                    <span className="text-xs bg-secondary px-2 py-1 rounded text-muted-foreground">CMD+K</span>
                                    <span className="text-xs text-muted-foreground">打开指令菜单</span>
                                </div>
                                <div className="flex flex-col items-center gap-1">
                                    <p className="text-sm text-muted-foreground font-bold tracking-wide">
                                        &copy; 2025 {SITE_NAME} Inc.
                                    </p>
                                    <p className="text-xs text-muted-foreground/80 font-serif italic">
                                        &#34;{BRAND_SLOGAN}&#34;
                                    </p>
                                </div>
                                <p className="text-[10px] text-muted-foreground/60 mt-4 font-mono uppercase tracking-widest">
                                    DESIGNED FOR IMMERSION · NOT HUAWEI
                                </p>
                            </footer>
                        </div>
                    </RouteGuard>

                    <Toaster />
                    <LazyCommandMenu />
                </AuthProvider>
            </ToastProvider>
        </ThemeProvider>
        </body>
        </html>
    );
}
