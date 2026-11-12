This file is a merged representation of the entire codebase, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of the entire repository's contents.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
.gitea/workflows/ci.yaml
.gitignore
Dockerfile
eslint.config.mjs
next.config.ts
package.json
postcss.config.mjs
public/file.svg
public/globe.svg
public/logo.svg
public/vercel.svg
public/window.svg
repo.bat
src/app/activate/page.tsx
src/app/api/og/route.tsx
src/app/error.tsx
src/app/explore/ExploreClient.tsx
src/app/explore/page.tsx
src/app/feedback/page.tsx
src/app/forgot-password/page.tsx
src/app/globals.css
src/app/history/page.tsx
src/app/layout.tsx
src/app/loading.tsx
src/app/login/page.tsx
src/app/movie/[id]/page.tsx
src/app/my-list/page.tsx
src/app/not-found.tsx
src/app/page.tsx
src/app/profile/page.tsx
src/app/reset-password/page.tsx
src/app/robots.ts
src/app/search/page.tsx
src/app/signup/page.tsx
src/app/sitemap.ts
src/app/template.tsx
src/app/topic/[...slug]/page.tsx
src/app/watch/[id]/page.tsx
src/components/features/bento/SpotlightCard.tsx
src/components/features/bento/SpotlightSection.tsx
src/components/features/bento/TrendingItem.tsx
src/components/features/bento/TrendingSection.tsx
src/components/features/CastList.tsx
src/components/features/CategoryNav.tsx
src/components/features/CommandMenu.tsx
src/components/features/explore/ExploreControls.tsx
src/components/features/explore/ExploreFilterPanel.tsx
src/components/features/explore/ExploreGrid.tsx
src/components/features/explore/ExploreHeader.tsx
src/components/features/FeaturedBento.tsx
src/components/features/history/HistoryItem.tsx
src/components/features/movie/MovieHero.tsx
src/components/features/movie/MovieInfoComponents.tsx
src/components/features/movie/MovieStructuredData.tsx
src/components/features/MovieActions.tsx
src/components/features/MovieCard.tsx
src/components/features/MovieRail.tsx
src/components/features/player/IframePlayer.tsx
src/components/features/player/VidstackPlayer.tsx
src/components/features/profile/IdentityCard.tsx
src/components/features/profile/PasswordModal.tsx
src/components/features/profile/StatCard.tsx
src/components/features/resource/CloudPanel.tsx
src/components/features/resource/DownloadPanel.tsx
src/components/features/resource/EpisodeGrid.tsx
src/components/features/resource/OnlinePanel.tsx
src/components/features/resource/SourceRail.tsx
src/components/features/resource/types.ts
src/components/features/resource/utils.ts
src/components/features/ResourceSelector.tsx
src/components/features/search/FilterModal.tsx
src/components/features/search/SearchClient.tsx
src/components/features/VideoPlayer.tsx
src/components/features/WatchSidebar.tsx
src/components/layout/Navbar.tsx
src/components/layout/navbar/MobileMenu.tsx
src/components/layout/RouteGuard.tsx
src/components/ui/CinematicGrain.tsx
src/components/ui/DualRangeSlider.tsx
src/components/ui/GlowButton.tsx
src/components/ui/QuantumImage.tsx
src/components/ui/Toaster.tsx
src/hooks/useAutoScroll.ts
src/hooks/useCarousel.ts
src/hooks/useContentSearch.ts
src/hooks/useDebounce.ts
src/hooks/useMediaInteraction.ts
src/hooks/usePoW.ts
src/hooks/useVideoHeartbeat.ts
src/lib/api/auth.ts
src/lib/api/content.ts
src/lib/api/interaction.ts
src/lib/context/AuthContext.tsx
src/lib/context/ThemeContext.tsx
src/lib/context/ToastContext.tsx
src/lib/domain/video.ts
src/lib/seo.ts
src/lib/types/index.ts
src/lib/utils.ts
src/proxy.ts
tsconfig.json
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path=".gitignore">
# See https://help.github.com/articles/ignoring-files/ for more about ignoring files.

# dependencies
/node_modules
/.pnp
.pnp.*
.yarn/*
!.yarn/patches
!.yarn/plugins
!.yarn/releases
!.yarn/versions

# testing
/coverage

# next.js
/.next/
/out/

# production
/build

# misc
.DS_Store
*.pem

# debug
npm-debug.log*
yarn-debug.log*
yarn-error.log*
.pnpm-debug.log*

# env files (can opt-in for committing if needed)
.env*

# vercel
.vercel

# typescript
*.tsbuildinfo
next-env.d.ts
</file>

<file path="eslint.config.mjs">
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
</file>

<file path="postcss.config.mjs">
const config = {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};

export default config;
</file>

<file path="public/file.svg">
<svg fill="none" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg"><path d="M14.5 13.5V5.41a1 1 0 0 0-.3-.7L9.8.29A1 1 0 0 0 9.08 0H1.5v13.5A2.5 2.5 0 0 0 4 16h8a2.5 2.5 0 0 0 2.5-2.5m-1.5 0v-7H8v-5H3v12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1M9.5 5V2.12L12.38 5zM5.13 5h-.62v1.25h2.12V5zm-.62 3h7.12v1.25H4.5zm.62 3h-.62v1.25h7.12V11z" clip-rule="evenodd" fill="#666" fill-rule="evenodd"/></svg>
</file>

<file path="public/globe.svg">
<svg fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><g clip-path="url(#a)"><path fill-rule="evenodd" clip-rule="evenodd" d="M10.27 14.1a6.5 6.5 0 0 0 3.67-3.45q-1.24.21-2.7.34-.31 1.83-.97 3.1M8 16A8 8 0 1 0 8 0a8 8 0 0 0 0 16m.48-1.52a7 7 0 0 1-.96 0H7.5a4 4 0 0 1-.84-1.32q-.38-.89-.63-2.08a40 40 0 0 0 3.92 0q-.25 1.2-.63 2.08a4 4 0 0 1-.84 1.31zm2.94-4.76q1.66-.15 2.95-.43a7 7 0 0 0 0-2.58q-1.3-.27-2.95-.43a18 18 0 0 1 0 3.44m-1.27-3.54a17 17 0 0 1 0 3.64 39 39 0 0 1-4.3 0 17 17 0 0 1 0-3.64 39 39 0 0 1 4.3 0m1.1-1.17q1.45.13 2.69.34a6.5 6.5 0 0 0-3.67-3.44q.65 1.26.98 3.1M8.48 1.5l.01.02q.41.37.84 1.31.38.89.63 2.08a40 40 0 0 0-3.92 0q.25-1.2.63-2.08a4 4 0 0 1 .85-1.32 7 7 0 0 1 .96 0m-2.75.4a6.5 6.5 0 0 0-3.67 3.44 29 29 0 0 1 2.7-.34q.31-1.83.97-3.1M4.58 6.28q-1.66.16-2.95.43a7 7 0 0 0 0 2.58q1.3.27 2.95.43a18 18 0 0 1 0-3.44m.17 4.71q-1.45-.12-2.69-.34a6.5 6.5 0 0 0 3.67 3.44q-.65-1.27-.98-3.1" fill="#666"/></g><defs><clipPath id="a"><path fill="#fff" d="M0 0h16v16H0z"/></clipPath></defs></svg>
</file>

<file path="repo.bat">
@echo off
echo 正在执行 Repomix...
repomix -o huawai.md
echo.
echo 执行完成！按任意键关闭窗口...
pause
</file>

<file path="src/hooks/useAutoScroll.ts">
"use client";

import { useRef, useEffect, useCallback } from "react";

interface UseAutoScrollProps {
    itemCount: number;
    enabled?: boolean;
}

export function useAutoScroll({ itemCount, enabled = true }: UseAutoScrollProps) {
    const scrollRef = useRef<HTMLDivElement>(null);

    // 初始化位置
    useEffect(() => {
        const container = scrollRef.current;
        if (container && enabled && itemCount >= 3) {
            const setHeight = container.scrollHeight / 3;
            if (container.scrollTop < 50) container.scrollTop = setHeight;
        }
    }, [itemCount, enabled]);

    // 滚动监听与重置
    const handleScroll = useCallback(() => {
        const container = scrollRef.current;
        if (!container || !enabled || itemCount < 3) return;

        const scrollTop = container.scrollTop;
        const scrollHeight = container.scrollHeight;
        const setHeight = scrollHeight / 3;
        const buffer = 50;

        if (scrollTop < buffer) {
            container.scrollTop = scrollTop + setHeight;
        } else if (scrollTop >= setHeight * 2 - buffer) {
            container.scrollTop = scrollTop - setHeight;
        }
    }, [itemCount, enabled]);

    return { scrollRef, handleScroll };
}
</file>

<file path="src/lib/context/ToastContext.tsx">
"use client";

import React, { createContext, useContext, useState, useCallback, ReactNode } from "react";
import { nanoid } from "nanoid"; // 如果没有 nanoid，可以使用简单的 Math.random 生成

// 定义通知类型
export type ToastType = "success" | "error" | "info" | "loading";

export interface Toast {
    id: string;
    message: string;
    type: ToastType;
    duration?: number;
}

interface ToastContextType {
    addToast: (message: string, type?: ToastType, duration?: number) => void;
    removeToast: (id: string) => void;
    toasts: Toast[];
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);

    const removeToast = useCallback((id: string) => {
        setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, []);

    const addToast = useCallback((message: string, type: ToastType = "info", duration = 3000) => {
        const id = Math.random().toString(36).substring(2, 9); // 简易 ID 生成
        const newToast = { id, message, type, duration };

        setToasts((prev) => [...prev, newToast]);

        if (duration > 0) {
            setTimeout(() => {
                removeToast(id);
            }, duration);
        }
    }, [removeToast]);

    return (
        <ToastContext.Provider value={{ addToast, removeToast, toasts }}>
            {children}
        </ToastContext.Provider>
    );
}

// Custom Hook
export function useToast() {
    const context = useContext(ToastContext);
    if (context === undefined) {
        throw new Error("useToast must be used within a ToastProvider");
    }
    return context;
}
</file>

<file path="tsconfig.json">
{
  "compilerOptions": {
    "target": "ES2017",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "react-jsx",
    "incremental": true,
    "plugins": [
      {
        "name": "next"
      }
    ],
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": [
    "next-env.d.ts",
    "**/*.ts",
    "**/*.tsx",
    ".next/types/**/*.ts",
    ".next/dev/types/**/*.ts",
    "**/*.mts"
  ],
  "exclude": ["node_modules"]
}
</file>

<file path="Dockerfile">
# 1. 依赖安装阶段
FROM node:20-alpine AS deps
WORKDIR /app
# 复制依赖定义文件
COPY package.json package-lock.json* ./
# 安装依赖 (ci 安装更稳定)
RUN npm ci

# 2. 构建阶段
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 禁用 Next.js 的遥测数据
ENV NEXT_TELEMETRY_DISABLED 1

# 开始构建
RUN npm run build

# 3. 运行阶段 (生产环境)
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV production
ENV NEXT_TELEMETRY_DISABLED 1

# 创建非 root 用户 (安全最佳实践)
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# 复制构建产物
# public 目录包含静态资源 (图片等)
COPY --from=builder /app/public ./public

# 自动生成的 standalone 文件夹包含了运行所需的所有最小依赖
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

# Next.js 默认端口是 3000
EXPOSE 3000

ENV PORT 3000
# 这里的主机名必须设为 0.0.0.0，否则 Docker 外部无法访问
ENV HOSTNAME "0.0.0.0"

# 启动命令 (注意这里运行的是 server.js)
CMD ["node", "server.js"]
</file>

<file path="public/vercel.svg">
<svg width="400" height="400" viewBox="0 0 400 400" fill="none" xmlns="http://www.w3.org/2000/svg">
    <!--
      Brand: Huawai (画外) - Geometric Final
      Concept: The Architectural H
      Principles: Strict Grid, Uniform Weight, Micro-Radius
    -->

    <!-- 1. Background: The Squircle (Xiaomi/Apple Shape) -->
    <path d="M 200 0 C 400 0 400 0 400 200 C 400 400 400 400 200 400 C 0 400 0 400 0 200 C 0 0 0 0 200 0 Z" fill="#030305"/>

    <!-- 2. The Foundation (Grey): A solid, architectural structure -->
    <!-- Looks like a lowercase 'h' but geometric -->
    <!-- Left Column + Bridge + Right Bottom Column merged into one path -->
    <path d="M 100 80
           L 100 320
           Q 100 340 120 340
           L 160 340
           Q 180 340 180 320
           L 180 260
           L 220 260
           L 220 320
           Q 220 340 240 340
           L 280 340
           Q 300 340 300 320
           L 300 220
           Q 300 200 280 200
           L 280 200
           L 180 200
           L 180 100
           Q 180 80 160 80
           L 120 80
           Q 100 80 100 100
           Z"
          fill="#D4D4D8"/>

    <!-- 3. The Window (Indigo): The Floating Pixel -->
    <!-- Top Right corner, completing the implied capital 'H' -->
    <!-- Perfectly aligned with the grid of the grey part below it -->
    <rect x="220" y="80" width="80" height="80" rx="16" fill="#6366F1"/>

</svg>
</file>

<file path="src/app/api/og/route.tsx">
import { ImageResponse } from 'next/og';
import { NextRequest } from 'next/server';

export const runtime = 'edge';

export async function GET(req: NextRequest) {
    try {
        const { searchParams } = new URL(req.url);
        const title = searchParams.get('title') || '画外 Huawai';
        const rating = searchParams.get('rating') || '';
        const year = searchParams.get('year') || '';
        const poster = searchParams.get('poster');
        const category = searchParams.get('category') || 'Movie';

        return new ImageResponse(
            (
                <div
                    style={{
                        height: '100%',
                        width: '100%',
                        display: 'flex',
                        flexDirection: 'row',
                        backgroundColor: '#030305',
                        color: 'white',
                        fontFamily: 'sans-serif',
                    }}
                >
                    <div style={{ display: 'flex', width: '40%', height: '100%', position: 'relative' }}>
                        {poster ? (
                            <img
                                src={poster}
                                alt={title}
                                style={{ width: '100%', height: '100%', objectFit: 'cover', opacity: 0.6 }}
                            />
                        ) : (
                            <div style={{ width: '100%', height: '100%', background: '#1a1a1c' }} />
                        )}
                        <div style={{
                            position: 'absolute',
                            top: 0, left: 0, right: 0, bottom: 0,
                            backgroundImage: 'linear-gradient(to right, rgba(3,3,5,0), #030305)'
                        }} />
                    </div>

                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        width: '60%',
                        padding: '40px 60px 40px 20px',
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
                            <div style={{
                                width: '40px', height: '40px',
                                background: 'white', borderRadius: '8px',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                color: 'black', fontWeight: 900, fontSize: '24px', marginRight: '16px'
                            }}>画</div>
                            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#a1a1aa' }}>HUAWAI</div>
                        </div>

                        <div style={{
                            fontSize: title.length > 10 ? '50px' : '70px',
                            fontWeight: 900,
                            lineHeight: 1.1,
                            marginBottom: '20px',
                            textShadow: '0 0 30px rgba(255,255,255,0.3)',
                            background: 'linear-gradient(to bottom right, #fff, #a1a1aa)',
                            backgroundClip: 'text',
                            color: 'transparent',
                        }}>
                            {title}
                        </div>

                        <div style={{ display: 'flex', gap: '16px', fontSize: '24px', fontWeight: 600 }}>
                            {rating && (
                                <div style={{
                                    background: 'rgba(245, 158, 11, 0.2)',
                                    color: '#fbbf24',
                                    padding: '8px 16px',
                                    borderRadius: '12px',
                                    border: '1px solid rgba(245, 158, 11, 0.3)'
                                }}>
                                    ★ {rating}
                                </div>
                            )}
                            <div style={{
                                background: 'rgba(99, 102, 241, 0.2)',
                                color: '#818cf8',
                                padding: '8px 16px',
                                borderRadius: '12px',
                                border: '1px solid rgba(99, 102, 241, 0.3)'
                            }}>
                                4K HDR
                            </div>
                            {year && <div style={{ color: '#71717a', padding: '8px 0' }}>{year}</div>}
                        </div>

                        <div style={{
                            marginTop: '40px',
                            background: 'white',
                            color: 'black',
                            padding: '16px 32px',
                            borderRadius: '999px',
                            fontSize: '24px',
                            fontWeight: 'bold',
                            width: 'fit-content'
                        }}>
                            立即观看
                        </div>
                    </div>
                </div>
            ),
            {
                width: 1200,
                height: 630,
            },
        );
    } catch (e) {
        return new Response(`Failed to generate the image`, {
            status: 500,
        });
    }
}
</file>

<file path="src/app/error.tsx">
"use client";

import { useEffect } from "react";
import { GlowButton } from "@/components/ui/GlowButton";
import { RefreshCw, WifiOff } from "lucide-react";

export default function Error({
                                  error,
                                  reset,
                              }: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        console.error("Home Page Error:", error);
    }, [error]);

    return (
        <div className="min-h-[70vh] flex flex-col items-center justify-center px-6 text-center">
            <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center mb-6 border border-red-500/20">
                <WifiOff className="w-10 h-10 text-red-500" />
            </div>

            <h2 className="text-2xl font-bold text-foreground mb-3">
                信号连接中断
            </h2>
            <p className="text-muted-foreground max-w-sm mb-8">
                无法获取首页推荐数据。这可能是暂时的网络波动或服务器正在维护。
            </p>

            <GlowButton
                onClick={
                    () => reset()
                }
                icon={<RefreshCw className="w-4 h-4" />}
            >
                重新连接
            </GlowButton>

            <div className="mt-8 text-[10px] font-mono text-muted-foreground/40">
                ERROR_DIGEST: {error.digest || "UNKNOWN"}
            </div>
        </div>
    );
}
</file>

<file path="src/app/robots.ts">
import { MetadataRoute } from 'next';

export default function robots(): MetadataRoute.Robots {
    const baseUrl = 'https://huawai.sophia.fr.eu.org';

    return {
        rules: {
            userAgent: '*',
            allow: '/',
            disallow: [
                '/api/',          // API 路由无需索引
                '/my-list/',      // 私人收藏页
                '/profile/',      // 个人中心
                '/login/',        // 登录页
                '/signup/',       // 注册页
                '/activate/',     // 激活页
                '/forgot-password/',
                '/watch/',        // 播放页通常含动态参数，建议只索引详情页(/movie/id)，或者谨慎索引
            ],
        },
        sitemap: `${baseUrl}/sitemap.xml`,
    };
}
</file>

<file path="src/components/features/CastList.tsx">
"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { ChevronDown, ChevronUp, User } from "lucide-react";
import { CastMember } from "@/lib/types";
import { cn } from "@/lib/utils";

interface CastListProps {
    cast: CastMember[];
}

export function CastList({ cast }: CastListProps) {
    const [isExpanded, setIsExpanded] = useState(false);

    const INITIAL_COUNT = 8;
    const hasMore = cast.length > INITIAL_COUNT;

    return (
        <div className="relative">
            <motion.div
                initial={false}
                animate={{
                    height: isExpanded ? "auto" : hasMore ? "130px" : "auto",
                }}
                transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                className={cn(
                    "grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 overflow-hidden relative",
                    !isExpanded && hasMore && "after:absolute after:bottom-0 after:left-0 after:right-0 after:h-24 after:bg-gradient-to-t after:from-background via-background/80 to-transparent after:pointer-events-none after:z-10"
                )}
            >
                {cast.map((actor) => (
                    <div
                        key={actor.id}
                        className="group flex items-center gap-3 px-4 py-3 rounded-lg bg-card/40 border border-border-subtle/10 hover:bg-card/80 hover:border-border-subtle/20 transition-all duration-200 cursor-default"
                    >
                        <div className="p-1.5 rounded-full bg-secondary/50 text-muted-foreground group-hover:text-primary group-hover:bg-primary/10 transition-colors flex-shrink-0">
                            <User className="w-3.5 h-3.5" />
                        </div>

                        <div className="min-w-0">
                            <div className="text-sm font-medium text-foreground group-hover:text-primary transition-colors truncate">
                                {actor.name}
                            </div>
                            <div className="text-xs text-muted-foreground group-hover:text-muted-foreground/80 truncate">
                                {actor.role}
                            </div>
                        </div>
                    </div>
                ))}
            </motion.div>

            {hasMore && (
                <div className={cn(
                    "flex justify-center transition-all duration-500 ease-out relative z-20",
                    isExpanded ? "mt-6" : "-mt-4"
                )}>
                    <motion.button
                        layout
                        onClick={() => setIsExpanded(!isExpanded)}
                        whileHover={{ scale: 1.05 }}
                        whileTap={{ scale: 0.95 }}
                        className="flex items-center gap-2 px-5 py-2 rounded-full bg-card border border-border-subtle/10 text-xs font-bold text-muted-foreground hover:text-foreground hover:border-primary/50 hover:bg-secondary hover:shadow-[0_0_20px_rgba(99,102,241,0.15)] transition-all shadow-lg backdrop-blur-md group"
                    >
                        {isExpanded ? (
                            <>
                                收起 <ChevronUp className="w-3.5 h-3.5 group-hover:-translate-y-0.5 transition-transform" />
                            </>
                        ) : (
                            <>
                                查看全部 ({cast.length}) <ChevronDown className="w-3.5 h-3.5 group-hover:translate-y-0.5 transition-transform" />
                            </>
                        )}
                    </motion.button>
                </div>
            )}
        </div>
    );
}
</file>

<file path="src/components/features/profile/PasswordModal.tsx">
"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Lock, Save } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { useState } from "react";

interface PasswordModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (oldPass: string, newPass: string, confirmPass: string) => Promise<void>;
}

export function PasswordModal({ isOpen, onClose, onSubmit }: PasswordModalProps) {
    const [data, setData] = useState({ old: "", new: "", confirm: "" });

    const handleSubmit = () => {
        onSubmit(data.old, data.new, data.confirm);
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
                    <motion.div
                        initial={{ opacity: 0, scale: 0.95, y: 20 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.95, y: 20 }}
                        className="w-full max-w-md bg-[#1a1a1c] border border-white/10 rounded-3xl p-8 shadow-2xl relative overflow-hidden"
                    >
                        {/* 顶部装饰条 */}
                        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-indigo-500" />

                        <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
                            <Lock className="w-5 h-5 text-indigo-500"/>
                            修改密码
                        </h3>

                        <div className="space-y-4">
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">当前密码</label>
                                <input
                                    type="password"
                                    value={data.old}
                                    onChange={e => setData({...data, old: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                />
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">新密码</label>
                                <input
                                    type="password"
                                    value={data.new}
                                    onChange={e => setData({...data, new: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                    placeholder="至少 6 位字符"
                                />
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">确认新密码</label>
                                <input
                                    type="password"
                                    value={data.confirm}
                                    onChange={e => setData({...data, confirm: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                />
                            </div>
                        </div>

                        <div className="flex gap-3 mt-8">
                            <button
                                onClick={onClose}
                                className="flex-1 py-3 text-neutral-400 hover:text-white transition-colors text-sm font-medium hover:bg-white/5 rounded-xl"
                            >
                                取消
                            </button>
                            <GlowButton
                                className="flex-1"
                                onClick={handleSubmit}
                                icon={<Save className="w-4 h-4"/>}
                            >
                                确认修改
                            </GlowButton>
                        </div>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
}
</file>

<file path="src/components/features/resource/types.ts">
import { MovieDetail } from "@/lib/types";
import React from "react";

export interface ResourcePanelProps {
    movie: MovieDetail;
}

export interface TabProps {
    isActive: boolean;
    onClick: () => void;
    label: string;
    icon: React.ReactNode;
}
</file>

<file path="src/components/features/resource/utils.ts">
export const cleanEpisodeTitle = (rawTitle: string): string => {
    let title = rawTitle.replace(/(\[.*?\]|\(.*?\)|【.*?】)/g, "").trim();

    if (/^\d+$/.test(title)) {
        return title.padStart(2, '0');
    }

    title = title.replace(/(HD|BD|中字|国语|高清|修正|完整版)/gi, "").trim();

    if (!title) {
        return rawTitle.replace(/(\[.*?\])/g, "").substring(0, 8);
    }

    return title;
};
</file>

<file path="src/components/ui/DualRangeSlider.tsx">
"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

interface DualRangeSliderProps {
    min: number;
    max: number;
    value: [number, number];
    onChange: (value: [number, number]) => void;
    onCommit: (value: [number, number]) => void;
    className?: string;
}

export function DualRangeSlider({
                                    min,
                                    max,
                                    value,
                                    onChange,
                                    onCommit,
                                    className
                                }: DualRangeSliderProps) {
    const [localValue, setLocalValue] = useState(value);
    const minRef = useRef<HTMLInputElement>(null);
    const maxRef = useRef<HTMLInputElement>(null);
    const trackRef = useRef<HTMLDivElement>(null);

    // Sync external value changes
    useEffect(() => {
        setLocalValue(value);
    }, [value]);

    const getPercent = useCallback(
        (val: number) => Math.round(((val - min) / (max - min)) * 100),
        [min, max]
    );

    const handleMinChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const val = Math.min(Number(event.target.value), localValue[1] - 1);
        const newValue: [number, number] = [val, localValue[1]];
        setLocalValue(newValue);
        onChange(newValue);
    };

    const handleMaxChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const val = Math.max(Number(event.target.value), localValue[0] + 1);
        const newValue: [number, number] = [localValue[0], val];
        setLocalValue(newValue);
        onChange(newValue);
    };

    const handleCommit = () => {
        onCommit(localValue);
    };

    return (
        <div className={cn("flex flex-col w-full gap-4 touch-none select-none", className)}>
            <div className="flex items-center justify-between text-sm font-bold font-mono text-muted-foreground px-1">
                <span>{localValue[0]}</span>
                <span>{localValue[1]}</span>
            </div>

            <div className="relative w-full h-6 flex items-center">
                {/* Visual Track */}
                <div className="absolute w-full h-1.5 bg-secondary/50 rounded-full overflow-hidden">
                    <div
                        className="absolute h-full bg-primary transition-all duration-75 ease-out"
                        style={{
                            left: `${getPercent(localValue[0])}%`,
                            width: `${getPercent(localValue[1]) - getPercent(localValue[0])}%`
                        }}
                    />
                </div>

                {/* Range Inputs */}
                <input
                    type="range"
                    min={min}
                    max={max}
                    value={localValue[0]}
                    onChange={handleMinChange}
                    onMouseUp={handleCommit}
                    onTouchEnd={handleCommit}
                    className="absolute w-full h-full appearance-none bg-transparent pointer-events-none z-30 focus:outline-none
                    [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
                    [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white
                    [&::-webkit-slider-thumb]:shadow-[0_0_10px_rgba(0,0,0,0.3)] [&::-webkit-slider-thumb]:appearance-none
                    [&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb]:active:cursor-grabbing
                    [&::-webkit-slider-thumb]:border [&::-webkit-slider-thumb]:border-border-subtle/50
                    [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4
                    [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:border-none
                    [&::-moz-range-thumb]:cursor-grab [&::-moz-range-thumb]:active:cursor-grabbing"
                />
                <input
                    type="range"
                    min={min}
                    max={max}
                    value={localValue[1]}
                    onChange={handleMaxChange}
                    onMouseUp={handleCommit}
                    onTouchEnd={handleCommit}
                    className="absolute w-full h-full appearance-none bg-transparent pointer-events-none z-40 focus:outline-none
                    [&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:h-4
                    [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white
                    [&::-webkit-slider-thumb]:shadow-[0_0_10px_rgba(0,0,0,0.3)] [&::-webkit-slider-thumb]:appearance-none
                    [&::-webkit-slider-thumb]:cursor-grab [&::-webkit-slider-thumb]:active:cursor-grabbing
                    [&::-webkit-slider-thumb]:border [&::-webkit-slider-thumb]:border-border-subtle/50
                    [&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:w-4 [&::-moz-range-thumb]:h-4
                    [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:border-none
                    [&::-moz-range-thumb]:cursor-grab [&::-moz-range-thumb]:active:cursor-grabbing"
                />
            </div>
        </div>
    );
}
</file>

<file path="src/components/ui/Toaster.tsx">
"use client";

import { useToast } from "@/lib/context/ToastContext";
import { motion, AnimatePresence } from "framer-motion";
import { CheckCircle2, AlertCircle, Info, Loader2, X } from "lucide-react";
import { cn } from "@/lib/utils";

export function Toaster() {
    const { toasts, removeToast } = useToast();

    return (
        <div className="fixed top-24 right-6 z-[100] flex flex-col gap-3 pointer-events-none">
            <AnimatePresence mode="popLayout">
                {toasts.map((toast) => (
                    <motion.div
                        key={toast.id}
                        layout
                        initial={{ opacity: 0, x: 50, scale: 0.9 }}
                        animate={{ opacity: 1, x: 0, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.9, transition: { duration: 0.2 } }}
                        className={cn(
                            "pointer-events-auto w-80 p-4 rounded-xl border backdrop-blur-xl shadow-2xl flex items-start gap-3 relative overflow-hidden",
                            toast.type === "success" && "bg-green-500/10 border-green-500/20 text-green-700 dark:text-green-200",
                            toast.type === "error" && "bg-red-500/10 border-red-500/20 text-red-700 dark:text-red-200",
                            toast.type === "info" && "bg-indigo-500/10 border-indigo-500/20 text-indigo-700 dark:text-indigo-200",
                            toast.type === "loading" && "bg-card/90 border-border-subtle/10 text-foreground"
                        )}
                    >
                        <div className="flex-shrink-0 mt-0.5">
                            {toast.type === "success" && <CheckCircle2 className="w-5 h-5 text-green-500" />}
                            {toast.type === "error" && <AlertCircle className="w-5 h-5 text-red-500" />}
                            {toast.type === "info" && <Info className="w-5 h-5 text-indigo-500" />}
                            {toast.type === "loading" && <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />}
                        </div>

                        <div className="flex-1 mr-2">
                            <p className="text-sm font-medium leading-tight">{toast.message}</p>
                        </div>

                        <button
                            onClick={() => removeToast(toast.id)}
                            className="text-foreground/40 hover:text-foreground transition-colors absolute top-2 right-2"
                        >
                            <X className="w-4 h-4" />
                        </button>

                        {toast.type !== "loading" && toast.duration && toast.duration > 0 && (
                            <motion.div
                                initial={{ width: "100%" }}
                                animate={{ width: "0%" }}
                                transition={{ duration: toast.duration / 1000, ease: "linear" }}
                                className={cn(
                                    "absolute bottom-0 left-0 h-0.5",
                                    toast.type === "success" && "bg-green-500",
                                    toast.type === "error" && "bg-red-500",
                                    toast.type === "info" && "bg-indigo-500"
                                )}
                            />
                        )}
                    </motion.div>
                ))}
            </AnimatePresence>
        </div>
    );
}
</file>

<file path="src/hooks/useCarousel.ts">
"use client";

import { useState, useEffect, useCallback, useRef } from "react";

interface UseCarouselProps {
    totalItems: number;
    interval?: number;
    autoPlay?: boolean;
}

export function useCarousel({ totalItems, interval = 6000, autoPlay = true }: UseCarouselProps) {
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);

    const nextSlideRef = useRef<() => void>(() => {});

    const nextSlide = useCallback(() => {
        setCurrentIndex((prev) => (prev + 1) % totalItems);
    }, [totalItems]);

    const prevSlide = useCallback(() => {
        setCurrentIndex((prev) => (prev - 1 + totalItems) % totalItems);
    }, [totalItems]);

    const goToSlide = useCallback((index: number) => {
        setCurrentIndex(index);
    }, []);

    useEffect(() => {
        nextSlideRef.current = nextSlide;
    }, [nextSlide]);

    useEffect(() => {
        if (isPaused || !autoPlay) return;

        const timer = setInterval(() => {
            nextSlideRef.current();
        }, interval);

        return () => clearInterval(timer);
    }, [isPaused, autoPlay, interval]);

    return {
        currentIndex,
        isPaused,
        setIsPaused,
        nextSlide,
        prevSlide,
        goToSlide
    };
}
</file>

<file path="src/hooks/useDebounce.ts">
"use client";

import { useState, useEffect } from "react";

export function useDebounce<T>(value: T, delay: number): T {
    const [debouncedValue, setDebouncedValue] = useState<T>(value);
    useEffect(() => {
        const handler = setTimeout(() => setDebouncedValue(value), delay);
        return () => clearTimeout(handler);
    }, [value, delay]);
    return debouncedValue;
}
</file>

<file path="src/hooks/useMediaInteraction.ts">
"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { interactionApi } from "@/lib/api/interaction";
import { getErrorMessage } from "@/lib/utils";

interface UseMediaInteractionResult {
    isFavorite: boolean;
    isLoading: boolean;
    toggleFavorite: (e?: React.MouseEvent) => Promise<void>;
    checkFavoriteStatus: () => Promise<void>;
}

export function useMediaInteraction(unifiedVideoId: string): UseMediaInteractionResult {
    const { user } = useAuth();
    const { addToast } = useToast();
    const router = useRouter();

    const [isFavorite, setIsFavorite] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [hasChecked, setHasChecked] = useState(false);

    // Check status on mount if user exists
    const checkFavoriteStatus = useCallback(async () => {
        if (!user || !unifiedVideoId) return;
        try {
            const status = await interactionApi.checkFavorite(unifiedVideoId);
            setIsFavorite(status);
            setHasChecked(true);
        } catch (error) {
            console.error("Failed to check favorite status", error);
            setHasChecked(true);
        }
    }, [user, unifiedVideoId]);

    useEffect(() => {
        if (user && unifiedVideoId && !hasChecked) {
            checkFavoriteStatus();
        }
    }, [user, unifiedVideoId, hasChecked, checkFavoriteStatus]);

    const toggleFavorite = async (e?: React.MouseEvent) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }

        if (!user) {
            addToast("请先登录以收藏影片", "error");
            router.push("/login");
            return;
        }

        if (isLoading) return;

        setIsLoading(true);
        try {
            const newState = await interactionApi.toggleFavorite(unifiedVideoId);
            setIsFavorite(newState);
            addToast(newState ? "已加入收藏" : "已取消收藏", newState ? "success" : "info");
        } catch (err) {
            addToast(getErrorMessage(err) || "操作失败，请重试", "error");
        } finally {
            setIsLoading(false);
        }
    };

    return {
        isFavorite,
        isLoading,
        toggleFavorite,
        checkFavoriteStatus
    };
}
</file>

<file path="src/hooks/useVideoHeartbeat.ts">
"use client";

import { useEffect, useCallback, useRef } from "react";
import { useAuth } from "@/lib/context/AuthContext";
import { interactionApi } from "@/lib/api/interaction";

interface PlaybackState {
    currentTime: number;
    duration: number;
    isPlaying: boolean;
}

interface UseVideoHeartbeatProps {
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    /**
     * A callback that returns the current state of the player.
     * This avoids stale closures in setInterval.
     */
    getPlaybackState: () => PlaybackState;
    /**
     * How often to report progress in milliseconds. Default 15000 (15s).
     */
    intervalMs?: number;
}

export function useVideoHeartbeat({
                                      unifiedVideoId,
                                      videoId,
                                      episodeId,
                                      episodeName,
                                      getPlaybackState,
                                      intervalMs = 15000
                                  }: UseVideoHeartbeatProps) {
    const { user, isStealthMode } = useAuth();

    const report = useCallback((useKeepAlive = false) => {
        // 1. Check Auth & Stealth Mode
        if (!user || isStealthMode) return;

        // 2. Validate Metadata
        if (!unifiedVideoId || !videoId || !episodeName) return;

        // 3. Get Realtime State
        const { currentTime, duration, isPlaying } = getPlaybackState();

        // 4. Validate State (Don't report if paused and not forcing keepAlive, or if invalid duration)
        // Note: For keepAlive (tab close), we report even if paused to save exact spot.
        // For interval, we usually only report if playing.
        if (!useKeepAlive && !isPlaying) return;
        if (duration <= 0) return;

        // 5. API Call
        interactionApi.reportProgress(
            unifiedVideoId,
            videoId,
            episodeId,
            episodeName,
            Math.floor(currentTime),
            Math.floor(duration),
            useKeepAlive
        ).catch(console.error);
    }, [user, isStealthMode, unifiedVideoId, videoId, episodeId, episodeName, getPlaybackState]);

    // Setup Interval
    useEffect(() => {
        const timer = setInterval(() => {
            if (document.visibilityState === 'visible') {
                report(false);
            }
        }, intervalMs);

        return () => clearInterval(timer);
    }, [report, intervalMs]);

    // Setup BeforeUnload
    useEffect(() => {
        const handleBeforeUnload = () => report(true);
        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [report]);

    // Return manual report function if needed (e.g. on Pause event)
    return { report };
}
</file>

<file path="src/lib/domain/video.ts">
import { Episode, VideoFormat } from "@/lib/types";

export const VideoDomain = {
    /**
     * 检测视频 URL 的格式类型
     */
    detectFormat(url: string): VideoFormat {
        if (!url) return 'embed';
        const lower = url.toLowerCase();

        if (lower.includes('.m3u8')) {
            return 'hls';
        }
        if (lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg')) {
            return 'mp4';
        }

        return 'embed';
    },

    /**
     * 将集数列表按照播放方式分组 (直连 vs 外链/Embed)
     * 用于 UI 层的分栏展示
     */
    groupEpisodes(episodes: Episode[]) {
        const native: Episode[] = [];
        const external: Episode[] = [];

        episodes.forEach(ep => {
            const fmt = this.detectFormat(ep.url);
            if (fmt === 'embed') {
                external.push(ep);
            } else {
                native.push(ep);
            }
        });

        return { native, external };
    }
};
</file>

<file path="src/proxy.ts">
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function proxy(request: NextRequest) {
    const { pathname, search } = request.nextUrl;
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';

    if (pathname.startsWith('/api/portal/')) {
        const targetPath = pathname;
        const targetUrl = new URL(targetPath + search, backendUrl);
        console.log(`[Proxy] Forwarding API: ${pathname} -> ${targetUrl.toString()}`);
        return NextResponse.rewrite(targetUrl);
    }

    if (pathname.startsWith('/api/captcha/')) {
        const targetUrl = new URL(pathname + search, backendUrl);
        return NextResponse.rewrite(targetUrl);
    }

    if (pathname.startsWith('/media/')) {
        const targetUrl = new URL(pathname + search, backendUrl);
        return NextResponse.rewrite(targetUrl);
    }

    return NextResponse.next();
}

export const config = {
    matcher: [
        '/api/portal/:path*',
        '/api/captcha/:path*',
        '/media/:path*',
    ],
};
</file>

<file path=".gitea/workflows/ci.yaml">
name: Frontend - Test, Build, Push and Deploy

on:
  push:
    branches:
      - master

jobs:
  ################################################################################
  # JOB 1: 测试与构建
  ################################################################################
  build-and-test:
    name: "📦 Build & Test"
    runs-on: ircs
    outputs:
      image_name: ${{ steps.set_outputs.outputs.image_name }}
      image_tag_sha: ${{ steps.set_outputs.outputs.image_tag_sha }}
    steps:
      - name: "1. Checkout Code"
        uses: actions/checkout@v4

      - name: "2. Set up Node.js"
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: "3. Set up Docker Buildx"
        uses: docker/setup-buildx-action@v3

      - name: "4. Build Docker image"
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: false
          outputs: type=docker,dest=/tmp/huawai.tar
          tags: |
            harbor.mnnu.eu.org/ircs/huawai:${{ github.sha }}
            harbor.mnnu.eu.org/ircs/huawai:latest

      - name: "5. Upload Artifact"
        uses: actions/upload-artifact@v3
        with:
          name: huawai-image
          path: /tmp/huawai.tar

      - name: "6. Set Outputs"
        id: set_outputs
        run: |
          echo "image_name=harbor.mnnu.eu.org/ircs/huawai" >> $GITHUB_OUTPUT
          echo "image_tag_sha=${{ github.sha }}" >> $GITHUB_OUTPUT

  ################################################################################
  # JOB 2: 推送镜像 (包含修复后的隧道步骤)
  ################################################################################
  push:
    name: "🚀 Push Image"
    runs-on: ircs
    needs: build-and-test
    outputs:
      image_name: ${{ needs.build-and-test.outputs.image_name }}
      image_tag_sha: ${{ needs.build-and-test.outputs.image_tag_sha }}
    steps:
      - name: "0. Setup Tunnel to Internal Harbor"
        run: |
          echo "🛠️ Preparing network tunnel to Harbor..."
          
          if ! command -v socat &> /dev/null || ! command -v ip &> /dev/null; then
              echo "Installing dependencies (socat, iproute2)..."
              sudo apt-get update && sudo apt-get install -y socat iproute2
          fi

          HOST_IP=$(ip route show | grep default | awk '{print $3}')
          echo "👉 Host IP detected: $HOST_IP"

          echo "🔌 Starting socat tunnel: 127.0.0.1:443 -> $HOST_IP:32400"
          sudo socat TCP-LISTEN:443,fork,bind=127.0.0.1 TCP:$HOST_IP:32400 &

          DOMAIN="harbor.mnnu.eu.org"
          echo "127.0.0.1 $DOMAIN" | sudo tee -a /etc/hosts
          
          echo "✅ Tunnel established."
      # --- 🛠️ 修复结束 ---

      - name: "1. Download Artifact"
        uses: actions/download-artifact@v3
        with:
          name: huawai-image
          path: /tmp

      - name: "2. Load & Push"
        uses: docker/login-action@v3
        with:
          registry: harbor.mnnu.eu.org
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: "3. Push Docker Image"
        run: |
          docker load --input /tmp/huawai.tar
          IMAGE_NAME="${{ needs.build-and-test.outputs.image_name }}"
          TAG_SHA="${{ needs.build-and-test.outputs.image_tag_sha }}"
          docker push "$IMAGE_NAME:$TAG_SHA"
          docker push "$IMAGE_NAME:latest"

  ################################################################################
  # JOB 3: 部署 (GitOps)
  ################################################################################
  deploy:
    name: "🛰️ Deploy via GitOps"
    runs-on: ircs
    needs: push
    steps:
      - name: "1. Install yq"
        run: |
          if ! command -v yq &> /dev/null; then
              sudo wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_arm64 -O /usr/bin/yq
              sudo chmod +x /usr/bin/yq
          fi

      - name: "2. Update GitOps Repository"
        env:
          GITOPS_REPO_URL: "https://gitea.mnnu.eu.org/gitea-admin/ircs-config.git"
          GITOPS_USER: "gitea-bot"
          GITOPS_TOKEN: ${{ secrets.GITOPS_TOKEN }}
          NEW_IMAGE: ${{ needs.push.outputs.image_name }}:${{ needs.push.outputs.image_tag_sha }}
          NEW_TAG: ${{ needs.push.outputs.image_tag_sha }}
        run: |
          git clone --depth 1 "https://${GITOPS_USER}:${GITOPS_TOKEN}@${GITOPS_REPO_URL#https://}" gitops_repo
          cd gitops_repo

          TARGET_FILE="09-huawai-deployment.yaml"

          if [ ! -f "$TARGET_FILE" ]; then
            echo "❌ Error: $TARGET_FILE not found in ircs-config repo!"
            exit 1
          fi
          
          echo "Updating image to: $NEW_IMAGE"
          yq e -i 'select(.kind == "Deployment" and .metadata.name == "huawai").spec.template.spec.containers[0].image = strenv(NEW_IMAGE)' "$TARGET_FILE"
          
          git config --global user.name 'Gitea CI Bot'
          git config --global user.email 'gitea-bot@mnnu.eu.org'
          
          git add "$TARGET_FILE"
          if ! git diff --staged --quiet; then
            git commit -m "ci(frontend): Update huawai image to ${NEW_TAG}"
            git push
            echo "✅ Deployment updated."
          else
            echo "ℹ️ No changes needed."
          fi
</file>

<file path="public/logo.svg">
<svg width="400" height="400" viewBox="0 0 400 400" fill="none" xmlns="http://www.w3.org/2000/svg">
    <defs>
        <linearGradient id="brandGradient" x1="0" y1="0" x2="400" y2="400" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stop-color="#2A2A2A" /> <stop offset="100%" stop-color="#000000" /> </linearGradient>

        <filter id="innerShadow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="4" result="blur"/>
            <feOffset dx="2" dy="4"/>
            <feComposite in2="SourceAlpha" operator="arithmetic" k2="-1" k3="1"/>
            <feColorMatrix type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.2 0"/>
        </filter>
    </defs>

    <path
            d="M 200 0 C 366 0 400 34 400 200 C 400 366 366 400 200 400 C 34 400 0 366 0 200 C 0 34 34 0 200 0 Z"
            fill="url(#brandGradient)"
    />

    <path
            d="M 200 0 C 366 0 400 34 400 200 C 400 366 366 400 200 400 C 34 400 0 366 0 200 C 0 34 34 0 200 0 Z"
            fill="white"
            fill-opacity="0.05"
            style="mix-blend-mode: overlay;"
    />

    <text x="200" y="200"
          dominant-baseline="central"
          text-anchor="middle"
          font-family="'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', serif"
          font-weight="900"
          font-size="220"
          fill="#FFFFFF"
          style="transform: translateY(-12px); text-shadow: 0 4px 10px rgba(0,0,0,0.3);"
    >
        画
    </text>
</svg>
</file>

<file path="public/window.svg">
<svg width="400" height="400" viewBox="0 0 400 400" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M 200 0 C 400 0 400 0 400 200 C 400 400 400 400 200 400 C 0 400 0 400 0 200 C 0 0 0 0 200 0 Z" fill="#030305"/>

    <path d="M 100 80
           L 100 320
           Q 100 340 120 340
           L 160 340
           Q 180 340 180 320
           L 180 260
           L 220 260
           L 220 320
           Q 220 340 240 340
           L 280 340
           Q 300 340 300 320
           L 300 220
           Q 300 200 280 200
           L 280 200
           L 180 200
           L 180 100
           Q 180 80 160 80
           L 120 80
           Q 100 80 100 100
           Z"
          fill="#D4D4D8"/>

    <rect x="220" y="80" width="80" height="80" rx="16" fill="#6366F1"/>

</svg>
</file>

<file path="src/app/reset-password/page.tsx">
"use client";

import { useState, Suspense, useEffect } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Lock, ArrowLeft, CheckCircle2, AlertCircle } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { getErrorMessage } from "@/lib/utils";

function ResetPasswordContent() {
    const searchParams = useSearchParams();
    const router = useRouter();
    const { addToast } = useToast();

    const token = searchParams.get("token");

    const [isLoading, setIsLoading] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);
    const [formData, setFormData] = useState({
        newPassword: "",
        confirmPassword: ""
    });

    // 如果没有 Token，直接显示错误提示
    useEffect(() => {
        if (!token) {
            addToast("无效的重置链接：缺少令牌", "error");
        }
    }, [token, addToast]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!token) {
            addToast("重置令牌丢失，请重新申请", "error");
            return;
        }

        if (formData.newPassword.length < 6) {
            addToast("密码长度至少为 6 位", "error");
            return;
        }

        if (formData.newPassword !== formData.confirmPassword) {
            addToast("两次输入的密码不一致", "error");
            return;
        }

        try {
            setIsLoading(true);
            await authApi.resetPassword(token, formData.newPassword);
            setIsSuccess(true);
            addToast("密码重置成功", "success");

            // 3秒后自动跳转
            setTimeout(() => {
                router.push("/login");
            }, 3000);

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "重置失败，链接可能已过期", "error");
        } finally {
            setIsLoading(false);
        }
    };

    if (!token) {
        return (
            <div className="text-center">
                <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-4 border border-red-500/20">
                    <AlertCircle className="w-8 h-8 text-red-500" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-2">链接无效</h3>
                <p className="text-muted-foreground mb-6">
                    重置链接无效或已过期。请重新发起申请。
                </p>
                <Link href="/forgot-password">
                    <GlowButton>重新申请</GlowButton>
                </Link>
            </div>
        );
    }

    return (
        <AnimatePresence mode="wait">
            {!isSuccess ? (
                <motion.form
                    key="reset-form"
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -20 }}
                    onSubmit={handleSubmit}
                    className="space-y-6"
                >
                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">新密码</label>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.newPassword}
                                onChange={(e) => setFormData({ ...formData, newPassword: e.target.value })}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="设置新密码 (至少6位)"
                            />
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">确认新密码</label>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.confirmPassword}
                                onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="再次输入以确认"
                            />
                        </div>
                    </div>

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                            disabled={isLoading}
                        >
                            {isLoading ? "重置中..." : "确认修改"}
                        </GlowButton>
                    </div>
                </motion.form>
            ) : (
                <motion.div
                    key="success"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="bg-green-500/10 border border-green-500/20 rounded-2xl p-8 text-center"
                >
                    <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
                        <CheckCircle2 className="w-8 h-8 text-green-500" />
                    </div>
                    <h3 className="text-xl font-bold text-foreground mb-2">密码重置成功</h3>
                    <p className="text-muted-foreground text-sm mb-6">
                        您的密码已更新。即将为您跳转至登录页面...
                    </p>
                    <Link href="/login">
                        <button className="text-sm font-bold text-primary hover:underline underline-offset-4">
                            立即登录
                        </button>
                    </Link>
                </motion.div>
            )}
        </AnimatePresence>
    );
}

export default function ResetPasswordPage() {
    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 blur-[120px] rounded-full" />
                <div className="absolute bottom-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/10 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.5 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <Lock className="w-6 h-6 text-primary" />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">重置密码</h1>
                    <p className="text-muted-foreground">请设置您的新安全密码</p>
                </div>

                <Suspense fallback={
                    <div className="flex justify-center py-10">
                        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
                    </div>
                }>
                    <ResetPasswordContent />
                </Suspense>

                <div className="mt-8 text-center">
                    <Link
                        href="/login"
                        className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors group"
                    >
                        <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-1" />
                        返回登录界面
                    </Link>
                </div>
            </motion.div>
        </div>
    );
}
</file>

<file path="src/app/sitemap.ts">
import { MetadataRoute } from 'next';
import { contentApi } from '@/lib/api/content';

// 强制动态
export const dynamic = 'force-dynamic';

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
    // 1. 基础域名 (已修复双斜杠问题)
    const baseUrl = 'https://huawai.sophia.fr.eu.org';

    // ------------------------------------------------
    // A. 基础静态页面
    // ------------------------------------------------
    const staticRoutes = [
        '',             // 首页
        '/explore',     // 探索总页
        '/login',
        '/signup',
        '/feedback',    // [新增] 留言板/留言墙
    ].map((route) => ({
        url: `${baseUrl}${route}`,
        lastModified: new Date().toISOString(),
        changeFrequency: 'daily' as const,
        priority: 1,
    }));

    // ------------------------------------------------
    // B. 分类入口 (SEO 权重较高)
    // ------------------------------------------------
    // 这些硬编码的分类来自你的 CategoryNav.tsx 或 search/page.tsx
    const categories = ['movie', 'series', 'short-drama', 'variety', 'anime', 'documentary'];

    const categoryRoutes = categories.map((cat) => ({
        url: `${baseUrl}/explore?type=${cat}`,
        lastModified: new Date().toISOString(),
        changeFrequency: 'daily' as const,
        priority: 0.9, // 权重略低于首页，但高于具体电影
    }));

    // ------------------------------------------------
    // C. 动态数据获取 (电影详情 + 题材聚合)
    // ------------------------------------------------
    let movieRoutes: MetadataRoute.Sitemap = [];
    let genreRoutes: MetadataRoute.Sitemap = [];

    try {
        // 并行请求：获取热门电影 + 获取元数据(题材/地区等)
        const [exploreRes, metadataRes] = await Promise.all([
            contentApi.explore({ page: 0, size: 1000, sort: 'trending' }),
            contentApi.getMetadata()
        ]);

        // C-1. 电影详情页
        if (exploreRes.content) {
            movieRoutes = exploreRes.content.map((movie) => ({
                url: `${baseUrl}/movie/${movie.id}`,
                // 如果后端有 updateTime，建议使用 movie.lastTrendAt 或其他时间字段
                lastModified: new Date().toISOString(),
                changeFrequency: 'weekly' as const,
                priority: 0.8,
            }));
        }

        // C-2. [高级] 题材聚合页 (例如: /explore?genre=科幻)
        // 这会让搜索引擎收录你的"科幻片"、"喜剧片"等分类列表
        if (metadataRes.genres) {
            genreRoutes = metadataRes.genres.map((genre) => ({
                // 注意：URL 中的中文参数通常需要 encodeURIComponent，但 Next.js Sitemap 会自动处理
                url: `${baseUrl}/explore?genre=${encodeURIComponent(genre)}`,
                lastModified: new Date().toISOString(),
                changeFrequency: 'daily' as const,
                priority: 0.7,
            }));
        }

    } catch (error) {
        console.error('Sitemap generation failed:', error);
    }

    // 合并所有路由
    return [
        ...staticRoutes,
        ...categoryRoutes,
        ...genreRoutes,
        ...movieRoutes,
    ];
}
</file>

<file path="src/app/template.tsx">
"use client";

import {motion, Variants} from "framer-motion";
import {ReactNode} from "react";

const variants: Variants = {
    hidden: {opacity: 0},
    enter: {
        opacity: 1,
        transition: {
            duration: 0.3,
            ease: "easeOut"
        }
    }
};

export default function Template({children}: { children: ReactNode }) {
    return (
        <motion.div
            variants={variants}
            initial="hidden"
            animate="enter"
            className="w-full min-h-screen"
        >
            {children}
        </motion.div>
    );
}
</file>

<file path="src/components/features/CategoryNav.tsx">
"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { Clapperboard, Tv, Smartphone, Mic2, Ghost, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

const CATEGORIES = [
    {
        id: "movie",
        name: "电影",
        eng: "Movies",
        icon: Clapperboard,
        color: "from-blue-500/20 to-cyan-500/20",
        hoverBorder: "group-hover:border-blue-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(59,130,246,0.2)]"
    },
    {
        id: "series",
        name: "剧集",
        eng: "Series",
        icon: Tv,
        color: "from-purple-500/20 to-pink-500/20",
        hoverBorder: "group-hover:border-purple-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(168,85,247,0.2)]"
    },
    {
        id: "short-drama",
        name: "短剧",
        eng: "Shorts",
        icon: Smartphone,
        color: "from-amber-500/20 to-orange-500/20",
        hoverBorder: "group-hover:border-amber-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(245,158,11,0.2)]"
    },
    {
        id: "variety",
        name: "综艺",
        eng: "Variety",
        icon: Mic2,
        color: "from-green-500/20 to-emerald-500/20",
        hoverBorder: "group-hover:border-green-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(34,197,94,0.2)]"
    },
    {
        id: "anime",
        name: "动漫",
        eng: "Anime",
        icon: Ghost,
        color: "from-rose-500/20 to-red-500/20",
        hoverBorder: "group-hover:border-rose-500/50",
        glow: "group-hover:shadow-[0_0_30px_rgba(244,63,94,0.2)]"
    }
];

export function CategoryNav() {
    return (
        <section className="px-4 md:px-6 w-full">
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 md:gap-4">
                {CATEGORIES.map((cat, idx) => (
                    <Link key={cat.id} href={`/explore?type=${cat.id}`} className="block h-full">
                        <motion.div
                            initial={{ opacity: 0, y: 20 }}
                            whileInView={{ opacity: 1, y: 0 }}
                            viewport={{ once: true }}
                            transition={{ delay: idx * 0.05 }}
                            whileHover={{ scale: 1.02, y: -2 }}
                            whileTap={{ scale: 0.98 }}
                            className={cn(
                                "group relative h-24 md:h-28 rounded-2xl overflow-hidden border transition-all duration-300",
                                "bg-card border-border-subtle/10 hover:bg-card/80",
                                cat.hoverBorder,
                                cat.glow
                            )}
                        >
                            <div className={cn(
                                "absolute inset-0 bg-gradient-to-br opacity-0 group-hover:opacity-100 transition-opacity duration-500",
                                cat.color
                            )} />

                            <div className="absolute inset-0 bg-secondary/5 group-hover:bg-transparent transition-colors" />

                            <div className="relative z-10 h-full flex flex-col justify-between p-4">
                                <div className="flex justify-between items-start">
                                    <div className="p-2 rounded-lg bg-secondary text-muted-foreground group-hover:text-foreground group-hover:bg-background/80 transition-colors">
                                        <cat.icon className="w-5 h-5" />
                                    </div>
                                    <ArrowRight className="w-4 h-4 text-muted-foreground/50 group-hover:text-foreground -translate-x-2 opacity-0 group-hover:translate-x-0 group-hover:opacity-100 transition-all duration-300" />
                                </div>

                                <div>
                                    <h3 className="text-base font-bold text-foreground transition-colors">
                                        {cat.name}
                                    </h3>
                                    <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider group-hover:text-foreground/70 transition-colors">
                                        {cat.eng}
                                    </p>
                                </div>
                            </div>
                        </motion.div>
                    </Link>
                ))}
            </div>
        </section>
    );
}
</file>

<file path="src/components/features/explore/ExploreControls.tsx">
"use client";

import { SlidersHorizontal } from "lucide-react";
import { cn } from "@/lib/utils";
import { CategoryItem } from "@/lib/types";

interface ExploreControlsProps {
    showFilters: boolean;
    setShowFilters: (show: boolean) => void;
    hasAdvancedFilters: boolean;

    categories: CategoryItem[];
    currentCategorySlug: string;
    onCategoryChange: (slug: string) => void;

    isLoading: boolean;
    totalResults: number;

    currentSort: string;
    onSortChange: (sort: string) => void;
}

export function ExploreControls({
                                    showFilters,
                                    setShowFilters,
                                    hasAdvancedFilters,
                                    categories,
                                    currentCategorySlug,
                                    onCategoryChange,
                                    isLoading,
                                    totalResults,
                                    currentSort,
                                    onSortChange
                                }: ExploreControlsProps) {
    return (
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border-subtle/10 pb-6">
            <div className="flex flex-wrap items-center gap-4">
                <button
                    onClick={() => setShowFilters(!showFilters)}
                    className={cn(
                        "flex items-center justify-center gap-2 text-sm px-5 py-2.5 rounded-full border transition-all font-medium min-w-[100px]",
                        showFilters || hasAdvancedFilters
                            ? "bg-foreground text-background border-foreground shadow-lg shadow-foreground/10"
                            : "bg-secondary text-muted-foreground border-border-subtle/10 hover:text-foreground hover:bg-secondary/80 hover:border-border-subtle/20"
                    )}
                >
                    <SlidersHorizontal className="w-4 h-4" />
                    <span>筛选</span>
                    {hasAdvancedFilters && (
                        <span className="flex h-1.5 w-1.5 rounded-full bg-primary ring-2 ring-background" />
                    )}
                </button>

                <div className="h-6 w-px bg-border-subtle/10 hidden md:block" />

                <div className="flex flex-wrap gap-2">
                    {categories.length > 0 ? (
                        categories.map(cat => (
                            <button
                                key={cat.slug}
                                onClick={() => onCategoryChange(cat.slug)}
                                className={cn(
                                    "px-4 py-2 rounded-lg text-xs font-bold transition-all border select-none",
                                    currentCategorySlug === cat.slug
                                        ? "bg-secondary text-foreground border-border-subtle/10 shadow-inner"
                                        : "text-muted-foreground border-transparent hover:text-foreground hover:bg-secondary/50"
                                )}
                            >
                                {cat.name}
                            </button>
                        ))
                    ) : (
                        Array.from({ length: 5 }).map((_, i) => (
                            <div key={i} className="h-8 w-16 bg-secondary/30 rounded-lg animate-pulse" />
                        ))
                    )}
                </div>
            </div>

            <div className="flex items-center justify-between md:justify-end gap-4 w-full md:w-auto">
                 <span className="text-xs font-mono text-muted-foreground uppercase tracking-widest hidden sm:inline-block">
                    {isLoading ? (
                        <span className="animate-pulse">Searching...</span>
                    ) : (
                        <span>{totalResults} ITEMS FOUND</span>
                    )}
                </span>

                <div className="flex gap-1 bg-secondary/30 p-1 rounded-lg border border-border-subtle/5">
                    {["latest", "rating", "trending"].map(sortKey => (
                        <button
                            key={sortKey}
                            onClick={() => onSortChange(sortKey)}
                            className={cn(
                                "text-[10px] px-3 py-1.5 rounded-md transition-all font-bold",
                                currentSort === sortKey
                                    ? "bg-background text-foreground shadow-sm"
                                    : "text-muted-foreground hover:text-foreground hover:bg-background/50"
                            )}
                        >
                            {sortKey === "latest" ? "最新" : sortKey === "rating" ? "高分" : "热门"}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/explore/ExploreGrid.tsx">
"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Search } from "lucide-react";
import { MovieCard } from "@/components/features/MovieCard";
import { MovieCard as MovieCardType } from "@/lib/types";

interface ExploreGridProps {
    isLoading: boolean;
    results: MovieCardType[];
    query: string;
    onClearAll: () => void;
}

export function ExploreGrid({ isLoading, results, query, onClearAll }: ExploreGridProps) {
    // 骨架屏：密度也需要匹配新的 Grid
    if (isLoading) {
        return (
            <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-3 md:gap-4">
                {Array.from({ length: 21 }).map((_, i) => (
                    <div key={i} className="aspect-[2/3] bg-white/5 rounded-lg border border-white/5 animate-pulse" />
                ))}
            </div>
        );
    }

    return (
        <motion.div
            layout
            // 核心修改：更密集的 Grid 系统
            // 移动端 3 列，平板 4-5 列，桌面 6-7 列
            className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-x-3 gap-y-6 md:gap-x-4 md:gap-y-8"
        >
            <AnimatePresence mode="popLayout">
                {results.length > 0 ? (
                    results.map((movie, idx) => (
                        <motion.div
                            layout
                            key={movie.id}
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0, scale: 0.9 }}
                            transition={{ duration: 0.2, delay: 0 }} // 移除延迟或极短延迟，因为元素太多了
                        >
                            <MovieCard movie={movie} index={idx} />
                        </motion.div>
                    ))
                ) : (
                    <div className="col-span-full py-32 flex flex-col items-center justify-center text-center opacity-0 animate-in fade-in duration-700 border border-dashed border-white/5 rounded-3xl bg-white/[0.01]">
                        <div className="w-20 h-20 bg-gradient-to-tr from-white/5 to-white/10 rounded-full flex items-center justify-center mb-6 border border-white/5">
                            <Search className="w-8 h-8 text-neutral-600" />
                        </div>
                        <h3 className="text-lg font-bold text-white mb-2">信号丢失</h3>
                        <p className="text-neutral-500 max-w-xs mx-auto mb-8 text-sm">
                            {query ? `未侦测到与 "${query}" 匹配的信号。` : "当前坐标系下无内容。"}
                        </p>
                        <button
                            onClick={onClearAll}
                            className="px-6 py-2.5 bg-white text-black text-xs font-bold rounded-full hover:bg-neutral-200 transition-colors"
                        >
                            重置雷达
                        </button>
                    </div>
                )}
            </AnimatePresence>
        </motion.div>
    );
}
</file>

<file path="src/components/features/explore/ExploreHeader.tsx">
"use client";

import { Search as SearchIcon, X, Telescope } from "lucide-react";

interface ExploreHeaderProps {
    canSearch: boolean;
    query: string;
    setQuery: (val: string) => void;
    currentCategoryName: string;
}

export function ExploreHeader({
                                  canSearch,
                                  query,
                                  setQuery,
                                  currentCategoryName
                              }: ExploreHeaderProps) {
    if (canSearch) {
        return (
            <div className="relative group max-w-3xl animate-in fade-in slide-in-from-top-2 duration-500">
                <SearchIcon className="absolute left-0 top-1/2 -translate-y-1/2 w-6 h-6 text-muted-foreground transition-colors group-focus-within:text-primary" />
                <input
                    type="text"
                    placeholder={`在${currentCategoryName}中搜索...`}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    className="w-full bg-transparent border-b border-border-subtle/10 py-4 pl-10 text-xl md:text-3xl font-bold text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary transition-colors"
                    autoFocus={false}
                />
                {query && (
                    <button
                        onClick={() => setQuery("")}
                        className="absolute right-0 top-1/2 -translate-y-1/2 p-2 text-muted-foreground hover:text-foreground transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                )}
            </div>
        );
    }

    return (
        <div className="py-4 flex items-center gap-3 animate-in fade-in slide-in-from-top-2 duration-500">
            <div className="p-2 rounded-xl bg-secondary border border-border-subtle/10 text-muted-foreground">
                <Telescope className="w-8 h-8" />
            </div>
            <div>
                <h1 className="text-3xl font-bold text-foreground tracking-tight">
                    浏览 {currentCategoryName}
                </h1>
                <p className="text-xs text-muted-foreground mt-1">
                    登录并激活账号以解锁全文搜索功能
                </p>
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/FeaturedBento.tsx">
"use client";

import { MovieCard } from "@/lib/types";
import { SpotlightSection } from "./bento/SpotlightSection";
import { TrendingSection } from "./bento/TrendingSection";

interface FeaturedBentoProps {
    spotlights: MovieCard[];
    trending: MovieCard[];
}

export function FeaturedBento({ spotlights, trending }: FeaturedBentoProps) {
    return (
        <section className="w-full pt-24 md:pt-32 px-4 md:px-8 pb-6 max-w-[1800px] mx-auto">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:h-[620px]">
                <SpotlightSection movies={spotlights} />
                <TrendingSection movies={trending} />
            </div>
        </section>
    );
}
</file>

<file path="src/components/features/movie/MovieInfoComponents.tsx">
import React from "react";
import { cn } from "@/lib/utils";
import { ExternalLink } from "lucide-react";

interface BadgeProps {
    children: React.ReactNode;
    className?: string;
}

export function Badge({ children, className }: BadgeProps) {
    return (
        <span className={cn(
            "inline-flex items-center px-3 py-1 rounded-lg text-[10px] md:text-xs font-bold border uppercase tracking-wider backdrop-blur-md transition-colors",
            "bg-card/50 text-muted-foreground border-border-subtle/10",
            className
        )}>
            {children}
        </span>
    );
}

interface StatCardProps {
    label: string;
    value: string;
    icon: React.ReactNode;
    subValue: string;
    highlight?: boolean;
}

export function StatCard({ label, value, icon, subValue, highlight = false }: StatCardProps) {
    return (
        <div className={cn(
            "flex flex-col p-4 rounded-2xl border transition-all duration-300 group",
            highlight
                ? "bg-amber-500/5 border-amber-500/20 hover:bg-amber-500/10"
                : "bg-card/40 border-border-subtle/10 hover:bg-card/60 hover:border-border-subtle/20"
        )}>
            <div className="flex items-center justify-between mb-3">
                <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">{label}</span>
                <div className={cn(
                    "p-1.5 rounded-lg transition-transform group-hover:scale-110",
                    highlight ? "bg-amber-500/10" : "bg-secondary/50 text-foreground"
                )}>
                    {icon}
                </div>
            </div>
            <div className="mt-auto">
                <div className={cn(
                    "text-xl md:text-2xl font-black tracking-tight truncate",
                    highlight ? "text-amber-500" : "text-foreground"
                )}>
                    {value}
                </div>
                <div className="text-[10px] text-muted-foreground font-mono mt-0.5 truncate">
                    {subValue}
                </div>
            </div>
        </div>
    );
}

interface ExternalLinkBadgeProps {
    href: string;
    label: string;
    color: string;
}

export function ExternalLinkBadge({ href, label, color }: ExternalLinkBadgeProps) {
    return (
        <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-full border transition-all hover:scale-105 active:scale-95 group",
                color
            )}
        >
            <span className="text-xs font-bold">{label}</span>
            <ExternalLink className="w-3 h-3 opacity-70 group-hover:opacity-100" />
        </a>
    );
}
</file>

<file path="src/components/features/movie/MovieStructuredData.tsx">
import React from "react";
import { MovieDetail } from "@/lib/types";
import {
    generateBreadcrumbSchema,
    generateFAQSchema,
    generateMovieSchema,
    generateVideoObjectSchema
} from "@/lib/seo";

interface MovieStructuredDataProps {
    movie: MovieDetail;
}

export function MovieStructuredData({ movie }: MovieStructuredDataProps) {
    const movieSchema = generateMovieSchema(movie);
    const videoObjectSchema = generateVideoObjectSchema(movie);
    const faqSchema = generateFAQSchema(movie);

    const breadcrumbItems = [
        { name: "首页", url: "/" },
        {
            name: movie.categoryName || "探索",
            url: `/explore?type=${movie.categoryName === '剧集' ? 'series' : 'movie'}`
        }
    ];

    if (movie.genres && movie.genres.length > 0) {
        breadcrumbItems.push({
            name: movie.genres[0],
            url: `/explore?genre=${encodeURIComponent(movie.genres[0])}`
        });
    }

    breadcrumbItems.push({ name: movie.title, url: `/movie/${movie.id}` });

    const breadcrumbSchema = generateBreadcrumbSchema(breadcrumbItems);

    return (
        <>
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(movieSchema) }}
            />
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbSchema) }}
            />
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(videoObjectSchema) }}
            />
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{ __html: JSON.stringify(faqSchema) }}
            />
        </>
    );
}
</file>

<file path="src/components/features/profile/StatCard.tsx">
"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { ReactNode } from "react";

interface StatCardProps {
    label: string;
    value: string | number;
    icon: ReactNode;
    color: string;
    borderColor: string;
}

export function StatCard({ label, value, icon, color, borderColor }: StatCardProps) {
    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ y: -5, scale: 1.02 }}
            className={cn(
                "bg-card/50 border border-border-subtle/10 rounded-2xl p-6 relative overflow-hidden group transition-all duration-300 shadow-lg backdrop-blur-sm",
                borderColor
            )}
        >
            <div className={`p-3 rounded-xl bg-gradient-to-br ${color} text-foreground shadow-sm mb-4 w-fit`}>
                {icon}
            </div>

            <div>
                <h3 className="text-3xl font-black text-foreground tabular-nums tracking-tighter">
                    {value !== undefined && value !== null ? value : '0'}
                </h3>
                <p className="text-xs text-muted-foreground font-bold uppercase tracking-widest mt-1">
                    {label}
                </p>
            </div>

            <div className={`absolute -right-4 -bottom-4 w-24 h-24 bg-gradient-to-br ${color} blur-[60px] opacity-0 group-hover:opacity-40 transition-opacity duration-500`} />
        </motion.div>
    );
}
</file>

<file path="src/components/features/resource/EpisodeGrid.tsx">
"use client";

import { Episode } from "@/lib/types";
import { cn } from "@/lib/utils";
import { motion } from "framer-motion";
import { cleanEpisodeTitle } from "./utils";

interface EpisodeGridProps {
    episodes: Episode[];
    isNative: boolean;
    onPlay: (id: string) => void;
}

export function EpisodeGrid({ episodes, isNative, onPlay }: EpisodeGridProps) {
    return (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8 gap-3">
            {episodes.map((ep, idx) => {
                const cleanTitle = cleanEpisodeTitle(ep.title);
                return (
                    <motion.button
                        key={ep.id}
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: Math.min(idx * 0.005, 0.3) }}
                        onClick={() => onPlay(ep.id)}
                        className={cn(
                            "group relative flex items-center justify-center h-12 border rounded-lg transition-all overflow-hidden active:scale-95",
                            isNative
                                ? "bg-card border-border-subtle/10 hover:border-primary/50 hover:bg-secondary/80"
                                : "bg-card border-border-subtle/10 hover:border-border-subtle/30 hover:bg-secondary/80"
                        )}
                    >
                        <span className={cn(
                            "relative z-10 text-xs font-medium truncate px-2 transition-colors select-none",
                            isNative
                                ? "text-muted-foreground group-hover:text-primary font-mono"
                                : "text-muted-foreground group-hover:text-foreground"
                        )}>
                            {cleanTitle}
                        </span>

                        <div className={cn(
                            "absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none",
                            isNative
                                ? "bg-gradient-to-br from-primary/10 via-transparent to-transparent"
                                : "bg-gradient-to-br from-foreground/5 via-transparent to-transparent"
                        )} />
                    </motion.button>
                );
            })}
        </div>
    );
}
</file>

<file path="src/components/features/search/FilterModal.tsx">
"use client";

import { useState, useMemo, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { X, Search, Check } from "lucide-react";
import { cn } from "@/lib/utils";

interface FilterModalProps {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    options: string[];
    currentValue: string;
    onSelect: (value: string) => void;
    formatLabel?: (value: string) => string;
}

export function FilterModal({
                                isOpen,
                                onClose,
                                title,
                                options,
                                currentValue,
                                onSelect,
                                formatLabel = (s) => s
                            }: FilterModalProps) {
    const [searchQuery, setSearchQuery] = useState("");

    // 当弹窗打开时，重置搜索，并锁定背景滚动
    useEffect(() => {
        if (isOpen) {
            setSearchQuery("");
            document.body.style.overflow = "hidden";
        } else {
            document.body.style.overflow = "unset";
        }
        return () => {
            document.body.style.overflow = "unset";
        };
    }, [isOpen]);

    // 过滤选项
    const filteredOptions = useMemo(() => {
        if (!searchQuery.trim()) return options;
        return options.filter(opt =>
            formatLabel(opt).toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [options, searchQuery, formatLabel]);

    // 键盘 ESC 关闭支持
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape" && isOpen) onClose();
        };
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [isOpen, onClose]);

    return (
        <AnimatePresence>
            {isOpen && (
                <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6">
                    {/* 背景遮罩 */}
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={onClose}
                        className="fixed inset-0 bg-black/60 backdrop-blur-sm"
                    />

                    {/* 弹窗主体 - 使用 bg-card */}
                    <motion.div
                        initial={{ opacity: 0, scale: 0.95, y: 20 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.95, y: 20 }}
                        className="relative w-full max-w-4xl bg-card border border-border-subtle/10 rounded-2xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden"
                    >
                        {/* 顶部：标题 + 搜索 */}
                        <div className="flex-none p-5 border-b border-border-subtle/10 space-y-4 bg-card z-10">
                            <div className="flex items-center justify-between">
                                <h3 className="text-xl font-bold text-foreground flex items-center gap-2">
                                    选择{title}
                                    <span className="text-xs font-normal text-muted-foreground bg-secondary px-2 py-0.5 rounded-full">
                                        共 {options.length} 项
                                    </span>
                                </h3>
                                <button
                                    onClick={onClose}
                                    className="p-2 rounded-full hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors"
                                >
                                    <X className="w-5 h-5" />
                                </button>
                            </div>

                            <div className="relative">
                                <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                                <input
                                    type="text"
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    placeholder={`搜索${title}...`}
                                    className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl py-3 pl-10 pr-4 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all"
                                    autoFocus
                                />
                            </div>
                        </div>

                        {/* 内容区域：网格列表 */}
                        <div className="flex-1 overflow-y-auto p-5 custom-scrollbar bg-card">
                            {filteredOptions.length > 0 ? (
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
                                    {filteredOptions.map((opt) => {
                                        const isSelected = currentValue === opt;
                                        return (
                                            <button
                                                key={opt}
                                                onClick={() => {
                                                    onSelect(opt);
                                                    onClose();
                                                }}
                                                className={cn(
                                                    "group relative flex items-center justify-between px-4 py-3 rounded-xl text-sm font-medium transition-all text-left",
                                                    isSelected
                                                        ? "bg-primary text-primary-foreground shadow-lg shadow-primary/30 ring-1 ring-primary"
                                                        : "bg-secondary/30 text-muted-foreground hover:bg-secondary hover:text-foreground border border-transparent hover:border-border-subtle/10"
                                                )}
                                            >
                                                <span className="truncate mr-2">{formatLabel(opt)}</span>
                                                {isSelected && <Check className="w-4 h-4 flex-shrink-0" />}
                                            </button>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                                    <Search className="w-10 h-10 mb-4 opacity-20" />
                                    <p>未找到 &#34;{searchQuery}&#34;</p>
                                </div>
                            )}
                        </div>

                        {/* 底部：辅助信息 */}
                        <div className="flex-none p-3 border-t border-border-subtle/10 bg-secondary/10 text-center">
                            <p className="text-[10px] text-muted-foreground">
                                支持滚动查看 • 点击即选中
                            </p>
                        </div>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
}
</file>

<file path="src/components/layout/navbar/MobileMenu.tsx">
"use client";

import { motion } from "framer-motion";
import Link from "next/link";
import { User, LogOut } from "lucide-react";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { User as UserType } from "@/lib/types";
import { cn } from "@/lib/utils";

interface MobileMenuProps {
    isOpen: boolean;
    user: UserType | null;
    isLoading: boolean;
    onLogout: () => void;
    navItems: { name: string; href: string; icon: React.ReactNode }[];
    checkActive: (href: string) => boolean;
}

export function MobileMenu({ isOpen, user, isLoading, onLogout, navItems, checkActive }: MobileMenuProps) {
    if (!isOpen) return null;

    return (
        <motion.div
            initial={{ opacity: 0, scale: 0.98, y: -20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.98, y: -20 }}
            transition={{ duration: 0.3, ease: [0.32, 0.72, 0, 1] }}
            className="fixed inset-0 z-40 bg-background/95 backdrop-blur-3xl pt-28 px-6 lg:hidden flex flex-col"
        >
            <div className="absolute top-0 right-0 w-64 h-64 bg-primary/10 blur-[100px] rounded-full pointer-events-none" />

            <nav className="flex flex-col space-y-2 relative z-10">
                {navItems.map((item, idx) => {
                    const isActive = checkActive(item.href);
                    return (
                        <motion.div
                            key={item.name}
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: 0.05 + idx * 0.05 }}
                        >
                            <Link
                                href={item.href}
                                className={cn(
                                    "group flex items-center justify-between p-4 rounded-2xl transition-all",
                                    isActive
                                        ? "bg-secondary text-foreground"
                                        : "hover:bg-secondary/50 text-muted-foreground hover:text-foreground"
                                )}
                            >
                                <span className="text-xl font-bold tracking-tight flex items-center gap-3">
                                    {item.icon}
                                    {item.name}
                                </span>
                                {isActive && <div className="w-2 h-2 bg-primary rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)]" />}
                            </Link>
                        </motion.div>
                    );
                })}
            </nav>

            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.4 }}
                className="mt-auto pb-10 border-t border-border-subtle/10 pt-6 relative z-10"
            >
                {!isLoading && user ? (
                    <div className="bg-secondary/20 p-4 rounded-2xl border border-border-subtle/10">
                        <div className="flex items-center gap-4 mb-4">
                            <div className="w-12 h-12 rounded-full bg-secondary overflow-hidden">
                                <QuantumImage src={user.avatarUrl} alt={user.nickname} fill className="object-cover" />
                            </div>
                            <div>
                                <div className="text-foreground font-bold text-lg">{user.nickname}</div>
                                <div className="text-xs text-muted-foreground font-mono">ID: {user.id.slice(0, 8)}</div>
                            </div>
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                            <Link href="/profile" className="flex items-center justify-center gap-2 py-3 bg-card hover:bg-secondary rounded-xl text-sm font-bold text-foreground transition-colors border border-border-subtle/5">
                                <User className="w-4 h-4" /> 个人中心
                            </Link>
                            <button onClick={onLogout} className="flex items-center justify-center gap-2 py-3 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-xl text-sm font-bold transition-colors">
                                <LogOut className="w-4 h-4" /> 退出登录
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="grid grid-cols-2 gap-4">
                        <Link href="/login">
                            <button className="w-full py-4 bg-foreground text-background font-bold rounded-xl hover:bg-foreground/80 transition-colors">
                                登录
                            </button>
                        </Link>
                        <Link href="/signup">
                            <button className="w-full py-4 bg-secondary text-foreground font-bold rounded-xl border border-border-subtle/10 hover:bg-secondary/80 transition-colors">
                                注册
                            </button>
                        </Link>
                    </div>
                )}
            </motion.div>
        </motion.div>
    );
}
</file>

<file path="src/components/ui/CinematicGrain.tsx">
"use client";

import { useEffect, useRef } from "react";

export function CinematicGrain() {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        let width = canvas.width = window.innerWidth;
        let height = canvas.height = window.innerHeight;

        const resize = () => {
            width = canvas.width = window.innerWidth;
            height = canvas.height = window.innerHeight;
        };

        window.addEventListener("resize", resize);

        let frameId: number;

        const loop = () => {
            const w = width;
            const h = height;

            ctx.clearRect(0, 0, w, h);

            const idata = ctx.createImageData(w, h);
            const buffer32 = new Uint32Array(idata.data.buffer);
            const len = buffer32.length;

            for (let i = 0; i < len; i++) {
                if (Math.random() < 0.1) {
                    buffer32[i] = 0xffffffff;
                }
            }

            ctx.putImageData(idata, 0, 0);

            // 降低刷新频率，减少视觉疲劳 (从每帧刷新改为每 100ms)
            setTimeout(() => {
                frameId = requestAnimationFrame(loop);
            }, 100);
        };

        loop();

        return () => {
            window.removeEventListener("resize", resize);
            cancelAnimationFrame(frameId);
        };
    }, []);

    return (
        <canvas
            ref={canvasRef}
            className="pointer-events-none fixed inset-0 z-[9999] opacity-[0.03]"
            style={{ width: "100%", height: "100%" }}
        />
    );
}
</file>

<file path="src/components/ui/GlowButton.tsx">
"use client";

import { motion } from "framer-motion";
import { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface GlowButtonProps {
    children: ReactNode;
    onClick?: () => void;
    variant?: "primary" | "secondary" | "ghost";
    className?: string;
    icon?: ReactNode;
    disabled?: boolean;
}

export function GlowButton({
                               children,
                               onClick,
                               variant = "primary",
                               className,
                               icon,
                               disabled = false
                           }: GlowButtonProps) {
    const baseStyles = "relative flex items-center justify-center gap-2 px-6 py-3 rounded-full font-medium text-sm transition-all duration-300 overflow-hidden outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2";

    const variants = {
        primary: "bg-primary text-primary-foreground hover:bg-primary/90 shadow-lg shadow-primary/25 border border-primary/20",
        secondary: "bg-secondary/80 text-secondary-foreground backdrop-blur-md border border-border-subtle/10 hover:bg-secondary hover:border-border-subtle/20",
        ghost: "bg-transparent text-muted-foreground hover:text-foreground hover:bg-secondary/50"
    };

    return (
        <motion.button
            whileHover={disabled ? {} : { scale: 1.05 }}
            whileTap={disabled ? {} : { scale: 0.95 }}
            className={cn(
                baseStyles,
                variants[variant],
                disabled && "opacity-50 cursor-not-allowed pointer-events-none grayscale",
                className
            )}
            onClick={disabled ? undefined : onClick}
            disabled={disabled}
        >
            {icon && <span className="w-4 h-4">{icon}</span>}
            {children}
        </motion.button>
    );
}
</file>

<file path="src/hooks/useContentSearch.ts">
"use client";

import { useState, useCallback, useEffect, useMemo, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useDebounce } from "@/hooks/useDebounce";
import { contentApi } from "@/lib/api/content";
import { CategoryItem, MovieCard, PortalMetadata } from "@/lib/types";

// --- Types ---

export interface FilterState {
    category: string;
    sort: string;
    genre: string;
    area: string;
    year: string;
    language: string;
    keyword: string;
}

export interface UseContentSearchProps {
    initialResults: MovieCard[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
    pageSize?: number;
}

// --- Constants ---

export const DEFAULT_VALUE = "全部";
export const DEFAULT_CATEGORY_SLUG = "all";
export const DEFAULT_SORT = "latest";

const STATIC_CATEGORIES: CategoryItem[] = [
    { slug: "all", name: "全部" },
    { slug: "movie", name: "电影" },
    { slug: "series", name: "剧集" },
    { slug: "short-drama", name: "短剧" },
    { slug: "anime", name: "动漫" },
    { slug: "variety", name: "综艺" },
    { slug: "documentary", name: "纪录片" }
];

export interface FilterGroupConfig {
    key: keyof FilterState;
    label: string;
    options: string[];
}

// --- Hook Implementation ---

export function useContentSearch({
                                     initialResults,
                                     initialTotal,
                                     initialMetadata,
                                     initialParams,
                                     pageSize = 50
                                 }: UseContentSearchProps) {
    const pathname = usePathname();
    const router = useRouter();

    // 1. Core Data State
    const [results, setResults] = useState<MovieCard[]>(initialResults);
    const [totalResults, setTotalResults] = useState(initialTotal);
    const [metadata, setMetadata] = useState<PortalMetadata | null>(initialMetadata);
    const [isLoading, setIsLoading] = useState(false);

    // 2. Filter & Query State
    const [filters, setFilters] = useState<FilterState>(initialParams);
    const [query, setQuery] = useState(initialParams.keyword);
    const debouncedQuery = useDebounce(query, 600);

    // 3. Metadata Hydration (Client-side fallback)
    useEffect(() => {
        if (!initialMetadata) {
            contentApi.getMetadata().then(setMetadata).catch(console.error);
        }
    }, [initialMetadata]);

    // 4. Reset Data on Initial Props Change (External Navigation)
    // 当通过 Link 跳转或 URL 变化时，重置本地状态以匹配服务端数据
    useEffect(() => {
        setResults(initialResults);
        setTotalResults(initialTotal);
        setQuery(initialParams.keyword);
        setFilters(initialParams);
    }, [initialResults, initialTotal, initialParams]);

    // 5. Smart Options Generation
    const allCategories: CategoryItem[] = useMemo(() => {
        let list = [...STATIC_CATEGORIES];
        if (metadata?.categories && metadata.categories.length > 0) {
            list = [{ name: "全站", slug: "all" }, ...metadata.categories];
        }
        return list;
    }, [metadata]);

    const yearOptions = useMemo(() => {
        const currentYear = new Date().getFullYear();
        const years = [DEFAULT_VALUE];
        for (let i = currentYear + 1; i >= currentYear - 4; i--) {
            years.push(String(i));
        }
        const currentDecadeStart = Math.floor(currentYear / 10) * 10;
        for (let d = currentDecadeStart - 10; d >= 1990; d -= 10) {
            years.push(`${d}-${d + 9}`);
        }
        years.push("lt1990");
        return years;
    }, []);

    const filterGroups: FilterGroupConfig[] = useMemo(() => ([
        { key: "category", label: "分类", options: allCategories.map(c => c.slug) },
        { key: "genre", label: "题材", options: [DEFAULT_VALUE, ...(metadata?.genres || [])] },
        { key: "area", label: "地区", options: [DEFAULT_VALUE, ...(metadata?.areas || [])] },
        { key: "year", label: "年代", options: yearOptions },
        { key: "language", label: "语言", options: [DEFAULT_VALUE, ...(metadata?.languages || [])] },
    ]), [allCategories, metadata, yearOptions]);

    // 6. Search Execution
    const performSearch = useCallback(async (currentFilters: FilterState, searchQuery: string) => {
        setIsLoading(true);
        try {
            const res = await contentApi.explore({
                page: 0,
                size: pageSize,
                category: currentFilters.category,
                sort: currentFilters.sort,
                keyword: searchQuery,
                genre: currentFilters.genre === DEFAULT_VALUE ? undefined : currentFilters.genre,
                area: currentFilters.area === DEFAULT_VALUE ? undefined : currentFilters.area,
                year: currentFilters.year === DEFAULT_VALUE ? undefined : currentFilters.year,
                language: currentFilters.language === DEFAULT_VALUE ? undefined : currentFilters.language,
            });
            setResults(res.content || []);
            setTotalResults(res.totalElements ?? (res.content?.length || 0));
        } catch (err) {
            console.error("Search failed", err);
        } finally {
            setIsLoading(false);
        }
    }, [pageSize]);

    // 7. URL Sync & Trigger
    // 关键修复：正确处理路由跳转(Props Change)与用户交互(Interaction)的冲突
    const prevParamsRef = useRef(JSON.stringify(initialParams));

    useEffect(() => {
        const currentParamsStr = JSON.stringify(initialParams);

        // [CRITICAL FIX]: 检测是否是外部导航导致的更新
        // 如果 props (initialParams) 发生了变化，说明是路由跳转。
        // 此时 filters 状态可能还是旧的（因为 setState 是异步的）。
        // 我们必须阻断本次 Effect 的执行，防止用旧的 filters 覆盖新的 URL。
        if (currentParamsStr !== prevParamsRef.current) {
            prevParamsRef.current = currentParamsStr;
            return;
        }

        // 下面是处理用户在当前页面点击筛选器/搜索的逻辑
        const isInitialState =
            debouncedQuery === initialParams.keyword &&
            filters.category === initialParams.category &&
            filters.sort === initialParams.sort &&
            filters.genre === initialParams.genre &&
            filters.area === initialParams.area &&
            filters.year === initialParams.year &&
            filters.language === initialParams.language;

        // 如果状态与初始状态一致（即没有用户交互产生的变更），则不执行搜索和 URL 更新
        if (isInitialState) return;

        // 执行搜索
        performSearch(filters, debouncedQuery);

        // 更新 URL (仅在用户交互时)
        const params = new URLSearchParams();
        if (debouncedQuery) params.set("keyword", debouncedQuery);
        if (filters.category !== DEFAULT_CATEGORY_SLUG) params.set("type", filters.category);
        if (filters.genre !== DEFAULT_VALUE) params.set("genre", filters.genre);
        if (filters.area !== DEFAULT_VALUE) params.set("area", filters.area);
        if (filters.year !== DEFAULT_VALUE) params.set("year", filters.year);
        if (filters.language !== DEFAULT_VALUE) params.set("language", filters.language);
        if (filters.sort !== DEFAULT_SORT) params.set("sort", filters.sort);

        const newUrl = `${pathname}?${params.toString()}`;
        window.history.replaceState({ ...window.history.state, as: newUrl, url: newUrl }, "", newUrl);

    }, [debouncedQuery, filters, performSearch, pathname, initialParams]);

    // 8. Actions
    const handleFilterChange = (key: string, value: string) => {
        setFilters(prev => ({ ...prev, [key as keyof FilterState]: value }));
    };

    const resetFilters = (overrideDefaults?: Partial<FilterState>) => {
        setQuery("");
        const defaults: FilterState = {
            category: DEFAULT_CATEGORY_SLUG,
            sort: DEFAULT_SORT,
            genre: DEFAULT_VALUE,
            area: DEFAULT_VALUE,
            year: DEFAULT_VALUE,
            language: DEFAULT_VALUE,
            keyword: "",
            ...overrideDefaults
        };
        setFilters(defaults);
        // 重置时也需要更新 URL 状态，否则返回上一页可能有问题
        window.history.replaceState(null, "", pathname);
        performSearch(defaults, "");
    };

    // 9. Helpers
    const getDisplayOption = (key: string, val: string) => {
        if (key === "category") {
            const found = allCategories.find(c => c.slug === val);
            return found ? found.name : val;
        }
        if (key === "year") {
            if (val.includes("-")) return val.replace("-", "至") + "年";
            if (val.startsWith("lt")) return val.replace("lt", "") + "年以前";
            if (val === DEFAULT_VALUE) return val;
            return val + "年";
        }
        return val;
    };

    return {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        metadata,
        allCategories,
        filterGroups,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption,
    };
}
</file>

<file path="src/hooks/usePoW.ts">
"use client";

import { useState } from "react";
import { authApi } from "@/lib/api/auth";

export interface PoWVerification {
    id: string;
    nonce: string;
}

export function usePoW() {
    const [isCalculating, setIsCalculating] = useState(false);

    const solve = async (): Promise<PoWVerification> => {
        setIsCalculating(true);

        try {
            // 1. Get Challenge from Server
            const challenge = await authApi.getPoWChallenge();
            const { id, difficulty } = challenge;
            const targetPrefix = "0".repeat(difficulty);

            console.log(`[PoW] Challenge received. Difficulty: ${difficulty}`);

            // 2. Solve PoW using Web Crypto API
            const startCalc = performance.now(); // Start high-precision timer
            let nonce = 0;
            const encoder = new TextEncoder();

            while (true) {
                const input = id + nonce.toString();
                const data = encoder.encode(input);
                const hashBuffer = await crypto.subtle.digest("SHA-256", data);
                const hashArray = Array.from(new Uint8Array(hashBuffer));
                const hashHex = hashArray.map(b => b.toString(16).padStart(2, "0")).join("");

                if (hashHex.startsWith(targetPrefix)) {
                    const endCalc = performance.now();
                    const duration = (endCalc - startCalc).toFixed(2);

                    console.log(
                        `%c[PoW] Solved! 🛡️\n%cTime: ${duration}ms\nNonce: ${nonce}\nHash: ${hashHex.substring(0, 10)}...`,
                        "color: #4ade80; font-weight: bold; font-size: 12px;",
                        "color: #94a3b8;"
                    );

                    return { id, nonce: nonce.toString() };
                }

                nonce++;
                // Break loop if takes too long (safety valve)
                if (nonce > 5000000) throw new Error("PoW calculation timeout");
            }
        } finally {
            setIsCalculating(false);
        }
    };

    return {
        solve,
        isCalculating
    };
}
</file>

<file path="src/lib/context/AuthContext.tsx">
"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useToast } from "@/lib/context/ToastContext";
import { authApi } from "@/lib/api/auth";
import { User } from '@/lib/types';
interface AuthContextType {
    user: User | null;
    isLoading: boolean;
    isStealthMode: boolean;
    login: (email: string, password: string) => Promise<void>;
    signup: (name: string, email: string, password: string) => Promise<void>;
    updateUser: (user: User) => void;
    logout: () => void;
    toggleStealthMode: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isStealthMode, setIsStealthMode] = useState(false);

    const router = useRouter();
    const { addToast } = useToast();

    // 初始化：检查 Token 和 本地偏好设置
    useEffect(() => {
        const initAuth = async () => {
            // 1. 恢复隐身模式设置
            const storedStealth = localStorage.getItem("huawai_stealth_mode");
            if (storedStealth === "true") {
                setIsStealthMode(true);
            }

            // 2. 恢复用户 Session
            const token = localStorage.getItem("huawai_token");
            if (!token) {
                setIsLoading(false);
                return;
            }

            try {
                const userData = await authApi.getMe(token);
                setUser({ ...userData, token });
            } catch (e) {
                console.error("Auth check failed", e);
                localStorage.removeItem("huawai_token");
                setUser(null);
            } finally {
                setIsLoading(false);
            }
        };

        initAuth();
    }, []);

    const login = async (email: string, password: string) => {
        // 占位
    };

    const signup = async (name: string, email: string, password: string) => {
        // 占位
    };

    const updateUser = (userData: User) => {
        setUser(userData);
        if (userData.token) {
            localStorage.setItem("huawai_token", userData.token);
        }
    };

    const logout = () => {
        setUser(null);
        localStorage.removeItem("huawai_token");
        addToast("已安全登出", "info");
        router.push("/login");
    };

    const toggleStealthMode = () => {
        const newValue = !isStealthMode;

        setIsStealthMode(newValue);
        localStorage.setItem("huawai_stealth_mode", String(newValue));

        if (newValue) {
            addToast("已开启隐身模式，暂停记录历史", "info");
        } else {
            addToast("已关闭隐身模式，恢复记录历史", "success");
        }
    };

    return (
        <AuthContext.Provider value={{
            user,
            isLoading,
            isStealthMode,
            login: async () => {},
            signup: async () => {},
            updateUser,
            logout,
            toggleStealthMode
        }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}
</file>

<file path="src/lib/context/ThemeContext.tsx">
"use client";

import * as React from "react";
import { ThemeProvider as NextThemesProvider, useTheme as useNextTheme } from "next-themes";

export function ThemeProvider({ children, ...props }: React.ComponentProps<typeof NextThemesProvider>) {
    return (
        <NextThemesProvider
            attribute="class"
            defaultTheme="system"
            enableSystem
            disableTransitionOnChange
            storageKey="huawai-theme"
            {...props}
        >
            {children}
        </NextThemesProvider>
    );
}

export const useTheme = useNextTheme;
</file>

<file path="package.json">
{
  "name": "huawai",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev -p 3001",
    "build": "next build",
    "start": "next start",
    "lint": "eslint"
  },
  "dependencies": {
    "@headlessui/react": "^2.2.9",
    "@vidstack/react": "^1.12.13",
    "clsx": "^2.1.1",
    "crypto-js": "^4.2.0",
    "framer-motion": "^12.0.0",
    "hls.js": "^1.6.15",
    "lucide-react": "^0.474.0",
    "next": "16.1.1",
    "next-themes": "^0.4.6",
    "react": "19.2.3",
    "react-dom": "19.2.3",
    "tailwind-merge": "^2.6.0",
    "vidstack": "^1.12.13"
  },
  "devDependencies": {
    "@tailwindcss/postcss": "^4",
    "@types/crypto-js": "^4.2.2",
    "@types/node": "^20",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "babel-plugin-react-compiler": "1.0.0",
    "eslint": "^9",
    "eslint-config-next": "16.1.1",
    "tailwindcss": "^4",
    "typescript": "^5"
  }
}
</file>

<file path="src/app/forgot-password/page.tsx">
"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, ArrowLeft, CheckCircle2, User, ShieldCheck } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { usePoW } from "@/hooks/usePoW";
import { getErrorMessage } from "@/lib/utils";

export default function ForgotPasswordPage() {
    const { addToast } = useToast();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const [isLoading, setIsLoading] = useState(false);
    const [isSubmitted, setIsSubmitted] = useState(false);
    const [formData, setFormData] = useState({
        email: "",
        nickname: ""
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        try {
            setIsLoading(true);

            const verification = await solve();

            await authApi.forgotPassword(formData.email, formData.nickname, verification);

            setIsSubmitted(true);
            addToast("重置邮件已发送", "success");

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "请求失败，请检查输入信息", "error");
        } finally {
            setIsLoading(false);
        }
    };

    const isBusy = isLoading || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-0 left-1/4 w-[40%] h-[40%] bg-primary/5 blur-[120px] rounded-full" />
                <div className="absolute bottom-0 right-1/4 w-[40%] h-[40%] bg-blue-500/10 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.5 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <span className="text-foreground font-bold text-2xl">?</span>
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">忘记密码?</h1>
                    <p className="text-muted-foreground">验证您的身份以获取重置链接。</p>
                </div>

                <AnimatePresence mode="wait">
                    {!isSubmitted ? (
                        <motion.form
                            key="form"
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            exit={{ opacity: 0, x: -20 }}
                            onSubmit={handleSubmit}
                            className="space-y-6"
                        >
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">注册邮箱</label>
                                <div className="relative group">
                                    <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="email"
                                        required
                                        value={formData.email}
                                        onChange={(e) => setFormData({...formData, email: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="name@example.com"
                                    />
                                </div>
                            </div>

                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">账号昵称</label>
                                <div className="relative group">
                                    <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="text"
                                        required
                                        value={formData.nickname}
                                        onChange={(e) => setFormData({...formData, nickname: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="请输入注册时的昵称"
                                    />
                                </div>
                                <p className="text-[10px] text-muted-foreground/60 ml-1">为了账号安全，我们需要验证您的昵称是否匹配。</p>
                            </div>

                            <div className="pt-2">
                                <GlowButton
                                    className="w-full py-4 text-base"
                                    icon={isBusy ? <Loader2 className="w-5 h-5 animate-spin" /> : <Mail className="w-5 h-5" />}
                                    disabled={isBusy}
                                >
                                    {isPoWCalculating ? "安全环境计算中..." : isLoading ? "验证中..." : "发送重置链接"}
                                </GlowButton>
                            </div>
                        </motion.form>
                    ) : (
                        <motion.div
                            key="success"
                            initial={{ opacity: 0, scale: 0.9 }}
                            animate={{ opacity: 1, scale: 1 }}
                            className="bg-green-500/10 border border-green-500/20 rounded-2xl p-8 text-center"
                        >
                            <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
                                <CheckCircle2 className="w-8 h-8 text-green-500" />
                            </div>
                            <h3 className="text-xl font-bold text-foreground mb-2">邮件已发送</h3>
                            <p className="text-muted-foreground text-sm mb-6">
                                我们已向 <strong>{formData.email}</strong> 发送了重置指南。请查收您的收件箱（包括垃圾邮件文件夹）。
                            </p>
                            <button
                                onClick={() => setIsSubmitted(false)}
                                className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                            >
                                没收到？返回重试
                            </button>
                        </motion.div>
                    )}
                </AnimatePresence>

                <div className="mt-8 text-center">
                    <Link
                        href="/login"
                        className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors group"
                    >
                        <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-1" />
                        返回登录界面
                    </Link>
                </div>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>本站采用 PoW 算力验证防御技术</span>
                </div>
            </motion.div>
        </div>
    );
}
</file>

<file path="src/app/topic/[...slug]/page.tsx">
import {Metadata} from "next";
import Link from "next/link";
import {contentApi} from "@/lib/api/content";
import {MovieCard} from "@/components/features/MovieCard";
import {SITE_NAME} from "@/lib/seo";
import {Download, HardDrive, Play} from "lucide-react";

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
    const title = `${coreKeyword}${modifierStr} 免费资源聚合 - ${SITE_NAME}`;

    return {
        title: title,
        description: `正在寻找《${coreKeyword}》的${modifiers.join("/")}资源？画外(Huawai)为您整理了${coreKeyword}的4K HDR、1080P、迅雷下载及在线观看线路。点击立即获取。`,
        keywords: [coreKeyword, ...modifiers, `${coreKeyword}下载`, `${coreKeyword}在线`, "资源聚合"],
        alternates: {
            canonical: `/topic/${slug.join("/")}`
        },
        robots: {
            index: true,
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
    let isFuzzy = false;

    if (!hasResults) {
        const trendingData = await contentApi.explore({sort: "trending", size: 12});
        displayMovies = trendingData.content || [];
        isFuzzy = true;
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

                        {/* 伪装的功能入口链接 (增强相关性) */}
                        {hasResults && (
                            <div className="flex flex-wrap gap-4 mt-6">
                                <FakeActionBtn icon={<Download className="w-4 h-4"/>} text="磁力链接检索"/>
                                <FakeActionBtn icon={<Play className="w-4 h-4"/>} text="在线线路检测"/>
                                <FakeActionBtn icon={<HardDrive className="w-4 h-4"/>} text="网盘资源聚合"/>
                            </div>
                        )}
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

function FakeActionBtn({icon, text}: { icon: React.ReactNode, text: string }) {
    return (
        <div
            className="flex items-center gap-2 px-4 py-2 bg-secondary/50 border border-border-subtle/10 rounded-lg text-sm text-muted-foreground select-none cursor-default opacity-80 hover:bg-secondary transition-colors">
            {icon}
            <span>{text}</span>
            <div className="w-2 h-2 rounded-full bg-green-500 ml-2 animate-pulse"/>
        </div>
    );
}
</file>

<file path="src/components/features/explore/ExploreFilterPanel.tsx">
"use client";

import { motion, AnimatePresence } from "framer-motion";
import { SlidersHorizontal, ChevronDown, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { DualRangeSlider } from "@/components/ui/DualRangeSlider";

export interface FilterGroup {
    key: string;
    label: string;
    options: string[];
    type?: "select" | "range";
    min?: number;
    max?: number;
}

interface ExploreFilterPanelProps {
    isOpen: boolean;
    filterGroups: FilterGroup[];
    getCurrentValue: (key: string) => string;
    onFilterChange: (key: string, value: string) => void;
    onShowMore: (groupKey: string) => void;
    onClearAll: () => void;
}

export function ExploreFilterPanel({
                                       isOpen,
                                       filterGroups,
                                       getCurrentValue,
                                       onFilterChange,
                                       onShowMore,
                                       onClearAll
                                   }: ExploreFilterPanelProps) {

    const parseRangeValue = (val: string, min: number, max: number): [number, number] => {
        if (!val || val === "全部") return [min, max];

        if (val.includes("-")) {
            const [s, e] = val.split("-").map(Number);
            return [s || min, e || max];
        }

        const single = Number(val);
        if (!isNaN(single) && single > 0) {
            return [single, single];
        }

        return [min, max];
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="overflow-hidden"
                >
                    <div className="bg-card border border-border-subtle/10 rounded-2xl p-6 space-y-8 relative overflow-hidden shadow-sm">
                        <div className="absolute top-0 right-0 p-4 opacity-10 pointer-events-none">
                            <SlidersHorizontal className="w-24 h-24 text-muted-foreground" />
                        </div>

                        {filterGroups.map((group) => {
                            if (group.key === "year") {
                                const currentYear = new Date().getFullYear() + 1;
                                const minYear = 1900;
                                const maxYear = currentYear;
                                const [currentStart, currentEnd] = parseRangeValue(
                                    getCurrentValue(group.key),
                                    minYear,
                                    maxYear
                                );

                                return (
                                    <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start">
                                        <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider w-16 pt-2 shrink-0">
                                            {group.label}
                                        </span>
                                        <div className="flex-1 w-full max-w-2xl pt-1 pr-4">
                                            <DualRangeSlider
                                                min={minYear}
                                                max={maxYear}
                                                value={[currentStart, currentEnd]}
                                                onChange={() => {}}
                                                onCommit={(val) => {
                                                    if (val[0] === minYear && val[1] === maxYear) {
                                                        onFilterChange(group.key, "全部");
                                                    }
                                                    else if (val[0] === val[1]) {
                                                        onFilterChange(group.key, String(val[0]));
                                                    }
                                                    else {
                                                        onFilterChange(group.key, `${val[0]}-${val[1]}`);
                                                    }
                                                }}
                                            />
                                        </div>
                                    </div>
                                );
                            }

                            const VISIBLE_LIMIT = 12;
                            const options = group.options;
                            const hasMore = options.length > VISIBLE_LIMIT;
                            const visibleOptions = options.slice(0, VISIBLE_LIMIT);
                            const currentVal = getCurrentValue(group.key);

                            return (
                                <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start">
                                    <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider w-16 pt-2 shrink-0">
                                        {group.label}
                                    </span>
                                    <div className="flex flex-wrap gap-2 flex-1">
                                        {visibleOptions.map((opt) => {
                                            const isSelected = currentVal === opt;
                                            return (
                                                <button
                                                    key={opt}
                                                    onClick={() => onFilterChange(group.key, opt)}
                                                    className={cn(
                                                        "px-3 py-1.5 rounded-lg text-xs transition-all border",
                                                        isSelected
                                                            ? "bg-primary border-primary text-primary-foreground shadow-lg shadow-primary/30"
                                                            : "bg-secondary/30 border-border-subtle/5 text-muted-foreground hover:bg-secondary hover:text-foreground hover:border-border-subtle/10"
                                                    )}
                                                >
                                                    {opt}
                                                </button>
                                            );
                                        })}
                                        {hasMore && (
                                            <button
                                                onClick={() => onShowMore(group.key)}
                                                className="px-3 py-1.5 rounded-lg text-xs bg-secondary/30 border border-border-subtle/5 text-primary hover:bg-secondary hover:text-foreground transition-all flex items-center gap-1"
                                            >
                                                更多 <ChevronDown className="w-3 h-3" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}

                        <div className="pt-4 border-t border-border-subtle/10 flex justify-end">
                            <button
                                onClick={onClearAll}
                                className="flex items-center gap-2 text-xs font-bold text-destructive hover:text-destructive/80 transition-colors px-4 py-2 hover:bg-destructive/5 rounded-lg"
                            >
                                <Trash2 className="w-3.5 h-3.5" />
                                重置所有条件
                            </button>
                        </div>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    );
}
</file>

<file path="src/components/features/history/HistoryItem.tsx">
"use client";

import { motion, useMotionValue, PanInfo } from "framer-motion";
import { Play, X, Film, Disc, Loader2, CheckCircle2, Clock, Trash2 } from "lucide-react";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { cn, formatDuration } from "@/lib/utils";
import { InteractionRecord } from "@/lib/types";
import { useState } from "react";

interface HistoryItemProps {
    rec: InteractionRecord;
    isPlaying: boolean;
    onPlay: (rec: InteractionRecord) => void;
    onDelete: (id: string) => void;
    index: number;
}

export function HistoryItem({ rec, isPlaying, onPlay, onDelete, index }: HistoryItemProps) {
    const x = useMotionValue(0);
    const [isHovered, setIsHovered] = useState(false);
    const [isDragging, setIsDragging] = useState(false);

    const progressPercent = rec.durationSeconds > 0
        ? Math.min(100, Math.round((rec.progressSeconds / rec.durationSeconds) * 100))
        : 0;

    const remaining = rec.durationSeconds - rec.progressSeconds;
    const isFinished = progressPercent > 90;

    const handleDragEnd = (_: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
        if (info.offset.x < -100) {
            onDelete(rec.id);
        }
        setTimeout(() => setIsDragging(false), 150);
    };

    const handleClick = (e: React.MouseEvent) => {
        if (isDragging) {
            e.preventDefault();
            e.stopPropagation();
            return;
        }
        onPlay(rec);
    };

    return (
        <motion.div
            layout
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -50, height: 0, marginBottom: 0 }}
            transition={{ duration: 0.3, delay: index * 0.05 }}
            className="relative pl-8 group"
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            {/* 时间轴竖线：使用语义化 border-subtle/10 */}
            <div className="absolute left-0 top-0 bottom-0 w-px bg-gradient-to-b from-border-subtle/20 via-border-subtle/10 to-transparent group-last:bottom-auto group-last:h-1/2" />

            {/* Timeline Dot */}
            <div className={cn(
                "absolute left-[-4px] top-8 w-[9px] h-[9px] rounded-full border-2 transition-all duration-300 z-10",
                isFinished
                    ? "bg-background border-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]"
                    : isHovered
                        ? "bg-primary border-primary shadow-[0_0_10px_rgba(99,102,241,0.8)] scale-125"
                        : "bg-background border-border-subtle/30"
            )} />

            <motion.div
                style={{ x }}
                drag="x"
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={{ left: 0.5, right: 0 }}
                onDragStart={() => setIsDragging(true)}
                onDragEnd={handleDragEnd}
                onClick={handleClick}
                className="relative overflow-hidden rounded-2xl bg-card border border-border-subtle/10 hover:border-border-subtle/30 transition-all cursor-pointer group/card active:cursor-grabbing shadow-sm hover:shadow-md"
            >
                <div className={cn(
                    "absolute inset-0 opacity-0 transition-opacity duration-500 pointer-events-none",
                    isPlaying ? "opacity-100 bg-primary/5" : "group-hover/card:opacity-100 bg-secondary/30"
                )} />

                <div className="flex gap-4 p-4 relative z-10 pointer-events-none">
                    {/* Thumbnail */}
                    <div className="relative w-32 md:w-40 aspect-video rounded-lg overflow-hidden flex-shrink-0 bg-neutral-900 shadow-lg ring-1 ring-border-subtle/10 group-hover/card:ring-border-subtle/20 transition-all">
                        <QuantumImage src={rec.coverImageUrl} alt={rec.title} fill className="object-cover transition-transform duration-700 group-hover/card:scale-110" />

                        <div className={cn(
                            "absolute inset-0 flex items-center justify-center transition-all duration-300 bg-black/40 backdrop-blur-[2px]",
                            isPlaying || isHovered ? "opacity-100" : "opacity-0"
                        )}>
                            <div className={cn(
                                "w-10 h-10 rounded-full flex items-center justify-center transition-all duration-300 shadow-2xl border border-white/10",
                                isPlaying
                                    ? "bg-primary text-white scale-110 shadow-[0_0_20px_rgba(99,102,241,0.5)]"
                                    : "bg-white/20 text-white group-hover/card:bg-white group-hover/card:text-black group-hover/card:scale-110"
                            )}>
                                {isPlaying ? <Loader2 className="w-5 h-5 animate-spin" /> : <Play className="w-5 h-5 fill-current" />}
                            </div>
                        </div>

                        <div className="absolute bottom-0 left-0 right-0 h-1 bg-black/50">
                            <motion.div
                                initial={{ width: 0 }}
                                animate={{ width: `${progressPercent}%` }}
                                transition={{ duration: 1, ease: "circOut" }}
                                className={cn(
                                    "h-full shadow-[0_0_10px_currentColor]",
                                    isFinished ? "bg-green-500 text-green-500" : "bg-primary text-primary"
                                )}
                            />
                        </div>
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0 flex flex-col justify-center py-1">
                        <div className="flex justify-between items-start mb-1">
                            <h3 className={cn(
                                "text-base md:text-lg font-bold truncate pr-8 transition-colors",
                                isPlaying ? "text-primary" : "text-foreground group-hover/card:text-primary/80"
                            )}>
                                {rec.title}
                            </h3>

                            <button
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    onDelete(rec.id);
                                }}
                                className="hidden md:flex p-2 -mt-2 -mr-2 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-full transition-all opacity-0 group-hover/card:opacity-100 pointer-events-auto"
                                title="删除记录"
                                onPointerDown={(e) => e.stopPropagation()}
                            >
                                <X className="w-4 h-4" />
                            </button>
                        </div>

                        <div className="flex items-center gap-2 mb-3">
                            {rec.episodeName ? (
                                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[10px] font-medium bg-secondary border border-border-subtle/10 text-muted-foreground">
                                    <Film className="w-3 h-3" />
                                    {rec.episodeName}
                                </span>
                            ) : (
                                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[10px] font-medium bg-secondary border border-border-subtle/10 text-muted-foreground">
                                    <Disc className="w-3 h-3" />
                                    正片
                                </span>
                            )}
                            <span className="text-[10px] text-muted-foreground/70 font-mono">
                                {rec.lastWatchedAt.split('T')[0].replace(/-/g, '.')}
                            </span>
                        </div>

                        <div className="mt-auto flex items-center gap-3">
                            {isFinished ? (
                                <span className="flex items-center gap-1.5 text-xs text-green-500 font-bold bg-green-500/10 px-2 py-0.5 rounded border border-green-500/20">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> 已看完
                                </span>
                            ) : (
                                <div className="flex items-center gap-2 text-xs font-mono text-muted-foreground">
                                    <Clock className="w-3.5 h-3.5" />
                                    <span className="text-muted-foreground/80">剩余 {formatDuration(Math.floor(remaining / 60))}</span>
                                    <span className="w-1 h-1 rounded-full bg-border" />
                                    <span className="text-primary font-bold">{progressPercent}%</span>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                <div className="absolute inset-y-0 right-0 w-24 bg-destructive/10 flex items-center justify-center -z-10">
                    <Trash2 className="w-6 h-6 text-destructive" />
                </div>
            </motion.div>
        </motion.div>
    );
}
</file>

<file path="src/components/features/MovieActions.tsx">
"use client";

import { useState } from "react";
import { Share2, Loader2, ArrowDown, Heart } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { useToast } from "@/lib/context/ToastContext";
import { usePathname } from "next/navigation";
import { useMediaInteraction } from "@/hooks/useMediaInteraction";

export function MovieActions() {
    const { addToast } = useToast();
    const pathname = usePathname();
    const unifiedVideoId = pathname.split("/").pop() || "";

    const { isFavorite, isLoading: isLoadingList, toggleFavorite } = useMediaInteraction(unifiedVideoId);

    const [isSharing, setIsSharing] = useState(false);

    const handleScrollToPlay = () => {
        const resourceSection = document.getElementById("resources");
        if (resourceSection) {
            resourceSection.scrollIntoView({ behavior: "smooth", block: "start" });
        } else {
            addToast("未找到资源列表", "error");
        }
    };

    const handleShare = async () => {
        if (isSharing) return;
        setIsSharing(true);
        try {
            await navigator.clipboard.writeText(window.location.href);
            addToast("链接已复制", "success");
        } catch {
            addToast("复制失败", "error");
        } finally {
            setIsSharing(false);
        }
    };

    return (
        <div className="flex flex-col sm:flex-row gap-5">
            <GlowButton
                onClick={handleScrollToPlay}
                icon={<ArrowDown className="w-5 h-5" />}
                className="px-8"
            >
                选择播放源
            </GlowButton>

            <GlowButton
                variant={isFavorite ? "ghost" : "secondary"}
                onClick={toggleFavorite}
                className={isFavorite ? "text-pink-400 border-pink-500/30 bg-pink-500/10" : ""}
                icon={
                    isLoadingList ? <Loader2 className="w-5 h-5 animate-spin" /> :
                        <Heart className={`w-5 h-5 ${isFavorite ? "fill-current" : ""}`} />
                }
            >
                {isLoadingList ? "处理中..." : isFavorite ? "已收藏" : "收藏"}
            </GlowButton>

            <GlowButton
                variant="ghost"
                onClick={handleShare}
                icon={isSharing ? <Loader2 className="w-5 h-5 animate-spin" /> : <Share2 className="w-5 h-5" />}
            >
                分享
            </GlowButton>
        </div>
    );
}
</file>

<file path="src/components/features/profile/IdentityCard.tsx">
"use client";

import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { Camera, Mail, Award, Crown, Check as CheckIcon, X, Edit2, CalendarCheck, LogOut } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { User } from "@/lib/types";
import { cn } from "@/lib/utils";
import React, { useRef } from "react";

interface IdentityCardProps {
    user: User;
    isEditing: boolean;
    editNickname: string;
    isUploading: boolean;
    isCheckingIn: boolean;
    isCheckedInToday: boolean;
    onSetEditNickname: (val: string) => void;
    onSaveProfile: () => void;
    onStartEdit: () => void;
    onCancelEdit: () => void;
    onUploadAvatar: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onCheckIn: () => void;
    onLogout: () => void;
}

export function IdentityCard({
                                 user,
                                 isEditing,
                                 editNickname,
                                 isUploading,
                                 isCheckingIn,
                                 isCheckedInToday,
                                 onSetEditNickname,
                                 onSaveProfile,
                                 onStartEdit,
                                 onCancelEdit,
                                 onUploadAvatar,
                                 onCheckIn,
                                 onLogout
                             }: IdentityCardProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const x = useMotionValue(0);
    const y = useMotionValue(0);
    const mouseX = useSpring(x, { stiffness: 100, damping: 30 });
    const mouseY = useSpring(y, { stiffness: 100, damping: 30 });

    const rotateX = useTransform(mouseY, [-0.5, 0.5], [7, -7]);
    const rotateY = useTransform(mouseX, [-0.5, 0.5], [-7, 7]);
    const sheenX = useTransform(mouseX, [-0.5, 0.5], ["-50%", "150%"]);
    const sheenOpacity = useTransform(mouseX, [-0.5, 0, 0.5], [0, 0.1, 0]);

    const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
        const rect = e.currentTarget.getBoundingClientRect();
        x.set((e.clientX - rect.left) / rect.width - 0.5);
        y.set((e.clientY - rect.top) / rect.height - 0.5);
    };

    const handleMouseLeave = () => {
        x.set(0);
        y.set(0);
    };

    const handleAvatarClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        fileInputRef.current?.click();
    };

    const currentLevelXp = (user.level - 1) * (user.level - 1) * 100;
    const nextLevelXp = user.nextLevelXp;
    const progressPercent = Math.min(100, Math.max(0, ((user.experience - currentLevelXp) / (nextLevelXp - currentLevelXp)) * 100));
    const isAdmin = user.role === 'ADMIN' || user.role === 'ROLE_ADMIN';

    return (
        <div className="perspective-1000 mb-12">
            <motion.div
                onMouseMove={handleMouseMove}
                onMouseLeave={handleMouseLeave}
                style={{
                    rotateX,
                    rotateY,
                    transformStyle: "preserve-3d"
                }}
                className="relative bg-card/60 border border-border-subtle/20 rounded-[32px] overflow-hidden shadow-2xl backdrop-blur-xl group h-auto md:h-64"
            >
                <motion.div
                    style={{
                        x: sheenX,
                        opacity: sheenOpacity,
                        background: "linear-gradient(105deg, transparent 20%, rgba(255,255,255,0.3) 40%, transparent 60%)"
                    }}
                    className="absolute inset-0 z-20 pointer-events-none mix-blend-overlay"
                />

                <div className="absolute -top-20 -right-20 w-64 h-64 bg-primary/20 blur-[80px] rounded-full pointer-events-none" />
                <div className="absolute -bottom-20 -left-20 w-64 h-64 bg-purple-500/20 blur-[80px] rounded-full pointer-events-none" />

                <div className="absolute inset-0 opacity-20 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] pointer-events-none z-0" />

                <div className="relative z-10 grid grid-cols-1 md:grid-cols-[auto_1fr_auto] gap-8 p-8 md:p-10 h-full items-center">

                    <div className="flex justify-center md:justify-start">
                        <div className="relative group/avatar cursor-pointer" onClick={handleAvatarClick}>
                            <div className="w-32 h-32 rounded-full p-1 bg-gradient-to-br from-primary/50 to-purple-500/50 shadow-[0_0_30px_rgba(99,102,241,0.3)] relative z-10 transition-transform duration-300 group-hover/avatar:scale-105">
                                <div className="w-full h-full rounded-full overflow-hidden bg-background relative">
                                    <QuantumImage
                                        src={user.avatarUrl || "https://i.pravatar.cc/300?u=default"}
                                        alt="User Avatar"
                                        fill
                                        className="object-cover"
                                    />
                                    <div className="absolute inset-0 bg-black/60 opacity-0 group-hover/avatar:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-sm">
                                        {isUploading ? (
                                            <div className="animate-spin w-8 h-8 border-2 border-white border-t-transparent rounded-full"/>
                                        ) : (
                                            <Camera className="w-8 h-8 text-white" />
                                        )}
                                    </div>
                                    <input
                                        type="file"
                                        ref={fileInputRef}
                                        className="hidden"
                                        accept="image/jpeg,image/png,image/webp"
                                        onChange={onUploadAvatar}
                                    />
                                </div>
                            </div>

                            <div className="absolute bottom-0 right-0 z-20 translate-x-1 translate-y-1">
                                <div className="bg-background p-1 rounded-full border border-border-subtle/10">
                                    <div className="bg-gradient-to-r from-amber-500 to-orange-600 text-white text-xs font-bold px-3 py-1 rounded-full flex items-center gap-1 shadow-lg border border-white/10">
                                        <span>Lv.{user.level}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col items-center md:items-start space-y-5 min-w-0 transform-gpu translate-z-10">
                        <div className="flex items-center gap-3 w-full justify-center md:justify-start h-10">
                            {isEditing ? (
                                <div className="flex items-center gap-2 w-full max-w-sm animate-in fade-in zoom-in duration-200">
                                    <input
                                        value={editNickname}
                                        onChange={(e) => onSetEditNickname(e.target.value)}
                                        autoFocus
                                        className="flex-1 bg-secondary/50 border border-primary/50 rounded-lg px-3 py-1 text-2xl font-bold text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 h-10"
                                    />
                                    <button onClick={onSaveProfile} className="h-10 w-10 flex items-center justify-center bg-green-500/20 text-green-500 border border-green-500/30 rounded-lg hover:bg-green-500/30 transition-colors">
                                        <CheckIcon className="w-5 h-5"/>
                                    </button>
                                    <button onClick={onCancelEdit} className="h-10 w-10 flex items-center justify-center bg-red-500/20 text-red-500 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-colors">
                                        <X className="w-5 h-5"/>
                                    </button>
                                </div>
                            ) : (
                                <div className="group/name flex items-center gap-3">
                                    <h1 className="text-4xl font-black text-foreground tracking-tighter truncate max-w-[200px] md:max-w-[300px]" title={user.nickname}>
                                        {user.nickname}
                                    </h1>
                                    <button onClick={onStartEdit} className="p-1.5 rounded-lg bg-secondary/50 text-muted-foreground opacity-0 group-hover/name:opacity-100 hover:bg-secondary hover:text-foreground transition-all transform translate-y-1">
                                        <Edit2 className="w-4 h-4" />
                                    </button>
                                </div>
                            )}
                        </div>

                        <div className="flex flex-wrap items-center justify-center md:justify-start gap-3">
                            <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-secondary/50 border border-border-subtle/10 text-xs text-muted-foreground">
                                <Mail className="w-3 h-3" />
                                <span className="font-mono pt-0.5">{user.email}</span>
                            </div>

                            <div className={cn(
                                "flex items-center gap-1.5 px-3 py-1 rounded-full border text-xs font-bold uppercase tracking-wider shadow-sm",
                                isAdmin
                                    ? "bg-red-500/10 border-red-500/30 text-red-500"
                                    : "bg-primary/10 border-primary/30 text-primary"
                            )}>
                                {isAdmin ? <Award className="w-3 h-3" /> : <Crown className="w-3 h-3" />}
                                <span className="pt-0.5">{isAdmin ? '管理员' : (user.title || '萌新影迷')}</span>
                            </div>
                        </div>

                        <div className="w-full max-w-sm bg-secondary/50 rounded-full h-5 border border-border-subtle/10 relative overflow-hidden group/xp">
                            <div className="absolute inset-0 flex items-center justify-between px-3 text-[9px] font-bold font-mono text-foreground/60 z-20 pointer-events-none mix-blend-overlay">
                                <span>Lv.{user.level}</span>
                                <span>{progressPercent.toFixed(1)}%</span>
                                <span>Lv.{user.level + 1}</span>
                            </div>
                            <motion.div
                                initial={{ width: 0 }}
                                animate={{ width: `${progressPercent}%` }}
                                transition={{ duration: 1.5, ease: "circOut" }}
                                className="h-full bg-gradient-to-r from-indigo-600 via-purple-500 to-indigo-400 relative"
                            >
                                <div className="absolute inset-0 bg-[url('/noise.svg')] opacity-30" />
                                <div className="absolute top-0 right-0 bottom-0 w-px bg-white/50 shadow-[0_0_10px_white]" />
                            </motion.div>
                        </div>
                    </div>

                    <div className="flex flex-col gap-3 justify-center min-w-[140px]">
                        <GlowButton
                            variant={isCheckedInToday ? "ghost" : "primary"}
                            onClick={onCheckIn}
                            className={cn(
                                "w-full justify-center shadow-lg",
                                isCheckedInToday ? "opacity-60 cursor-not-allowed bg-secondary/30 hover:bg-secondary/30 shadow-none border border-border-subtle/10" : ""
                            )}
                            icon={isCheckingIn ? <div className="animate-spin w-4 h-4 border-2 border-current border-t-transparent rounded-full"/> : <CalendarCheck className="w-4 h-4" />}
                        >
                            {isCheckedInToday ? "今日已签" : "每日签到"}
                        </GlowButton>

                        <button
                            onClick={onLogout}
                            className="flex items-center justify-center gap-2 w-full py-3 rounded-full text-xs font-bold text-muted-foreground hover:text-foreground hover:bg-secondary/50 transition-all group/logout"
                        >
                            <LogOut className="w-4 h-4 group-hover/logout:-translate-x-0.5 transition-transform" />
                            安全登出
                        </button>
                    </div>
                </div>
            </motion.div>
        </div>
    );
}
</file>

<file path="src/components/features/resource/OnlinePanel.tsx">
"use client";

import { useMemo, useState } from "react"; // Removed useEffect
import { motion } from "framer-motion";
import { FileX, Zap, Layers } from "lucide-react";
import { MovieDetail } from "@/lib/types";
import { SourceRail } from "./SourceRail";
import { EpisodeGrid } from "./EpisodeGrid";
import { VideoDomain } from "@/lib/domain/video";

interface OnlinePanelProps {
    movie: MovieDetail;
    onPlay: (episodeId: string, sourceId: string) => void;
}

export function OnlinePanel({ movie, onPlay }: OnlinePanelProps) {
    const [selectedId, setSelectedId] = useState(movie.sources[0]?.id || "");

    const activeSourceId = useMemo(() => {
        const isValid = movie.sources.some(s => s.id === selectedId);
        return isValid ? selectedId : (movie.sources[0]?.id || "");
    }, [movie.sources, selectedId]);

    const activeSource = useMemo(() =>
            movie.sources.find(s => s.id === activeSourceId),
        [movie.sources, activeSourceId]);

    const episodeGroups = useMemo(() => {
        if (!activeSource) return { native: [], external: [] };
        return VideoDomain.groupEpisodes(activeSource.episodes);
    }, [activeSource]);

    if (!movie.sources || movie.sources.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <FileX className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无在线播放资源</p>
            </div>
        );
    }

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="flex flex-col h-full bg-card"
        >
            <SourceRail
                sources={movie.sources}
                activeSourceId={activeSourceId} // Uses the safe, derived ID
                onSelect={setSelectedId}        // Updates the user preference
            />

            <div className="p-6 md:p-8 min-h-[300px]">
                {activeSource && activeSource.episodes.length > 0 ? (
                    <div className="space-y-10 animate-in fade-in slide-in-from-bottom-2 duration-500">
                        {episodeGroups.native.length > 0 && (
                            <div className="space-y-4">
                                <div className="flex items-center gap-3 select-none">
                                    <div className="p-1.5 rounded-md bg-primary/10 text-primary">
                                        <Zap className="w-4 h-4 fill-current" />
                                    </div>
                                    <span className="text-sm font-bold text-foreground">极速直连</span>
                                    <span className="text-[10px] text-muted-foreground font-mono bg-secondary px-2 py-0.5 rounded border border-border-subtle/10">
                                        HLS / 4K
                                    </span>
                                </div>
                                <EpisodeGrid
                                    episodes={episodeGroups.native}
                                    isNative={true}
                                    onPlay={(epId) => onPlay(epId, activeSourceId)}
                                />
                            </div>
                        )}

                        {episodeGroups.native.length > 0 && episodeGroups.external.length > 0 && (
                            <div className="h-px bg-gradient-to-r from-transparent via-border-subtle/10 to-transparent" />
                        )}

                        {episodeGroups.external.length > 0 && (
                            <div className="space-y-4">
                                <div className="flex items-center gap-3 select-none">
                                    <div className="p-1.5 rounded-md bg-secondary text-muted-foreground">
                                        <Layers className="w-4 h-4" />
                                    </div>
                                    <span className="text-sm font-bold text-muted-foreground">网页解析</span>
                                    <span className="text-[10px] text-muted-foreground/80 font-mono">
                                        第三方播放器
                                    </span>
                                </div>
                                <EpisodeGrid
                                    episodes={episodeGroups.external}
                                    isNative={false}
                                    onPlay={(epId) => onPlay(epId, activeSourceId)}
                                />
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                        <FileX className="w-8 h-8 mb-3 opacity-30" />
                        <p className="text-sm">该线路暂无资源</p>
                    </div>
                )}
            </div>
        </motion.div>
    );
}
</file>

<file path="src/components/features/resource/SourceRail.tsx">
"use client";

import { useRef, useEffect, useState, useCallback } from "react";
import { motion, useMotionValue, animate, useMotionValueEvent } from "framer-motion";
import { Server, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { VideoSource } from "@/lib/types";

interface SourceRailProps {
    sources: VideoSource[];
    activeSourceId: string;
    onSelect: (id: string) => void;
}

export function SourceRail({ sources, activeSourceId, onSelect }: SourceRailProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);

    const x = useMotionValue(0);

    const [constraints, setConstraints] = useState({ left: 0, right: 0 });
    const [showLeftArrow, setShowLeftArrow] = useState(false);
    const [showRightArrow, setShowRightArrow] = useState(false);
    const [isDragging, setIsDragging] = useState(false);

    const measureConstraints = useCallback(() => {
        if (!containerRef.current || !contentRef.current) return;

        const containerWidth = containerRef.current.clientWidth;
        const contentWidth = contentRef.current.scrollWidth;

        const minX = Math.min(0, containerWidth - contentWidth);

        setConstraints({ left: minX, right: 0 });

        const currentX = x.get();
        setShowLeftArrow(currentX < -10);
        setShowRightArrow(currentX > minX + 10);
    }, [x, sources]);

    useEffect(() => {
        measureConstraints();
        const handleResize = () => measureConstraints();
        window.addEventListener("resize", handleResize);
        return () => window.removeEventListener("resize", handleResize);
    }, [measureConstraints]);

    useMotionValueEvent(x, "change", (latest) => {
        setShowLeftArrow(latest < -10);
        setShowRightArrow(latest > constraints.left + 10);
    });

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        const handleWheel = (e: WheelEvent) => {
            if (constraints.left === 0) return;

            const isVertical = Math.abs(e.deltaY) > Math.abs(e.deltaX);
            const delta = isVertical ? e.deltaY : e.deltaX;

            if (Math.abs(delta) < 1) return;

            e.preventDefault();

            const currentX = x.get();
            const distance = delta;

            let targetX = currentX - distance;
            targetX = Math.max(constraints.left, Math.min(constraints.right, targetX));

            animate(x, targetX, {
                type: "tween",
                ease: "easeOut",
                duration: 0.3
            });
        };

        container.addEventListener("wheel", handleWheel, { passive: false });
        return () => container.removeEventListener("wheel", handleWheel);
    }, [constraints, x]);

    const manualScroll = (direction: 'left' | 'right') => {
        const currentX = x.get();
        const offset = containerRef.current ? containerRef.current.clientWidth / 2 : 300;

        let targetX = direction === 'left' ? currentX + offset : currentX - offset;
        targetX = Math.max(constraints.left, Math.min(constraints.right, targetX));

        animate(x, targetX, {
            type: "spring",
            stiffness: 250,
            damping: 25
        });
    };

    return (
        <div
            className="relative border-b border-border-subtle/10 bg-muted/20 group/rail select-none"
            ref={containerRef}
        >
            <div className="absolute inset-0 pointer-events-none z-20 flex justify-between items-center px-2">
                <motion.button
                    initial={false}
                    animate={{
                        opacity: showLeftArrow ? 1 : 0,
                        scale: showLeftArrow ? 1 : 0.8,
                        x: showLeftArrow ? 0 : -10
                    }}
                    onClick={() => manualScroll('left')}
                    className="pointer-events-auto p-2 rounded-full bg-card/80 border border-border-subtle/10 text-foreground backdrop-blur-md hover:bg-primary hover:border-primary hover:text-white transition-colors shadow-lg"
                >
                    <ChevronLeft className="w-4 h-4" />
                </motion.button>

                <motion.button
                    initial={false}
                    animate={{
                        opacity: showRightArrow ? 1 : 0,
                        scale: showRightArrow ? 1 : 0.8,
                        x: showRightArrow ? 0 : 10
                    }}
                    onClick={() => manualScroll('right')}
                    className="pointer-events-auto p-2 rounded-full bg-card/80 border border-border-subtle/10 text-foreground backdrop-blur-md hover:bg-primary hover:border-primary hover:text-white transition-colors shadow-lg"
                >
                    <ChevronRight className="w-4 h-4" />
                </motion.button>
            </div>

            <div
                className="overflow-hidden"
                style={{
                    maskImage: 'linear-gradient(to right, transparent 0%, black 5%, black 95%, transparent 100%)',
                    WebkitMaskImage: 'linear-gradient(to right, transparent 0%, black 5%, black 95%, transparent 100%)'
                }}
            >
                <motion.div
                    ref={contentRef}
                    style={{ x }}
                    drag="x"
                    dragConstraints={constraints}
                    dragElastic={0.1}
                    onDragStart={() => setIsDragging(true)}
                    onDragEnd={() => setTimeout(() => setIsDragging(false), 10)}
                    className="flex items-center py-5 px-6 gap-3 w-max cursor-grab active:cursor-grabbing touch-pan-y"
                >
                    <div className="w-2 flex-shrink-0" />

                    <div className="flex items-center gap-2 text-xs font-bold text-muted-foreground uppercase tracking-widest mr-4 flex-shrink-0 select-none border border-border-subtle/10 px-3 py-1.5 rounded-md bg-background/50">
                        <Server className="w-3 h-3" /> 线路
                    </div>

                    {sources.map((source) => (
                        <button
                            key={source.id}
                            onClick={() => {
                                if (!isDragging) onSelect(source.id);
                            }}
                            className={cn(
                                "flex-shrink-0 px-4 py-2 rounded-lg text-xs font-medium transition-all border whitespace-nowrap relative overflow-hidden group select-none outline-none focus:ring-2 focus:ring-primary/50",
                                activeSourceId === source.id
                                    ? "bg-card border-primary/50 text-primary shadow-[0_0_20px_rgba(99,102,241,0.15)]"
                                    : "bg-background/50 border-border-subtle/10 text-muted-foreground hover:bg-card hover:text-foreground hover:border-border-subtle/20"
                            )}
                        >
                            <span className="relative z-10">{source.name}</span>

                            {activeSourceId === source.id && (
                                <motion.div
                                    layoutId="activeSourceGlow"
                                    className="absolute inset-0 bg-primary/5 z-0"
                                    transition={{ duration: 0.3 }}
                                />
                            )}

                            <div className={cn(
                                "absolute bottom-0 left-0 w-full h-[2px] transition-colors duration-300",
                                activeSourceId === source.id ? "bg-primary" : "bg-transparent group-hover:bg-border-subtle/20"
                            )} />
                        </button>
                    ))}

                    <div className="w-2 flex-shrink-0" />
                </motion.div>
            </div>
        </div>
    );
}
</file>

<file path="src/components/layout/RouteGuard.tsx">
"use client";

import React, { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { Loader2 } from "lucide-react";

const PUBLIC_PATHS = [
    "/",
    "/explore",
    "/login",
    "/signup",
    "/activate",
    "/forgot-password"
];

const PUBLIC_PATH_PREFIXES = [
    "/movie/"
];

const PENDING_ALLOWED_PATHS = [
    "/profile"
];

export function RouteGuard({ children }: { children: React.ReactNode }) {
    const { user, isLoading } = useAuth();
    const router = useRouter();
    const pathname = usePathname();

    useEffect(() => {
        if (isLoading) return;

        const isPublic = PUBLIC_PATHS.includes(pathname) ||
            PUBLIC_PATH_PREFIXES.some(prefix => pathname.startsWith(prefix));

        if (!user) {
            if (!isPublic) {
                router.push("/login");
            }
            return;
        }

        if (user.status === "PENDING_VERIFICATION") {
            const isAllowed =
                pathname === "/activate" ||
                isPublic ||
                PENDING_ALLOWED_PATHS.includes(pathname);

            if (!isAllowed) {
                router.push("/activate");
            }
            return;
        }

        const authPages = ["/login", "/signup", "/activate", "/forgot-password"];
        if (authPages.includes(pathname)) {
            router.push("/");
        }

    }, [user, isLoading, pathname, router]);

    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-[#030305]">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
            </div>
        );
    }

    return <>{children}</>;
}
</file>

<file path="src/app/globals.css">
@import "tailwindcss";

/*
  SYSTEM: SEMANTIC TOKENS
  ----------------------------------------------------------------------------
  We use CSS variables to define the design system's primitive values.
  The .dark class mirrors the existing "Deep Obsidian" aesthetic perfectly.
  The :root (Light) class introduces a "Soft Industrial" aesthetic.
*/

:root {
  /* --- Light Mode (Soft Industrial) --- */

  /* Base Layout */
  --background: 242 242 245;    /* #f2f2f5 - Cool Grey/Zinc Tint */
  --foreground: 24 24 27;       /* #18181b - Zinc 900 */

  /* Surfaces (Cards, Panels, Modals) */
  --card: 255 255 255;          /* #ffffff */
  --card-foreground: 24 24 27;  /* #18181b */
  --card-hover: 244 244 245;    /* #f4f4f5 - Zinc 100 */

  /* Interactive Elements (Popovers, Tooltips) */
  --popover: 255 255 255;
  --popover-foreground: 24 24 27;

  /* Primary Brand (Indigo) */
  --primary: 79 70 229;         /* #4f46e5 - Indigo 600 (Darker for Light Mode contrast) */
  --primary-foreground: 255 255 255;

  /* Secondary / Muted (Subtle elements) */
  --secondary: 244 244 245;     /* #f4f4f5 */
  --secondary-foreground: 24 24 27;

  --muted: 244 244 245;         /* #f4f4f5 */
  --muted-foreground: 113 113 122; /* #71717a - Zinc 500 */

  /* Destructive Actions */
  --destructive: 239 68 68;     /* #ef4444 */
  --destructive-foreground: 255 255 255;

  /* Borders & Inputs */
  /* Light mode uses black alpha for borders to maintain glass feel on white */
  --border: 228 228 231;        /* #e4e4e7 - Zinc 200 (Solid fallback) */
  --border-subtle: 0 0 0;       /* Use with opacity in utilities, e.g. border-border-subtle/10 */
  --input: 228 228 231;
  --ring: 79 70 229;

  /* Charts / Accents */
  --radius: 1rem; /* 16px */
}

.dark {
  /* --- Dark Mode (Deep Obsidian - Legacy Compatible) --- */

  /* Base Layout */
  --background: 3 3 5;          /* #030305 - The core identity */
  --foreground: 255 255 255;    /* #ffffff */

  /* Surfaces */
  --card: 10 10 12;             /* #0a0a0c */
  --card-foreground: 255 255 255;
  --card-hover: 18 18 20;       /* #121214 */

  /* Interactive */
  --popover: 10 10 12;          /* #0a0a0c */
  --popover-foreground: 255 255 255;

  /* Primary Brand */
  --primary: 99 102 241;        /* #6366f1 - Indigo 500 (Brighter for Dark Mode) */
  --primary-foreground: 255 255 255;

  /* Secondary / Muted */
  --secondary: 39 39 42;        /* #27272a */
  --secondary-foreground: 250 250 250;

  --muted: 39 39 42;            /* #27272a */
  --muted-foreground: 161 161 170; /* #a1a1aa - Zinc 400 */

  /* Destructive */
  --destructive: 127 29 29;     /* #7f1d1d */
  --destructive-foreground: 255 255 255;

  /* Borders */
  /* Dark mode uses white alpha */
  --border: 39 39 42;           /* #27272a (Solid fallback) */
  --border-subtle: 255 255 255; /* Use with opacity, e.g. border-white/10 -> border-border-subtle/10 */
  --input: 39 39 42;
  --ring: 99 102 241;
}

/*
  THEME CONFIGURATION
  Mapping CSS variables to Tailwind utilities.
  We use `rgb()` to allow opacity modifiers (e.g., `bg-background/80`).
*/
@theme inline {
  --color-background: rgb(var(--background));
  --color-foreground: rgb(var(--foreground));

  --color-card: rgb(var(--card));
  --color-card-foreground: rgb(var(--card-foreground));
  --color-card-hover: rgb(var(--card-hover));

  --color-popover: rgb(var(--popover));
  --color-popover-foreground: rgb(var(--popover-foreground));

  --color-primary: rgb(var(--primary));
  --color-primary-foreground: rgb(var(--primary-foreground));

  --color-secondary: rgb(var(--secondary));
  --color-secondary-foreground: rgb(var(--secondary-foreground));

  --color-muted: rgb(var(--muted));
  --color-muted-foreground: rgb(var(--muted-foreground));

  --color-destructive: rgb(var(--destructive));
  --color-destructive-foreground: rgb(var(--destructive-foreground));

  --color-border: rgb(var(--border));
  --color-border-subtle: rgb(var(--border-subtle)); /* Special utility for glass borders */
  --color-input: rgb(var(--input));
  --color-ring: rgb(var(--ring));

  --radius-xl: calc(var(--radius) + 4px);
  --radius-lg: var(--radius);
  --radius-md: calc(var(--radius) - 2px);
  --radius-sm: calc(var(--radius) - 4px);

  /* Retain fonts from original file */
  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
}

/*
  GLOBAL STYLES
  ----------------------------------------------------------------------------
*/
body {
  background-color: var(--color-background);
  color: var(--color-foreground);

  /* Texture: Conditional based on mode */
  /* Dark Mode: Radial Glows (Simulated Lighting) */
  /* Light Mode: Subtle Grain (Physical Texture) */
  background-image:
          radial-gradient(circle at 50% 0%, rgb(var(--primary) / 0.03) 0%, transparent 50%),
          radial-gradient(circle at 100% 100%, rgb(var(--primary) / 0.02) 0%, transparent 40%);

  min-height: 100vh;
  overflow-x: hidden;
  -webkit-font-smoothing: antialiased;
}

/*
  UTILITIES: SEMANTIC COMPONENTS
  Replacing hardcoded .surface-panel and .density-card with semantic variants.
*/

/* Glass Surface (Panels, Dialogs) */
.surface-panel {
  /* Dynamic Background: White/60 (Dark) or White/80 (Light) ? No, semantic mapping. */
  /* Dark: bg-card/60, Light: bg-white/80 */
  background-color: rgb(var(--card) / 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);

  /* Dynamic Border: White/6 (Dark) or Black/6 (Light) */
  border: 1px solid rgb(var(--border-subtle) / 0.06);

  /* Dynamic Shadow */
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1); /* Reduced shadow intensity for general use */
}

/* High Density Card (Movie Cards) */
.density-card {
  background-color: var(--color-card);
  border: 1px solid rgb(var(--border-subtle) / 0.06); /* border-white/6 or border-black/6 */
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.density-card:hover {
  border-color: rgb(var(--border-subtle) / 0.15);     /* border-white/12 or border-black/15 */
  background-color: var(--color-card-hover);
  box-shadow: 0 10px 40px -10px rgba(0, 0, 0, 0.3); /* Softer shadow */
}

/* Custom Scrollbar - Semantic */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background-color: rgb(var(--muted-foreground) / 0.3);
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background-color: rgb(var(--muted-foreground) / 0.5);
}

/* Player Branding */
[data-vidstack-player] {
  --media-brand: rgb(var(--primary));
  --media-focus-ring: 0 0 0 2px rgb(var(--primary) / 0.5);
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}
</file>

<file path="src/app/not-found.tsx">
"use client";

import { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { GlowButton } from "@/components/ui/GlowButton";
import { Search, AlertTriangle, ArrowRight } from "lucide-react";
import { contentApi } from "@/lib/api/content";
import { MovieCard as MovieCardType } from "@/lib/types";
import { MovieCard } from "@/components/features/MovieCard";

export default function NotFound() {
    const pathname = usePathname();
    const [recommendations, setRecommendations] = useState<MovieCardType[]>([]);

    const guessKeyword = useMemo(() => {
        if (!pathname) return "";
        const parts = pathname.split('/').filter(Boolean);
        const lastPart = parts[parts.length - 1];
        if (lastPart && lastPart.length > 1) {
            try {
                return decodeURIComponent(lastPart).replace(/[-_]/g, ' ');
            } catch (e) {
                return "";
            }
        }
        return "";
    }, [pathname]);

    useEffect(() => {
        contentApi.explore({ sort: "trending", size: 6 })
            .then(res => setRecommendations(res.content || []))
            .catch(() => {});
    }, []);

    return (
        <div className="min-h-screen bg-background pt-20 pb-20 px-6 flex flex-col items-center relative overflow-hidden">
            <div className="absolute inset-0 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] opacity-10 pointer-events-none" />

            <div className="relative z-10 max-w-5xl w-full flex flex-col items-center text-center">
                <div className="w-24 h-24 bg-red-500/10 rounded-3xl flex items-center justify-center mb-8 border border-red-500/20 shadow-[0_0_50px_rgba(239,68,68,0.2)]">
                    <AlertTriangle className="w-10 h-10 text-red-500" />
                </div>

                <h1 className="text-5xl md:text-7xl font-black text-foreground tracking-tighter mb-6">
                    404 <span className="text-muted-foreground">Lost</span>
                </h1>

                <p className="text-lg text-muted-foreground max-w-lg mb-10">
                    您访问的坐标 <code className="bg-secondary px-2 py-0.5 rounded text-foreground font-mono text-sm border border-border-subtle/10">{pathname}</code> 似乎是一片虚空。
                    {guessKeyword && (
                        <span>
                            但我们猜测您可能在寻找 <span className="text-primary font-bold">“{guessKeyword}”</span>？
                        </span>
                    )}
                </p>

                {/* 强制引导搜索 */}
                <div className="w-full max-w-md mb-16">
                    <Link href={`/search?keyword=${guessKeyword}`}>
                        <GlowButton className="w-full py-4 text-lg" icon={<Search className="w-5 h-5"/>}>
                            {guessKeyword ? `搜索 "${guessKeyword}"` : "搜索全站资源"}
                        </GlowButton>
                    </Link>
                </div>

                {/* 智能推荐 (挽留流量的核心) */}
                {recommendations.length > 0 && (
                    <div className="w-full text-left">
                        <div className="flex items-center gap-3 mb-6 border-b border-border-subtle/10 pb-4">
                            <div className="w-1 h-5 bg-emerald-500 rounded-full" />
                            <h3 className="text-lg font-bold text-foreground">热门资源推荐</h3>
                            <span className="text-xs text-muted-foreground ml-auto">大家都在看</span>
                        </div>

                        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
                            {recommendations.map((movie, idx) => (
                                <MovieCard key={movie.id} movie={movie} index={idx} />
                            ))}
                        </div>
                    </div>
                )}

                <div className="mt-12">
                    <Link href="/" className="text-sm font-bold text-muted-foreground hover:text-foreground flex items-center gap-2 transition-colors">
                        返回首页 <ArrowRight className="w-4 h-4" />
                    </Link>
                </div>
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/CommandMenu.tsx">
"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { Search, Film, User, LogOut, Laptop, History, X } from "lucide-react";
import { useAuth } from "@/lib/context/AuthContext";
import { cn } from "@/lib/utils";

export function CommandMenu() {
    const [open, setOpen] = React.useState(false);
    const [query, setQuery] = React.useState("");
    const router = useRouter();
    const { logout, user } = useAuth();

    const canUseCommandMenu = user && user.status === "ACTIVE";

    React.useEffect(() => {
        const down = (e: KeyboardEvent) => {
            if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
                e.preventDefault();
                if (canUseCommandMenu) {
                    setOpen((open) => !open);
                }
            }
        };
        document.addEventListener("keydown", down);
        return () => document.removeEventListener("keydown", down);
    }, [canUseCommandMenu]);

    const runCommand = React.useCallback((command: () => void) => {
        setOpen(false);
        command();
    }, []);

    const groups = [
        {
            heading: "快捷导航",
            items: [
                { icon: Film, label: "浏览电影", action: () => router.push("/explore?type=movie") },
                { icon: Laptop, label: "浏览剧集", action: () => router.push("/explore?type=series") },
                { icon: History, label: "观看历史", action: () => router.push("/history") },
            ]
        },
        {
            heading: "账户",
            items: [
                { icon: User, label: "个人中心", action: () => router.push("/profile") },
                { icon: LogOut, label: "退出登录", action: () => logout(), color: "text-red-500" },
            ]
        }
    ];

    const filteredGroups = query === ""
        ? groups
        : groups.map(group => ({
            ...group,
            items: group.items.filter(item => item.label.toLowerCase().includes(query.toLowerCase()))
        })).filter(group => group.items.length > 0);

    if (!canUseCommandMenu) return null;

    return (
        <AnimatePresence>
            {open && (
                <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] px-4">
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={() => setOpen(false)}
                        className="fixed inset-0 bg-black/60 backdrop-blur-sm"
                    />

                    <motion.div
                        initial={{ opacity: 0, scale: 0.95, y: 10 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.95, y: 10 }}
                        transition={{ duration: 0.2, ease: "easeOut" }}
                        className="w-full max-w-xl bg-popover border border-border-subtle/10 rounded-xl shadow-2xl overflow-hidden relative z-10 flex flex-col max-h-[60vh]"
                    >
                        <div className="flex items-center border-b border-border-subtle/10 px-4 py-4">
                            <Search className="w-5 h-5 text-muted-foreground mr-3" />
                            <input
                                autoFocus
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                placeholder="输入命令或搜索..."
                                className="flex-1 bg-transparent text-foreground placeholder:text-muted-foreground outline-none text-base font-medium"
                            />
                            <div className="flex gap-2">
                                <kbd className="hidden sm:inline-block pointer-events-none h-6 select-none items-center gap-1 rounded border border-border-subtle/20 bg-secondary/50 px-1.5 font-mono text-[10px] font-medium text-muted-foreground opacity-100">
                                    ESC
                                </kbd>
                                <button onClick={() => setOpen(false)}>
                                    <X className="w-5 h-5 text-muted-foreground hover:text-foreground transition-colors" />
                                </button>
                            </div>
                        </div>

                        <div className="flex-1 overflow-y-auto p-2">
                            {filteredGroups.length === 0 ? (
                                <div className="py-12 text-center text-sm text-muted-foreground">
                                    未找到相关指令
                                </div>
                            ) : (
                                filteredGroups.map((group, idx) => (
                                    <div key={group.heading} className={cn(idx > 0 && "mt-4")}>
                                        <div className="px-2 py-1.5 text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                                            {group.heading}
                                        </div>
                                        {group.items.map((item) => (
                                            <button
                                                key={item.label}
                                                onClick={() => runCommand(item.action)}
                                                className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors group text-left"
                                            >
                                                <item.icon className={cn("w-4 h-4", item.color || "text-muted-foreground group-hover:text-foreground")} />
                                                <span>{item.label}</span>
                                                {item.label === "浏览电影" && query === "" && (
                                                    <span className="ml-auto text-[10px] text-muted-foreground/60">Explore</span>
                                                )}
                                            </button>
                                        ))}
                                    </div>
                                ))
                            )}
                        </div>

                        <div className="p-2 border-t border-border-subtle/10 bg-secondary/10 text-[10px] text-muted-foreground flex justify-end px-4">
                            HUAWAI COMMAND V1.0
                        </div>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
}
</file>

<file path="src/components/features/resource/CloudPanel.tsx">
"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { Cloud, Copy, Check, ExternalLink } from "lucide-react";
import { MovieDetail, CloudLink } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";

export function CloudPanel({ movie }: { movie: MovieDetail }) {
    const { addToast } = useToast();
    const [copiedId, setCopiedId] = useState<string | null>(null);

    const handleCopy = async (text: string, id: string) => {
        try {
            await navigator.clipboard.writeText(text);
            setCopiedId(id);
            addToast("提取码已复制", "success");
            setTimeout(() => setCopiedId(null), 2000);
        } catch {
            addToast("复制失败", "error");
        }
    };

    if (!movie.cloudLinks || movie.cloudLinks.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <Cloud className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无网盘资源</p>
            </div>
        );
    }

    return (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 md:p-8 grid grid-cols-1 md:grid-cols-2 gap-4">
            {movie.cloudLinks.map((link: CloudLink, idx) => {
                const uniqueId = link.id || `cloud-idx-${idx}`;
                return (
                    <motion.div
                        key={uniqueId}
                        initial={{ opacity: 0, scale: 0.95 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: idx * 0.05 }}
                        className="p-5 bg-secondary/30 border border-border-subtle/10 rounded-2xl hover:border-border-subtle/30 hover:bg-secondary/50 transition-all flex items-center justify-between group"
                    >
                        <div className="flex items-center gap-4 min-w-0">
                            <div className="w-12 h-12 rounded-xl bg-primary/10 border border-primary/10 flex items-center justify-center text-primary group-hover:scale-110 transition-transform">
                                <Cloud className="w-6 h-6" />
                            </div>
                            <div className="min-w-0">
                                <div className="text-foreground font-bold text-sm truncate max-w-[150px]" title={link.title}>
                                    {link.title}
                                </div>
                                <div className="text-xs text-muted-foreground mt-1 flex items-center gap-2">
                                    <span className="font-medium text-muted-foreground/80">{link.provider}</span>
                                    {link.code && (
                                        <span className="bg-background/50 px-1.5 py-0.5 rounded font-mono text-[10px] border border-border-subtle/10">
                                            码: <span className="text-foreground select-all">{link.code}</span>
                                        </span>
                                    )}
                                </div>
                            </div>
                        </div>

                        <div className="flex gap-2 pl-2">
                            {link.code && (
                                <button
                                    onClick={() => handleCopy(link.code!, `code-${uniqueId}`)}
                                    className={cn(
                                        "p-2.5 rounded-xl text-xs font-bold transition-all border",
                                        copiedId === `code-${uniqueId}`
                                            ? "bg-green-500/10 text-green-500 border-green-500/20"
                                            : "bg-background text-muted-foreground border-border-subtle/10 hover:bg-secondary hover:text-foreground"
                                    )}
                                    title="复制提取码"
                                >
                                    {copiedId === `code-${uniqueId}` ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </button>
                            )}
                            <a
                                href={link.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-foreground text-background hover:bg-foreground/80 text-xs font-bold transition-all shadow-md hover:shadow-lg"
                            >
                                打开
                                <ExternalLink className="w-3 h-3" />
                            </a>
                        </div>
                    </motion.div>
                );
            })}
        </motion.div>
    );
}
</file>

<file path="src/components/features/resource/DownloadPanel.tsx">
"use client";

import { useState } from "react";
import { motion } from "framer-motion";
import { Magnet, Copy, Check, HardDrive } from "lucide-react";
import { MovieDetail, MagnetLink } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";

export function DownloadPanel({ movie }: { movie: MovieDetail }) {
    const { addToast } = useToast();
    const [copiedId, setCopiedId] = useState<string | null>(null);

    const handleCopy = async (text: string, id: string) => {
        try {
            await navigator.clipboard.writeText(text);
            setCopiedId(id);
            addToast("磁力链已复制", "success");
            setTimeout(() => setCopiedId(null), 2000);
        } catch {
            addToast("复制失败", "error");
        }
    };

    if (!movie.magnets || movie.magnets.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-24 text-muted-foreground">
                <HardDrive className="w-10 h-10 mb-4 opacity-20" />
                <p>暂无下载资源</p>
            </div>
        );
    }

    return (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="p-6 md:p-8 space-y-3">
            {movie.magnets.map((magnet: MagnetLink, idx) => {
                const uniqueId = magnet.id || `magnet-idx-${idx}`;
                return (
                    <motion.div
                        key={uniqueId}
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: idx * 0.05 }}
                        className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 bg-secondary/30 border border-border-subtle/10 rounded-xl hover:border-border-subtle/30 hover:bg-secondary/50 transition-all group"
                    >
                        <div className="flex-1 min-w-0">
                            <div className="flex items-start gap-3 mb-2">
                            <span className="mt-0.5 p-1 rounded bg-green-500/10 text-green-500 border border-green-500/20">
                                <Magnet className="w-3.5 h-3.5" />
                            </span>
                                <h4 className="text-foreground/90 font-medium text-sm leading-relaxed break-all font-mono selection:bg-green-500/30">
                                    {magnet.title}
                                </h4>
                            </div>
                            <div className="flex items-center gap-3 text-xs text-muted-foreground ml-8">
                                <span className="text-muted-foreground/80 font-mono font-bold">{magnet.size}</span>
                                <span className="w-1 h-1 rounded-full bg-muted-foreground" />
                                <span>{magnet.uploadDate || magnet.date}</span>
                                <div className="hidden md:flex gap-2 ml-auto md:ml-2">
                                    {magnet.tags?.map((tag: string) => (
                                        <span key={tag} className="px-1.5 py-0.5 rounded bg-background border border-border-subtle/10 text-[10px] text-muted-foreground">
                                        {tag}
                                    </span>
                                    ))}
                                </div>
                            </div>
                        </div>
                        <button
                            onClick={() => handleCopy(magnet.link, uniqueId)}
                            className={cn(
                                "flex-shrink-0 flex items-center justify-center gap-2 px-5 py-2.5 rounded-lg text-xs font-bold transition-all border ml-8 md:ml-0",
                                copiedId === uniqueId
                                    ? "bg-green-500/10 text-green-500 border-green-500/20"
                                    : "bg-background text-muted-foreground border-border-subtle/10 hover:bg-secondary hover:text-foreground group-hover:border-border-subtle/20"
                            )}
                        >
                            {copiedId === uniqueId ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                            {copiedId === uniqueId ? "已复制" : "复制链接"}
                        </button>
                    </motion.div>
                );
            })}
        </motion.div>
    );
}
</file>

<file path="src/app/loading.tsx">
import Image from "next/image";

export default function Loading() {
    return (
        <div className="fixed inset-0 z-50 bg-background flex items-center justify-center">
            <div className="relative flex flex-col items-center gap-4">
                <div className="relative w-16 h-16 flex items-center justify-center">
                    <div className="absolute inset-0 bg-primary/20 rounded-xl blur-lg animate-pulse" />

                    <div className="relative z-10 w-16 h-16 animate-[bounce_2s_infinite]">
                        <Image
                            src="/logo.svg"
                            alt="Huawai Loading"
                            fill
                            className="[.dark_&]:invert drop-shadow-xl object-contain"
                            priority
                        />
                    </div>
                </div>
                <div className="w-32 h-1 bg-secondary rounded-full overflow-hidden mt-4">
                    <div className="w-full h-full bg-muted-foreground/50 origin-left animate-[shimmer_1.5s_infinite]" />
                </div>
                <p className="text-xs text-muted-foreground font-mono tracking-widest uppercase animate-pulse">
                    Loading...
                </p>
            </div>
        </div>
    );
}
</file>

<file path="src/app/my-list/page.tsx">
"use client";

import { useEffect, useState } from "react";
import { MovieCard } from "@/components/features/MovieCard";
import { Heart, LayoutGrid, List, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { interactionApi } from "@/lib/api/interaction";
import { useAuth } from "@/lib/context/AuthContext";
import { InteractionRecord, MovieCard as MovieCardType } from "@/lib/types";

export default function MyListPage() {
    const { user } = useAuth();
    const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
    const [favorites, setFavorites] = useState<InteractionRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (user) {
            interactionApi.getFavorites()
                .then(res => setFavorites(res.content))
                .catch(console.error)
                .finally(() => setIsLoading(false));
        } else {
            setIsLoading(false);
        }
    }, [user]);

    const adaptToMovieCard = (rec: InteractionRecord): MovieCardType => ({
        id: rec.unifiedVideoId,
        title: rec.title,
        posterUrl: rec.coverImageUrl,
        rating: rec.score,
        releaseYear: "",
        categoryName: "",
        area: "未知",
    });

    const handleRemove = async (unifiedVideoId: string) => {
        try {
            await interactionApi.toggleFavorite(unifiedVideoId);
            setFavorites(prev => prev.filter(f => f.unifiedVideoId !== unifiedVideoId));
        } catch (e) {
            console.error(e);
        }
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background pt-32 flex justify-center">
                <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6">
            <div className="max-w-7xl mx-auto">
                <div className="flex items-center justify-between mb-12">
                    <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-gradient-to-tr from-pink-600 to-rose-600 rounded-xl flex items-center justify-center shadow-lg shadow-pink-900/20">
                            <Heart className="w-6 h-6 text-white fill-current" />
                        </div>
                        <div>
                            <h1 className="text-3xl font-bold text-foreground">我的收藏</h1>
                            <p className="text-muted-foreground text-sm mt-1">
                                {favorites.length} 部已收藏影片
                            </p>
                        </div>
                    </div>

                    <div className="flex bg-secondary rounded-lg p-1 border border-border-subtle/10">
                        <button
                            onClick={() => setViewMode("grid")}
                            className={cn(
                                "p-2 rounded-md transition-all",
                                viewMode === "grid" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
                            )}
                        >
                            <LayoutGrid className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => setViewMode("list")}
                            className={cn(
                                "p-2 rounded-md transition-all",
                                viewMode === "list" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
                            )}
                        >
                            <List className="w-4 h-4" />
                        </button>
                    </div>
                </div>

                {favorites.length > 0 ? (
                    <div className={cn(
                        "grid gap-6",
                        viewMode === "grid"
                            ? "grid-cols-2 md:grid-cols-4 lg:grid-cols-5"
                            : "grid-cols-1"
                    )}>
                        {favorites.map((rec, idx) => {
                            const movie = adaptToMovieCard(rec);
                            return viewMode === "grid" ? (
                                <MovieCard key={rec.id} movie={movie} index={idx} />
                            ) : (
                                <div key={rec.id} className="flex items-center gap-6 p-4 bg-card border border-border-subtle/10 rounded-xl hover:bg-secondary/50 transition-colors group cursor-pointer shadow-sm"
                                     onClick={() => window.location.href = `/movie/${movie.id}`}
                                >
                                    <div className="w-20 md:w-24 aspect-[2/3] relative rounded-lg overflow-hidden bg-neutral-900 border border-border-subtle/10 flex-shrink-0">
                                        <QuantumImage src={rec.coverImageUrl} fill className="object-cover" alt={rec.title}/>
                                    </div>
                                    <div className="flex-1">
                                        <h3 className="text-xl font-bold text-foreground mb-2">{rec.title}</h3>
                                        <div className="flex items-center gap-3 text-sm text-muted-foreground">
                                            <span className="text-amber-500 font-bold">{rec.score}</span>
                                        </div>
                                    </div>
                                    <div className="mr-4">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleRemove(rec.unifiedVideoId); }}
                                            className="px-4 py-2 text-sm font-medium text-muted-foreground border border-border rounded-full hover:border-destructive hover:text-destructive hover:bg-destructive/5 transition-all"
                                        >
                                            取消收藏
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <div className="flex flex-col items-center justify-center py-32 text-center border border-dashed border-border-subtle/10 rounded-3xl bg-secondary/5">
                        <div className="w-20 h-20 bg-secondary rounded-full flex items-center justify-center mb-6">
                            <Heart className="w-8 h-8 text-muted-foreground" />
                        </div>
                        <h3 className="text-xl font-bold text-foreground mb-2">收藏夹空空如也</h3>
                        <p className="text-muted-foreground max-w-sm mb-8">
                            当您看到感兴趣的内容时，点击“收藏”图标即可在这里找到它们。
                        </p>
                        <a href="/explore" className="px-6 py-3 bg-foreground text-background rounded-full font-bold hover:bg-foreground/80 transition-colors">
                            去探索内容
                        </a>
                    </div>
                )}
            </div>
        </div>
    );
}
</file>

<file path="src/app/search/page.tsx">
import {Suspense} from "react";
import {Metadata} from "next";
import {contentApi} from "@/lib/api/content";
import {SearchClient} from "@/components/features/search/SearchClient";
import {PortalMetadata} from "@/lib/types";
import {SITE_NAME} from "@/lib/seo";
import {Loader2} from "lucide-react";

type Props = {
    searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
};

// [STRATEGY 1]: 搜索结果页索引化 (SERP Injection)
// 允许爬虫收录搜索结果页，将其视为“专题聚合页”
export async function generateMetadata({searchParams}: Props): Promise<Metadata> {
    const params = await searchParams;
    const keyword = typeof params.keyword === 'string' ? decodeURIComponent(params.keyword) : '';

    // 如果没有关键词，就是普通的搜索入口
    if (!keyword) {
        return {
            title: `资源搜索 - ${SITE_NAME}`,
            description: "搜索全网4K HDR电影、剧集、动漫资源。",
            robots: "index, follow"
        };
    }

    // [极度激进]: 将搜索结果包装成“下载专题”
    // 百度非常吃这一套“聚合页”逻辑
    return {
        title: `${keyword}下载_${keyword}在线观看_${keyword}4K资源 - ${SITE_NAME}搜索`,
        description: `画外(Huawai)为您聚合关于“${keyword}”的全部影视资源。包含：${keyword}迅雷下载、${keyword}百度云盘、${keyword}免费在线看。`,
        keywords: [keyword, `${keyword}下载`, `${keyword}在线`, `${keyword}剧情`, "4K电影", "高清资源"],
        openGraph: {
            title: `${keyword} - 高清资源聚合`,
            description: `发现关于 ${keyword} 的所有 4K HDR 版本。`,
        },
        alternates: {
            canonical: `/search?keyword=${encodeURIComponent(keyword)}`,
        },
        robots: {
            index: true,
            follow: true,
            // 避免收录无效空结果页，可以在逻辑中进一步判断
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
        contentApi.getMetadata().catch(() => null),
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
                    initialTotal={searchData.totalElements || 0}
                    initialMetadata={metadata as PortalMetadata}
                    initialParams={{category, sort, genre, area, year, language, keyword}}
                />
            </Suspense>

            {/* [SEO TRICK]: 隐藏的内链网络 (Internal Link Wheel) */}
            {/* 仅在有搜索结果且有关键词时显示，引导爬虫抓取相关长尾词 */}
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
</file>

<file path="src/app/signup/page.tsx">
"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { authApi } from "@/lib/api/auth";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, Lock, User, ArrowRight, CheckCircle2, ShieldCheck } from "lucide-react";
import { useToast } from "@/lib/context/ToastContext";
import { useRouter } from "next/navigation";
import { usePoW } from "@/hooks/usePoW";
import { getErrorMessage } from "@/lib/utils";

export default function SignupPage() {
    const { addToast } = useToast();
    const router = useRouter();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const [step, setStep] = useState(1); // 1: Info, 2: Activate
    const [isApiLoading, setIsApiLoading] = useState(false);

    const [formData, setFormData] = useState({
        name: "",
        email: "",
        password: "",
        confirmPassword: "",
        activationCode: ""
    });

    const handleSignup = async (e: React.FormEvent) => {
        e.preventDefault();
        if (formData.password !== formData.confirmPassword) {
            addToast("两次输入的密码不一致", "error");
            return;
        }

        try {
            setIsApiLoading(true);

            const verification = await solve();

            const response = await authApi.register(formData.email, formData.password, formData.name, verification);

            if (response.verifyRequired) {
                setStep(2);
                addToast("验证码已发送至您的邮箱", "success");
            } else {
                addToast("注册成功，请直接登录", "success");
                setTimeout(() => {
                    router.push("/login");
                }, 1500);
            }
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "注册失败", "error");
        } finally {
            setIsApiLoading(false);
        }
    };

    const handleActivate = async () => {
        setIsApiLoading(true);
        try {
            const data = await authApi.activate(formData.email, formData.activationCode);
            if (data.token) {
                localStorage.setItem("huawai_token", data.token);
            }
            addToast("账号激活成功！", "success");
            router.push("/");
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "激活失败", "error");
        } finally {
            setIsApiLoading(false);
        }
    };

    const isLoading = isApiLoading || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-20%] right-[20%] w-[60%] h-[60%] bg-primary/5 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <h1 className="text-3xl font-bold text-foreground mb-2">
                        {step === 1 ? "创建账号" : "激活账号"}
                    </h1>
                    <p className="text-muted-foreground">
                        {step === 1 ? "开启您的 4K 沉浸式体验之旅" : "请输入邮箱中的6位数字验证码"}
                    </p>
                </div>

                <AnimatePresence mode="wait">
                    {step === 1 ? (
                        <motion.form
                            key="step1"
                            initial={{ x: -20, opacity: 0 }}
                            animate={{ x: 0, opacity: 1 }}
                            exit={{ x: -20, opacity: 0 }}
                            onSubmit={handleSignup}
                            className="space-y-5"
                        >
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">昵称</label>
                                <div className="relative group">
                                    <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="text"
                                        required
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="怎么称呼您?"
                                    />
                                </div>
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">电子邮箱</label>
                                <div className="relative group">
                                    <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="email"
                                        required
                                        value={formData.email}
                                        onChange={(e) => setFormData({...formData, email: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="name@example.com"
                                    />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">密码</label>
                                    <div className="relative group">
                                        <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                        <input
                                            type="password"
                                            required
                                            value={formData.password}
                                            onChange={(e) => setFormData({...formData, password: e.target.value})}
                                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                            placeholder="••••••"
                                        />
                                    </div>
                                </div>
                                <div className="space-y-2">
                                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">确认密码</label>
                                    <div className="relative group">
                                        <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                        <input
                                            type="password"
                                            required
                                            value={formData.confirmPassword}
                                            onChange={(e) => setFormData({...formData, confirmPassword: e.target.value})}
                                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                            placeholder="••••••"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="pt-4">
                                <GlowButton
                                    className="w-full py-4 text-base"
                                    icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ArrowRight className="w-5 h-5" />}
                                >
                                    {isPoWCalculating ? "安全环境计算中..." : isApiLoading ? "正在提交..." : "立即注册"}
                                </GlowButton>
                            </div>
                        </motion.form>
                    ) : (
                        <motion.div
                            key="step2"
                            initial={{ x: 20, opacity: 0 }}
                            animate={{ x: 0, opacity: 1 }}
                            className="space-y-6"
                        >
                            <div className="p-4 bg-secondary/30 border border-border-subtle/10 rounded-xl text-center">
                                <p className="text-sm text-muted-foreground">我们已向 <span className="text-foreground font-bold">{formData.email}</span> 发送了邮件。</p>
                            </div>

                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">验证码</label>
                                <input
                                    type="text"
                                    required
                                    maxLength={6}
                                    value={formData.activationCode}
                                    onChange={(e) => setFormData({...formData, activationCode: e.target.value})}
                                    className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 text-center text-2xl tracking-[0.5em] text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 transition-all"
                                    placeholder="000000"
                                />
                            </div>

                            <GlowButton
                                onClick={handleActivate}
                                className="w-full py-4 text-base"
                                icon={isApiLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                            >
                                {isApiLoading ? "激活中..." : "确认激活"}
                            </GlowButton>
                        </motion.div>
                    )}
                </AnimatePresence>

                {step === 1 && (
                    <>
                        <p className="mt-8 text-center text-sm text-muted-foreground">
                            已有账号?{" "}
                            <Link href="/login" className="text-foreground font-medium hover:underline underline-offset-4 decoration-primary">
                                直接登录
                            </Link>
                        </p>
                        <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                            <ShieldCheck className="w-3 h-3" />
                            <span>本站采用 PoW 算力验证防御技术</span>
                        </div>
                    </>
                )}
            </motion.div>
        </div>
    );
}
</file>

<file path="src/components/features/bento/TrendingItem.tsx">
"use client";

import Link from "next/link";
import { Play, Star } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { cn } from "@/lib/utils";

interface TrendingItemProps {
    movie: MovieCard;
    rank: number;
}

export function TrendingItem({ movie, rank }: TrendingItemProps) {
    const rankStr = rank < 10 ? `0${rank}` : `${rank}`;

    return (
        <Link
            href={`/movie/${movie.id}`}
            className="block relative p-2.5 rounded-xl border border-transparent hover:border-border-subtle/10 hover:bg-secondary/50 transition-all group/item select-none"
        >
            <div className="flex items-center gap-3">
                <div className="relative w-[48px] aspect-[2/3] rounded-md overflow-hidden shadow-lg border border-border-subtle/10 flex-shrink-0 bg-neutral-900">
                    <QuantumImage src={movie.posterUrl} alt={movie.title} fill className="object-cover" />
                    <div className={cn(
                        "absolute top-0 left-0 px-1 py-[1px] text-[8px] font-bold text-white rounded-br-md backdrop-blur-md",
                        rank <= 3 ? "bg-red-600/90" : "bg-neutral-600/80"
                    )}>
                        {rankStr}
                    </div>
                </div>

                <div className="flex-1 min-w-0 flex flex-col justify-center">
                    {/* [REF_CHANGE]: text-foreground */}
                    <h4 className="text-sm font-bold text-foreground/90 group-hover/item:text-primary transition-colors line-clamp-1 mb-1">
                        {movie.title}
                    </h4>

                    <div className="flex items-center gap-2 mb-1">
                        {movie.categoryName && (
                            <span className="text-[9px] text-muted-foreground bg-secondary px-1 rounded border border-border-subtle/10">
                                {movie.categoryName}
                            </span>
                        )}
                        {movie.area && (
                            <span className="text-[9px] text-muted-foreground truncate max-w-[60px]">
                                {movie.area}
                            </span>
                        )}
                    </div>

                    <div className="flex items-center gap-3 text-[9px] text-muted-foreground font-mono">
                        {movie.rating > 0 ? (
                            <span className="flex items-center gap-0.5 text-amber-500 font-bold">
                                <Star className="w-2.5 h-2.5 fill-current" /> {movie.rating}
                            </span>
                        ) : (
                            <span>暂无评分</span>
                        )}
                        <span className="w-0.5 h-0.5 bg-muted-foreground rounded-full" />
                        <span className="flex items-center gap-1">
                            {movie.releaseYear}
                        </span>
                    </div>
                </div>

                <div className="w-6 h-6 rounded-full bg-primary text-primary-foreground flex items-center justify-center opacity-0 group-hover/item:opacity-100 transition-all transform scale-50 group-hover/item:scale-100">
                    <Play className="w-2.5 h-2.5 fill-current ml-0.5" />
                </div>
            </div>
        </Link>
    );
}
</file>

<file path="src/components/features/bento/TrendingSection.tsx">
"use client";

import { useMemo } from "react";
import Link from "next/link";
import { ArrowUpRight, Flame } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { useAutoScroll } from "@/hooks/useAutoScroll";
import { TrendingItem } from "./TrendingItem";
import { cn } from "@/lib/utils";

interface TrendingSectionProps {
    movies: MovieCard[];
}

export function TrendingSection({ movies }: TrendingSectionProps) {
    const loopedMovies = useMemo(() => {
        if (movies.length === 0) return [];
        const minItems = 15;
        let repeat = Math.ceil(minItems / movies.length);
        if (repeat < 2) repeat = 2;

        return Array.from({ length: repeat }).flatMap((_, loopIdx) =>
            movies.map(m => ({ ...m, uniqueId: `${m.id}-trend-${loopIdx}` }))
        );
    }, [movies]);

    const { scrollRef, handleScroll } = useAutoScroll({ itemCount: movies.length });

    return (
        <div
            className="lg:col-span-3 flex flex-col h-[500px] lg:h-full overflow-hidden gap-3"
        >
            <div className="flex-none flex items-center justify-between px-2 pt-1">
                <div className="flex items-center gap-2">
                    <div className="p-1.5 bg-amber-500/10 rounded-lg text-amber-500 border border-amber-500/20">
                        <Flame className="w-4 h-4 fill-current" />
                    </div>
                    <h3 className="text-base font-bold text-foreground tracking-tight">时下热门</h3>
                </div>
                <Link
                    href="/explore?sort=trending"
                    className="text-xs font-bold text-muted-foreground hover:text-foreground flex items-center gap-1 transition-colors bg-secondary/30 hover:bg-secondary/60 px-2 py-1 rounded-md border border-border-subtle/10"
                >
                    完整榜单 <ArrowUpRight className="w-3 h-3" />
                </Link>
            </div>

            <div className={cn(
                "flex-1 relative rounded-3xl overflow-hidden group/section",
                "bg-card border border-border-subtle/10"
            )}>
                <div className="absolute top-0 left-0 right-0 h-12 bg-gradient-to-b from-card to-transparent z-20 pointer-events-none" />
                <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-card to-transparent z-20 pointer-events-none" />

                <div className="absolute inset-0 border border-transparent group-hover/section:border-border-subtle/20 rounded-3xl transition-colors duration-500 z-30 pointer-events-none" />

                <div
                    ref={scrollRef}
                    onScroll={handleScroll}
                    className="absolute inset-0 overflow-y-auto px-4 py-4 scrollbar-hide space-y-2"
                    style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
                >
                    {loopedMovies.map((movie, index) => {
                        const realRank = (index % movies.length) + 1;
                        return (
                            <TrendingItem
                                key={movie.uniqueId}
                                movie={movie}
                                rank={realRank}
                            />
                        );
                    })}
                </div>
            </div>

            <style jsx>{`
                .scrollbar-hide::-webkit-scrollbar {
                    display: none;
                }
            `}</style>
        </div>
    );
}
</file>

<file path="src/components/features/movie/MovieHero.tsx">
import React from "react";
import {AlignLeft, Calendar, Clock, Layers, ListVideo, MapPin, Mic2, Monitor, Sparkles, Star, Tag, Trophy} from "lucide-react";
import {MovieDetail} from "@/lib/types";
import {QuantumImage} from "@/components/ui/QuantumImage";
import {MovieActions} from "@/components/features/MovieActions";
import {Badge, ExternalLinkBadge, StatCard} from "./MovieInfoComponents";

interface MovieHeroProps {
    movie: MovieDetail;
}

export function MovieHero({movie}: MovieHeroProps) {
    return (
        <>
            <div className="absolute inset-0 w-full h-[120vh] pointer-events-none z-0">
                <QuantumImage
                    src={movie.posterUrl}
                    alt="ambient"
                    fill
                    className="object-cover opacity-20 blur-[100px] scale-125 saturate-100 dark:opacity-20 opacity-10" // 亮色模式下降低不透明度
                />
                <div className="absolute inset-0 bg-gradient-to-b from-background/80 via-background/95 to-background"/>
                <div className="absolute inset-0 bg-gradient-to-r from-background via-transparent to-background"/>
            </div>

            <div className="relative z-10 pt-32 md:pt-40 px-6 md:px-12 max-w-7xl mx-auto">
                <div className="flex flex-col lg:flex-row gap-12 lg:gap-16 items-start justify-center">

                    {/* 海报区域 (LCP 关键元素) */}
                    <div className="w-full max-w-sm lg:w-[400px] flex-shrink-0 mx-auto lg:mx-0 perspective-1000">
                        <div
                            className="relative aspect-[2/3] rounded-3xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/10 group transform transition-transform duration-500 hover:scale-[1.02] hover:-rotate-1 bg-card">
                            <QuantumImage
                                src={movie.posterUrl}
                                alt={movie.title}
                                fill
                                preload={true}
                                className="object-cover"
                            />
                            {/* 状态角标 */}
                            {movie.remarks && (
                                <div className="absolute top-4 right-4 z-20">
                                    <span
                                        className="px-3 py-1 rounded-lg bg-black/60 backdrop-blur-md border border-white/10 text-xs font-bold text-white shadow-lg">
                                        {movie.remarks}
                                    </span>
                                </div>
                            )}
                            <div
                                className="absolute inset-0 bg-gradient-to-tr from-white/0 via-white/0 to-white/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"/>
                        </div>
                        <div
                            className="hidden lg:block absolute top-full left-0 right-0 h-24 bg-gradient-to-b from-foreground/10 to-transparent blur-2xl opacity-30 transform scale-y-[-1]"
                            style={{
                                maskImage: 'linear-gradient(to bottom, black, transparent)',
                                WebkitMaskImage: 'linear-gradient(to bottom, black, transparent)'
                            }}
                        />
                    </div>

                    <div className="flex-1 w-full text-center lg:text-left pt-2 space-y-10">
                        {/* 标题与元数据 */}
                        <div className="space-y-6">
                            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3">
                                {movie.releaseYear === new Date().getFullYear().toString() && (
                                    <Badge
                                        className="bg-green-500/10 text-green-500 border-green-500/20 shadow-[0_0_15px_rgba(74,222,128,0.2)]">
                                        <Sparkles className="w-3.5 h-3.5 mr-1.5"/> NEW RELEASE
                                    </Badge>
                                )}
                                {movie.rating >= 8.0 && (
                                    <Badge
                                        className="bg-primary/10 text-primary border-primary/20 shadow-[0_0_15px_rgba(99,102,241,0.2)]">
                                        <Trophy className="w-3.5 h-3.5 mr-1.5"/> TOP RATED
                                    </Badge>
                                )}
                                {movie.categoryName && (
                                    <Badge className="bg-secondary text-muted-foreground border-border-subtle/10">
                                        {movie.categoryName}
                                    </Badge>
                                )}
                                {movie.season && movie.season > 0 && (
                                    <Badge className="bg-purple-500/10 text-purple-500 border-purple-500/20 shadow-[0_0_15px_rgba(168,85,247,0.2)]">
                                        <Layers className="w-3.5 h-3.5 mr-1.5"/> 第 {movie.season} 季
                                    </Badge>
                                )}
                            </div>

                            <div className="space-y-2">
                                <h1 className="text-4xl md:text-6xl lg:text-7xl font-black text-foreground tracking-tighter leading-[1.1] drop-shadow-2xl">
                                    {movie.title}
                                </h1>
                                {movie.subtitle && (
                                    <p className="text-xl md:text-2xl text-foreground/90 font-bold tracking-wide mt-2">
                                        {movie.subtitle}
                                    </p>
                                )}
                                {movie.aliasTitle && (
                                    <p className="text-lg md:text-xl text-muted-foreground font-medium tracking-wide">
                                        {movie.aliasTitle}
                                    </p>
                                )}
                                {movie.tagline && (
                                    <p className="text-lg text-muted-foreground/80 font-serif italic tracking-wide pt-2">
                                        &ldquo;{movie.tagline}&rdquo;
                                    </p>
                                )}
                            </div>

                            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3">
                                {movie.doubanId && (
                                    <ExternalLinkBadge href={`https://movie.douban.com/subject/${movie.doubanId}/`}
                                                       label="豆瓣"
                                                       color="bg-[#007722]/10 text-[#00c93c] border-[#007722]/20"/>
                                )}
                                {movie.tmdbId && (
                                    <ExternalLinkBadge
                                        href={`https://www.themoviedb.org/${movie.categoryName === '电视剧' ? 'tv' : 'movie'}/${movie.tmdbId}`}
                                        label="TMDB" color="bg-[#01b4e4]/10 text-[#00d3ea] border-[#01b4e4]/20"/>
                                )}
                                {movie.imdbId && (
                                    <ExternalLinkBadge href={`https://www.imdb.com/title/${movie.imdbId}/`} label="IMDb"
                                                       color="bg-[#f5c518]/10 text-[#f5c518] border-[#f5c518]/20"/>
                                )}
                            </div>
                        </div>

                        {/* 数据卡片 */}
                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                            <StatCard
                                label="用户评分"
                                value={movie.rating?.toString() || "N/A"}
                                icon={<Star className="w-5 h-5 fill-amber-400 text-amber-400"/>}
                                subValue="综合评分"
                                highlight
                            />
                            <StatCard
                                label="上映年份"
                                value={movie.releaseYear}
                                icon={<Calendar className="w-5 h-5 text-primary"/>}
                                subValue="首映"
                            />
                            <StatCard
                                label="片长/集数"
                                value={movie.duration || movie.totalEpisodes || "未知"}
                                icon={movie.totalEpisodes ? <ListVideo className="w-5 h-5 text-emerald-400"/> :
                                    <Clock className="w-5 h-5 text-emerald-400"/>}
                                subValue={movie.totalEpisodes ? "连载状态" : "完整版"}
                            />
                            <StatCard
                                label="画质标准"
                                value="4K HDR"
                                icon={<Monitor className="w-5 h-5 text-pink-400"/>}
                                subValue="超清"
                            />
                        </div>

                        {/* 标签云 */}
                        <div
                            className="flex flex-wrap items-center justify-center lg:justify-start gap-x-6 gap-y-3 text-sm text-muted-foreground">
                            {movie.area && (
                                <div className="flex items-center gap-2">
                                    <MapPin className="w-4 h-4 text-muted-foreground/70"/>
                                    <span>{movie.area}</span>
                                </div>
                            )}
                            {movie.language && (
                                <div className="flex items-center gap-2">
                                    <Mic2 className="w-4 h-4 text-muted-foreground/70"/>
                                    <span>{movie.language}</span>
                                </div>
                            )}
                            <div className="flex items-center gap-2">
                                <Tag className="w-4 h-4 text-muted-foreground/70"/>
                                <div className="flex gap-2">
                                    {movie.genres.map(g => (
                                        <span key={g}
                                              className="hover:text-foreground transition-colors cursor-default">{g}</span>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* 操作栏 */}
                        <div className="flex justify-center lg:justify-start pt-2">
                            <MovieActions/>
                        </div>

                        {/* 简介 */}
                        <div
                            className="bg-card/50 border border-border-subtle/10 rounded-2xl p-6 md:p-8 relative overflow-hidden group hover:bg-card/80 transition-colors">
                            <div
                                className="absolute top-0 right-0 p-4 opacity-5 group-hover:opacity-10 transition-opacity">
                                <AlignLeft className="w-24 h-24 text-foreground"/>
                            </div>
                            <h3 className="text-sm font-bold text-muted-foreground uppercase tracking-widest flex items-center gap-2 mb-4 relative z-10">
                                <span className="w-1 h-4 bg-primary rounded-full"/>
                                剧情简介
                            </h3>
                            <p className="text-base md:text-lg text-foreground/90 leading-relaxed font-normal tracking-wide relative z-10">
                                {movie.description}
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
</file>

<file path="src/components/features/MovieRail.tsx">
"use client";

import { CategorySection } from "@/lib/types";
import { MovieCard } from "./MovieCard";
import { useRef, useEffect, useState, useMemo } from "react";
import { motion, useMotionValue, animate, useMotionValueEvent } from "framer-motion";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { cn } from "@/lib/utils";

interface MovieRailProps {
    category: CategorySection;
}

export function MovieRail({ category }: MovieRailProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);

    const loopedMovies = useMemo(() => {
        if (!category.movies || category.movies.length === 0) return [];

        const MIN_ITEMS_FOR_SMOOTH_SCROLL = 24;
        const sourceLength = category.movies.length;
        let repeatCount = Math.ceil(MIN_ITEMS_FOR_SMOOTH_SCROLL / sourceLength);
        if (repeatCount < 3) repeatCount = 3;

        return Array.from({ length: repeatCount }).flatMap(() => category.movies);
    }, [category.movies]);

    const x = useMotionValue(0);
    const [unitWidth, setUnitWidth] = useState(0);
    const [isReady, setIsReady] = useState(false);

    useEffect(() => {
        if (!contentRef.current || category.movies.length === 0) return;
        const totalScrollWidth = contentRef.current.scrollWidth;
        const oneSetWidth = totalScrollWidth / (loopedMovies.length / category.movies.length);
        setUnitWidth(oneSetWidth);
        x.set(-oneSetWidth);
        const timer = setTimeout(() => setIsReady(true), 100);
        return () => clearTimeout(timer);
    }, [loopedMovies, x, category.movies.length]);

    useMotionValueEvent(x, "change", (latest) => {
        if (unitWidth === 0) return;
        if (latest > 0) {
            x.set(latest - unitWidth);
        }
        else if (latest < -2 * unitWidth) {
            x.set(latest + unitWidth);
        }
    });

    const scroll = (direction: "left" | "right") => {
        if (unitWidth === 0) return;
        const currentX = x.get();
        const containerWidth = containerRef.current?.offsetWidth || 0;
        const scrollAmount = containerWidth * 0.8;
        let startX = currentX;
        if (currentX > -0.5 * unitWidth) {
            startX = currentX - unitWidth;
            x.set(startX);
        } else if (currentX < -2.5 * unitWidth) {
            startX = currentX + unitWidth;
            x.set(startX);
        }

        const targetX = direction === "left"
            ? startX + scrollAmount
            : startX - scrollAmount;

        animate(x, targetX, {
            type: "spring",
            stiffness: 200,
            damping: 40,
            mass: 1.2
        });
    };

    if (!category.movies || category.movies.length === 0) return null;

    return (
        <div className="space-y-4 py-8 relative group/rail overflow-hidden select-none">
            {/* Header Area */}
            <div className="px-6 md:px-10 flex items-center justify-between mb-2">
                <motion.div
                    initial={{ opacity: 0, x: -10 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    className="flex items-center gap-3"
                >
                    <div className="w-1 h-5 bg-primary rounded-full shadow-[0_0_12px_rgba(99,102,241,0.5)]" />
                    <h2 className="text-xl font-bold text-foreground tracking-tight text-shadow-sm">
                        {category.title}
                    </h2>
                </motion.div>

                <div className="hidden md:block text-[10px] font-mono text-muted-foreground tracking-widest uppercase">
                    Rail-ID: {category.id.substring(0, 4)}
                </div>
            </div>

            {/* Scroll Container */}
            <div ref={containerRef} className="relative group/content">
                <motion.div
                    ref={contentRef}
                    style={{ x, opacity: isReady ? 1 : 0 }}
                    drag="x"
                    dragElastic={0.05}
                    onDragEnd={() => {}}
                    whileTap={{ cursor: "grabbing" }}
                    className="flex gap-3 md:gap-4 px-6 md:px-10 cursor-grab touch-pan-y relative z-10 w-max py-2"
                >
                    {loopedMovies.map((movie, idx) => (
                        <div key={`${movie.id}-loop-${idx}`} className="w-[130px] sm:w-[160px] md:w-[190px] flex-none pointer-events-none md:pointer-events-auto">
                            <MovieCard movie={movie} index={idx % category.movies.length} />
                        </div>
                    ))}
                </motion.div>

                {/*
                   视觉边界 - 使用 from-background 适配双色主题
                */}
                <div
                    className={cn(
                        "absolute left-0 top-0 bottom-0 w-32 z-30 pointer-events-none flex items-center justify-start pl-4 transition-opacity duration-500",
                        "bg-gradient-to-r from-background via-background/90 to-transparent",
                        "opacity-0 group-hover/rail:opacity-100"
                    )}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            scroll("left");
                        }}
                        className="pointer-events-auto p-3 rounded-full bg-card/80 backdrop-blur-md border border-border-subtle/10 text-foreground hover:bg-foreground hover:text-background transition-all shadow-xl hover:scale-110 active:scale-95 hidden md:flex"
                        aria-label="Previous"
                    >
                        <ChevronLeft className="w-5 h-5" />
                    </button>
                </div>

                {/* Right Boundary */}
                <div
                    className={cn(
                        "absolute right-0 top-0 bottom-0 w-32 z-30 pointer-events-none flex items-center justify-end pr-4 transition-opacity duration-500",
                        "bg-gradient-to-l from-background via-background/90 to-transparent",
                        "opacity-0 group-hover/rail:opacity-100"
                    )}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            scroll("right");
                        }}
                        className="pointer-events-auto p-3 rounded-full bg-card/80 backdrop-blur-md border border-border-subtle/10 text-foreground hover:bg-foreground hover:text-background transition-all shadow-xl hover:scale-110 active:scale-95 hidden md:flex"
                        aria-label="Next"
                    >
                        <ChevronRight className="w-5 h-5" />
                    </button>
                </div>
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/search/SearchClient.tsx">
"use client";

import { useState } from "react";
import { Search as SearchIcon, SlidersHorizontal, Trash2, ChevronDown, Loader2 } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import dynamic from 'next/dynamic';

import { MovieCard as MovieCardType, PortalMetadata } from "@/lib/types";
import { cn } from "@/lib/utils";
import { MovieCard } from "@/components/features/MovieCard";
import { DualRangeSlider } from "@/components/ui/DualRangeSlider";

// Hook
import { useContentSearch, FilterState, DEFAULT_VALUE, DEFAULT_CATEGORY_SLUG, DEFAULT_SORT } from "@/hooks/useContentSearch";

const FilterModal = dynamic(
    () => import('@/components/features/search/FilterModal').then(mod => mod.FilterModal),
    { ssr: false }
);

interface SearchClientProps {
    initialResults: MovieCardType[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
}

export function SearchClient(props: SearchClientProps) {
    const {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        filterGroups,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption
    } = useContentSearch(props);

    // UI State
    const [showFilters, setShowFilters] = useState(true);
    const [activeModalGroup, setActiveModalGroup] = useState<string | null>(null);

    const activeGroupConfig = activeModalGroup ? filterGroups.find(g => g.key === activeModalGroup) : null;
    const isDefaultState = !query &&
        filters.category === DEFAULT_CATEGORY_SLUG &&
        filters.sort === DEFAULT_SORT &&
        filters.genre === DEFAULT_VALUE &&
        filters.area === DEFAULT_VALUE &&
        filters.year === DEFAULT_VALUE &&
        filters.language === DEFAULT_VALUE;

    // Helper: Parse range value for slider
    const parseRangeValue = (val: string, min: number, max: number): [number, number] => {
        if (!val || val === DEFAULT_VALUE || val === "all") return [min, max];

        if (val.includes("-")) {
            const [s, e] = val.split("-").map(Number);
            return [s || min, e || max];
        }

        // Handle "lt1990" legacy format
        if (val.startsWith("lt")) {
            const limit = Number(val.replace("lt", ""));
            return [min, limit - 1];
        }

        const single = Number(val);
        if (!isNaN(single) && single > 0) {
            return [single, single];
        }

        return [min, max];
    };

    return (
        <div className="w-full flex flex-col gap-8">
            {/* --- 顶部搜索栏 --- */}
            <div className="relative group max-w-5xl mx-auto w-full z-30">
                <SearchIcon className="absolute left-0 top-1/2 -translate-y-1/2 w-6 h-6 text-neutral-500 transition-colors group-focus-within:text-white" />
                <input
                    type="text"
                    placeholder="搜索电影、剧集、动漫... (支持 4K/HDR 关键词)"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    className="w-full bg-transparent border-b border-white/10 py-4 pl-10 text-xl md:text-3xl font-bold text-white placeholder:text-neutral-700 focus:outline-none focus:border-indigo-500 transition-colors"
                />

                <div className="absolute right-0 top-1/2 -translate-y-1/2 flex items-center gap-4">
                    {!isDefaultState && (
                        <button
                            onClick={() => resetFilters()}
                            className="flex items-center gap-2 text-xs md:text-sm text-neutral-500 hover:text-red-400 transition-colors"
                        >
                            <Trash2 className="w-4 h-4" />
                            <span className="hidden md:inline">重置</span>
                        </button>
                    )}
                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className={cn(
                            "flex items-center gap-2 text-xs md:text-sm px-4 py-2 rounded-full border transition-all select-none",
                            showFilters
                                ? "bg-indigo-500/10 text-indigo-400 border-indigo-500/30"
                                : "bg-white/5 text-neutral-500 border-white/10 hover:text-white"
                        )}
                    >
                        <SlidersHorizontal className="w-4 h-4" />
                        筛选
                    </button>
                </div>
            </div>

            {/* --- 筛选面板 --- */}
            <AnimatePresence>
                {showFilters && (
                    <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: "auto", opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        className="w-full max-w-7xl mx-auto overflow-hidden"
                    >
                        <div className="bg-[#0a0a0c] border border-white/5 rounded-2xl p-6 space-y-5">
                            {filterGroups.map((group) => {
                                // Special Handling for Year: Use Slider
                                if (group.key === "year") {
                                    const currentYear = new Date().getFullYear() + 1;
                                    const minYear = 1900;
                                    const maxYear = currentYear;
                                    const [currentStart, currentEnd] = parseRangeValue(
                                        filters.year,
                                        minYear,
                                        maxYear
                                    );

                                    return (
                                        <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start sm:items-center">
                                            <span className="text-[10px] md:text-xs font-bold text-neutral-500 uppercase tracking-wider w-12 pt-1.5 sm:pt-0 shrink-0">
                                                {group.label}
                                            </span>
                                            <div className="flex-1 w-full max-w-xl px-2">
                                                <DualRangeSlider
                                                    min={minYear}
                                                    max={maxYear}
                                                    value={[currentStart, currentEnd]}
                                                    onChange={() => {}} // 仅仅是视觉更新，不实时触发搜索
                                                    onCommit={(val) => {
                                                        if (val[0] === minYear && val[1] === maxYear) {
                                                            handleFilterChange(group.key, DEFAULT_VALUE);
                                                        } else if (val[0] === val[1]) {
                                                            handleFilterChange(group.key, String(val[0]));
                                                        } else {
                                                            handleFilterChange(group.key, `${val[0]}-${val[1]}`);
                                                        }
                                                    }}
                                                />
                                            </div>
                                        </div>
                                    );
                                }

                                // Standard Buttons for other filters
                                const VISIBLE_LIMIT = 12;
                                const hasMore = group.options.length > VISIBLE_LIMIT;
                                const visibleOptions = group.options.slice(0, VISIBLE_LIMIT);

                                return (
                                    <div key={group.key} className="flex flex-col sm:flex-row gap-3 sm:gap-6 items-start sm:items-center">
                                        <span className="text-[10px] md:text-xs font-bold text-neutral-500 uppercase tracking-wider w-12 pt-1.5 sm:pt-0 shrink-0">
                                            {group.label}
                                        </span>
                                        <div className="flex flex-wrap gap-2 flex-1">
                                            {visibleOptions.map((opt) => {
                                                // [FIX]: Ensure type safety when accessing filters
                                                const isSelected = filters[group.key as keyof FilterState] === opt;
                                                return (
                                                    <button
                                                        key={opt}
                                                        onClick={() => handleFilterChange(group.key, opt)}
                                                        className={cn(
                                                            "px-3 py-1.5 rounded-lg text-xs transition-all border select-none",
                                                            isSelected
                                                                ? "bg-indigo-600 border-indigo-500 text-white shadow-lg shadow-indigo-500/20"
                                                                : "bg-white/5 border-white/5 text-neutral-400 hover:bg-white/10 hover:text-white hover:border-white/10"
                                                        )}
                                                    >
                                                        {getDisplayOption(group.key, opt)}
                                                    </button>
                                                );
                                            })}
                                            {hasMore && (
                                                <button
                                                    onClick={() => setActiveModalGroup(group.key)}
                                                    className="px-3 py-1.5 rounded-lg text-xs bg-white/5 border border-white/5 text-indigo-400 hover:bg-white/10 hover:text-white hover:border-white/20 transition-all flex items-center gap-1"
                                                >
                                                    更多 <ChevronDown className="w-3 h-3" />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* --- 结果展示区 --- */}
            <div className="flex-1 max-w-7xl mx-auto w-full flex flex-col gap-6 relative min-h-[400px]">

                {/* 结果统计与排序 */}
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-white/5 pb-4">
                    <p className="text-sm text-neutral-500 font-mono flex items-center gap-2">
                        {isLoading ? (
                            <span className="flex items-center gap-2 text-indigo-400 animate-pulse">
                                <Loader2 className="w-3 h-3 animate-spin" /> SEARCHING...
                            </span>
                        ) : (
                            <span>RESULT: <span className="text-white font-bold">{totalResults}</span> ITEMS</span>
                        )}
                    </p>
                    <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                        {["latest", "rating", "trending"].map(sortKey => (
                            <button
                                key={sortKey}
                                onClick={() => handleFilterChange("sort", sortKey)}
                                className={cn(
                                    "text-xs px-3 py-1.5 rounded-md transition-all font-bold",
                                    filters.sort === sortKey
                                        ? "bg-white/10 text-white shadow-sm"
                                        : "text-neutral-500 hover:text-neutral-300 hover:bg-white/5"
                                )}
                            >
                                {sortKey === "latest" ? "最新" : sortKey === "rating" ? "评分" : "热门"}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Grid 列表 */}
                <AnimatePresence mode="popLayout">
                    {results.length > 0 ? (
                        <motion.div
                            layout
                            className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7 gap-3 md:gap-4 md:gap-y-8"
                        >
                            {results.map((movie, idx) => (
                                <motion.div
                                    layout
                                    key={movie.id}
                                    initial={{ opacity: 0, scale: 0.95 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    exit={{ opacity: 0, scale: 0.9 }}
                                    transition={{ duration: 0.2, delay: 0 }}
                                >
                                    <MovieCard movie={movie} index={idx} />
                                </motion.div>
                            ))}
                        </motion.div>
                    ) : (
                        !isLoading && (
                            <motion.div
                                initial={{ opacity: 0 }}
                                animate={{ opacity: 1 }}
                                className="flex flex-col items-center justify-center py-20 text-neutral-600 border border-dashed border-white/5 rounded-2xl bg-white/[0.01]"
                            >
                                <SearchIcon className="w-12 h-12 mb-4 opacity-20" />
                                <p className="text-lg font-medium text-neutral-400">没有找到匹配内容</p>
                                <p className="text-sm opacity-60 mt-2">
                                    尝试搜索 &#34;4K&#34;、&#34;Netflix&#34; 或调整筛选条件
                                </p>
                            </motion.div>
                        )
                    )}
                </AnimatePresence>
            </div>

            {activeGroupConfig && (
                <FilterModal
                    isOpen={!!activeModalGroup}
                    onClose={() => setActiveModalGroup(null)}
                    title={activeGroupConfig.label}
                    options={activeGroupConfig.options}
                    currentValue={filters[activeGroupConfig.key as keyof FilterState] || ""}
                    onSelect={(val) => handleFilterChange(activeGroupConfig.key, val)}
                    formatLabel={(val) => getDisplayOption(activeGroupConfig.key, val)}
                />
            )}
        </div>
    );
}
</file>

<file path="src/components/features/VideoPlayer.tsx">
"use client";

import { useMemo } from "react";
import { VidstackPlayer } from "./player/VidstackPlayer";
import { IframePlayer } from "./player/IframePlayer";
import { VideoFormat } from "@/lib/types";
import { VideoDomain } from "@/lib/domain/video";

interface VideoPlayerProps {
    src?: string;       // 实际播放地址
    posterUrl?: string; // 封面图
    unifiedVideoId?: string; // 聚合 ID
    videoId?: string;   // 具体的源视频 ID
    episodeId?: string; // 具体的集数 ID
    episodeName?: string; // 集数名称
    title?: string;     // 视频标题
    initialTime?: number; // 初始播放时间 (秒)
}

export function VideoPlayer({
                                src,
                                posterUrl,
                                unifiedVideoId,
                                videoId,
                                episodeId,
                                episodeName,
                                title = "Video",
                                initialTime = 0
                            }: VideoPlayerProps) {

    // 1. 策略分发：检测格式 (Delegated to Domain Service)
    const format: VideoFormat = useMemo(() => {
        return VideoDomain.detectFormat(src || "");
    }, [src]);

    // 2. 空状态处理
    if (!src) {
        return (
            <div className="w-full h-full bg-black rounded-xl flex items-center justify-center text-neutral-500 border border-white/10">
                <span className="text-sm">暂无播放资源</span>
            </div>
        );
    }

    // 3. 渲染具体的播放器内核
    if (format === 'hls' || format === 'mp4') {
        return (
            <VidstackPlayer
                src={src}
                poster={posterUrl}
                title={title}
                unifiedVideoId={unifiedVideoId}
                videoId={videoId}
                episodeId={episodeId}
                episodeName={episodeName}
                initialTime={initialTime}
            />
        );
    }

    return (
        <IframePlayer
            src={src}
            title={title}
            unifiedVideoId={unifiedVideoId}
            videoId={videoId}
            episodeId={episodeId}
            episodeName={episodeName}
            initialTime={initialTime}
        />
    );
}
</file>

<file path="src/components/features/WatchSidebar.tsx">
"use client";

import { useMemo, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ChevronRight, ChevronLeft, Layers, PlayCircle, Server } from "lucide-react";
import { Episode, MovieDetail } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";
import { VideoDomain } from "@/lib/domain/video";

interface WatchSidebarProps {
    movie: MovieDetail;
    currentSourceId: string;
    currentEpisodeId: string;
}

const EpisodeButton = ({
                           ep,
                           isNative,
                           isPlaying,
                           onClick
                       }: {
    ep: Episode,
    isNative: boolean,
    isPlaying: boolean,
    onClick: () => void
}) => {
    return (
        <button
            key={ep.id}
            onClick={onClick}
            className={cn(
                "relative aspect-square rounded-lg flex flex-col items-center justify-center p-2 border transition-all group overflow-hidden",
                isPlaying
                    ? "bg-primary/20 border-primary/50 text-primary"
                    : "bg-secondary/30 border-border-subtle/10 text-muted-foreground hover:bg-secondary hover:border-border-subtle/30 hover:text-foreground"
            )}
        >
            <span className="relative z-10 text-xs font-medium truncate w-full text-center">
                {ep.title.replace(/第|集/g, '')}
            </span>
            {isPlaying && (
                <div className="absolute inset-0 flex items-center justify-center opacity-20">
                    <PlayCircle className="w-8 h-8" />
                </div>
            )}
            {isPlaying && (
                <div className="absolute bottom-1 w-1 h-1 bg-primary rounded-full animate-pulse" />
            )}
            {isNative && !isPlaying && (
                <div className="absolute top-1 right-1 w-1 h-1 bg-primary/50 rounded-full" />
            )}
        </button>
    );
};

// --- 主组件 ---

export function WatchSidebar({ movie, currentSourceId, currentEpisodeId }: WatchSidebarProps) {
    const router = useRouter();
    const [isOpen, setIsOpen] = useState(true);
    const [activeSourceId, setActiveSourceId] = useState(currentSourceId);

    const activeSource = movie.sources.find(s => s.id === activeSourceId) || movie.sources[0];

    // Use Domain Logic
    const episodeGroups = useMemo(() => {
        if (!activeSource) return { native: [], external: [] };
        return VideoDomain.groupEpisodes(activeSource.episodes);
    }, [activeSource]);

    const handleEpisodeClick = (epId: string) => {
        router.push(`/watch/${movie.id}?source=${activeSourceId}&episode=${epId}`);
    };

    return (
        <>
            <AnimatePresence>
                {!isOpen && (
                    <motion.button
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        exit={{ opacity: 0, x: 20 }}
                        onClick={() => setIsOpen(true)}
                        className="fixed right-0 top-1/2 -translate-y-1/2 z-40 bg-card/80 backdrop-blur-md border-l border-y border-border-subtle/10 p-3 rounded-l-xl text-foreground hover:bg-primary hover:border-primary hover:text-white transition-all shadow-lg"
                    >
                        <ChevronLeft className="w-5 h-5" />
                    </motion.button>
                )}
            </AnimatePresence>

            <motion.div
                initial={{ x: "100%" }}
                animate={{ x: isOpen ? 0 : "100%" }}
                transition={{ type: "spring", stiffness: 300, damping: 30 }}
                className="fixed top-20 bottom-4 right-4 z-40 w-80 flex flex-col pointer-events-none"
            >
                {/* 背景色使用 bg-card/95，确保在亮色下也是实体背景 */}
                <div className="flex-1 flex flex-col bg-card/95 backdrop-blur-xl border border-border-subtle/10 rounded-2xl shadow-2xl overflow-hidden pointer-events-auto">

                    <div className="flex items-center justify-between p-4 border-b border-border-subtle/10 bg-secondary/5">
                        <div className="flex items-center gap-2 text-sm font-bold text-foreground">
                            <Layers className="w-4 h-4 text-primary" />
                            选集列表
                        </div>
                        <button
                            onClick={() => setIsOpen(false)}
                            className="p-1 text-muted-foreground hover:text-foreground transition-colors"
                        >
                            <ChevronRight className="w-5 h-5" />
                        </button>
                    </div>

                    <div className="p-3 bg-secondary/10">
                        <div className="flex items-center gap-2 mb-2 text-[10px] font-bold text-muted-foreground uppercase tracking-widest px-1">
                            <Server className="w-3 h-3" /> 播放源
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {movie.sources.map((source) => (
                                <button
                                    key={source.id}
                                    onClick={() => setActiveSourceId(source.id)}
                                    className={cn(
                                        "px-3 py-1.5 rounded-lg text-xs font-medium transition-all border",
                                        activeSourceId === source.id
                                            ? "bg-primary border-primary text-white shadow-lg shadow-primary/30"
                                            : "bg-secondary/30 border-border-subtle/10 text-muted-foreground hover:bg-secondary hover:text-foreground"
                                    )}
                                >
                                    {source.name}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-6">
                        {/* 直连分组 */}
                        {episodeGroups.native.length > 0 && (
                            <div>
                                <div className="flex items-center gap-1.5 mb-2 px-1">
                                    <PlayCircle className="w-3 h-3 text-primary" />
                                    <span className="text-xs font-bold text-muted-foreground">极速播放</span>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                    {episodeGroups.native.map(ep => (
                                        <EpisodeButton
                                            key={ep.id}
                                            ep={ep}
                                            isNative={true}
                                            isPlaying={activeSourceId === currentSourceId && ep.id === currentEpisodeId}
                                            onClick={() => handleEpisodeClick(ep.id)}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* 分割线（如果两个都有） */}
                        {episodeGroups.native.length > 0 && episodeGroups.external.length > 0 && (
                            <div className="h-px bg-border-subtle/10 mx-2" />
                        )}

                        {/* 外链分组 */}
                        {episodeGroups.external.length > 0 && (
                            <div>
                                <div className="flex items-center gap-1.5 mb-2 px-1">
                                    <Layers className="w-3 h-3 text-muted-foreground" />
                                    <span className="text-xs font-bold text-muted-foreground">网页播放</span>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                    {episodeGroups.external.map(ep => (
                                        <EpisodeButton
                                            key={ep.id}
                                            ep={ep}
                                            isNative={false}
                                            isPlaying={activeSourceId === currentSourceId && ep.id === currentEpisodeId}
                                            onClick={() => handleEpisodeClick(ep.id)}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="p-3 border-t border-border-subtle/10 bg-secondary/5 text-[10px] text-muted-foreground text-center">
                        {movie.title} • {activeSource?.name}
                    </div>
                </div>
            </motion.div>
        </>
    );
}
</file>

<file path="src/components/ui/QuantumImage.tsx">
"use client";

import Image, { ImageProps } from "next/image";
import { useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { ImageOff } from "lucide-react";

interface QuantumImageProps extends Omit<ImageProps, "onLoad" | "onError" | "priority"> {
    containerClassName?: string;
    fallbackText?: string;
    preload?: boolean;
}

export function QuantumImage({
                                 src,
                                 alt,
                                 className,
                                 containerClassName,
                                 fallbackText = "画外",
                                 preload = false,
                                 sizes,
                                 ...props
                             }: QuantumImageProps) {
    const hasValidSrc = useMemo(() => {
        if (!src || typeof src !== "string" || src.trim() === "") return false;
        if (src.startsWith("/")) return true;
        if (src.startsWith("data:image")) return true;
        try {
            new URL(src);
            return true;
        } catch (e) {
            return false;
        }
    }, [src]);

    const [currentSrc, setCurrentSrc] = useState(src);
    const [status, setStatus] = useState<"loading" | "success" | "error">("loading");

    if (src !== currentSrc) {
        setCurrentSrc(src);
        setStatus(hasValidSrc ? "loading" : "error");
    }

    const defaultSizes = props.fill
        ? "(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
        : undefined;

    return (
        <div className={cn("relative overflow-hidden bg-neutral-900 w-full h-full isolate", containerClassName)}>
            <AnimatePresence mode="popLayout">
                {status === "loading" && hasValidSrc && (
                    <motion.div
                        key="skeleton"
                        initial={{ opacity: 1 }}
                        exit={{ opacity: 0, transition: { duration: 0.8, ease: "easeOut" } }}
                        className="absolute inset-0 z-20 flex items-center justify-center bg-neutral-900"
                    >
                        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent -translate-x-full animate-[shimmer_1.5s_infinite]" />
                        <div className="w-full h-full bg-neutral-800/50" />
                    </motion.div>
                )}
            </AnimatePresence>

            {(!hasValidSrc || status === "error") && (
                <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-neutral-900 p-4 border border-white/5">
                    <ImageOff className="w-8 h-8 text-neutral-700 mb-2" />
                    <span className="text-xs text-neutral-600 font-medium tracking-widest uppercase">
                        {fallbackText}
                    </span>
                    <div className="absolute inset-0 opacity-20 bg-[url('https://grainy-gradients.vercel.app/noise.svg')]" />
                </div>
            )}

            {hasValidSrc && (
                <Image
                    src={src}
                    alt={alt}
                    priority={preload}
                    sizes={sizes || defaultSizes}
                    className={cn(
                        "transition-all duration-1000 ease-out will-change-transform relative z-10",
                        status === "success"
                            ? "opacity-100 scale-100 blur-0 grayscale-0"
                            : "opacity-0 scale-110 blur-xl grayscale",
                        className
                    )}
                    onLoad={() => setStatus("success")}
                    onError={() => setStatus("error")}
                    {...props}
                />
            )}
        </div>
    );
}
</file>

<file path="src/lib/seo.ts">
import { MovieDetail } from "@/lib/types";

export const SITE_URL = "https://huawai.sophia.fr.eu.org";
export const SITE_NAME = "画外 Huawai";
export const BRAND_SLOGAN = "画里是故事，画外是生活";

/**
 * 将时长字符串 (例如 "120", "120 min", "02:00:00") 转换为 ISO 8601 格式 (PT2H)
 * 搜索引擎需要严格的 ISO 格式来显示视频时长富摘要
 */
function toIsoDuration(input?: string): string {
    if (!input) return "PT0M";

    // 情况 A: 时间戳格式 HH:MM:SS 或 MM:SS
    if (input.includes(":")) {
        const parts = input.split(":").map((p) => parseInt(p, 10) || 0);
        let h = 0, m = 0, s = 0;

        if (parts.length === 3) {
            [h, m, s] = parts;
        } else if (parts.length === 2) {
            [m, s] = parts;
        } else {
            return "PT0M";
        }
        return `PT${h}H${m}M${s}S`;
    }

    // 情况 B: 纯数字或带文本 (提取数字，默认为分钟)
    const digits = input.replace(/[^\d]/g, "");
    if (!digits) return "PT0M";

    const totalMinutes = parseInt(digits, 10);
    if (isNaN(totalMinutes)) return "PT0M";

    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;

    return `PT${hours}H${minutes}M`;
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

/**
 * 生成电影/剧集实体 Schema
 * 包含评分、上映日期、状态等关键信息，有助于出星级评分摘要
 */
export function generateMovieSchema(movie: MovieDetail) {
    const isSeries = movie.categoryName?.includes("剧") || movie.categoryName === "动漫";
    const dateModified = movie.lastTrendAt || new Date().toISOString();

    return {
        "@context": "https://schema.org",
        "@type": isSeries ? "TVSeries" : "Movie",
        "name": movie.title,
        "alternateName": [movie.aliasTitle, `${movie.title}在线观看`, `${movie.title}下载`].filter(Boolean),
        "image": movie.posterUrl,
        "description": movie.description,
        "datePublished": movie.releaseYear,
        "dateModified": dateModified,
        "inLanguage": "zh-CN",
        "countryOfOrigin": movie.area,
        "isFamilyFriendly": true,
        "provider": {
            "@type": "Organization",
            "name": SITE_NAME,
            "sameAs": SITE_URL
        },
        "offers": {
            "@type": "Offer",
            "availability": "https://schema.org/InStock",
            "price": "0",
            "priceCurrency": "CNY",
            "description": "免费4K HDR高清播放"
        },
        "aggregateRating": {
            "@type": "AggregateRating",
            "ratingValue": movie.rating > 0 ? movie.rating : "8.5", // 兜底策略：没有评分时给一个高分以激活星星
            "ratingCount": "1024", // 模拟基数
            "bestRating": "10",
            "worstRating": "1"
        },
        "genre": movie.genres
    };
}

/**
 * 生成视频对象 Schema
 * 针对 Google 的 "Video" tab 优化，增加 interactionStatistic
 */
export function generateVideoObjectSchema(movie: MovieDetail) {
    const uploadDate = movie.lastTrendAt || new Date().toISOString();
    const isoDuration = toIsoDuration(movie.duration);

    return {
        "@context": "https://schema.org",
        "@type": "VideoObject",
        "name": `在 ${SITE_NAME} 观看《${movie.title}》- 4K HDR`,
        "description": `免费在线观看《${movie.title}》完整版。${movie.description ? movie.description.substring(0, 150) : ''}...`,
        "thumbnailUrl": [movie.posterUrl],
        "uploadDate": uploadDate,
        "duration": isoDuration,
        "contentUrl": `${SITE_URL}/watch/${movie.id}`,
        "embedUrl": `${SITE_URL}/watch/${movie.id}`,
        "playerType": "HTML5",
        "regionsAllowed": "CN,HK,TW,US,JP,SG",
        "interactionStatistic": {
            "@type": "InteractionCounter",
            "interactionType": { "@type": "WatchAction" },
            "userInteractionCount": 5600 // 静态模拟数据，增加权威感
        }
    };
}

/**
 * 生成 FAQ Schema
 * 专门捕获 "在哪里看"、"怎么下载" 等长尾搜索意图
 * 占据 SERP 更多垂直像素
 */
export function generateFAQSchema(movie: MovieDetail) {
    const typeName = movie.categoryName || "影片";
    return {
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "mainEntity": [
            {
                "@type": "Question",
                "name": `《${movie.title}》在哪里可以免费在线观看？`,
                "acceptedAnswer": {
                    "@type": "Answer",
                    "text": `您可以在 ${SITE_NAME} (Huawai) 免费观看《${movie.title}》的完整版，本站提供 4K HDR 及 1080P 高清无广告画质，支持手机和电脑端流畅播放。`
                }
            },
            {
                "@type": "Question",
                "name": `《${movie.title}》有迅雷下载或百度云资源吗？`,
                "acceptedAnswer": {
                    "@type": "Answer",
                    "text": `${SITE_NAME} 已收录《${movie.title}》的高速磁力下载链接（支持迅雷），同时也提供了夸克网盘、阿里云盘等云存储资源，点击“下载/网盘”按钮即可获取。`
                }
            },
            {
                "@type": "Question",
                "name": `《${movie.title}》讲了什么故事？`,
                "acceptedAnswer": {
                    "@type": "Answer",
                    "text": `《${movie.title}》是 ${movie.releaseYear} 年上映的 ${movie.area} ${typeName}，由 ${movie.cast?.slice(0, 3).map(c => c.name).join("、")} 等主演。${movie.description?.substring(0, 50)}...`
                }
            }
        ]
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
</file>

<file path="next.config.ts">
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',

  images: {
    formats: ['image/avif', 'image/webp'],
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'img.mnnu.eu.org',
        port: '',
        pathname: '/**',
      },
      { protocol: 'https', hostname: '*.*' },
      { protocol: 'http', hostname: '*.*' },
      { protocol: 'https', hostname: 'imgwolong.com' },
      { protocol: 'http', hostname: 'imgwolong.com' },
      { protocol: 'https', hostname: '*.imgwolong.com' },
      { protocol: 'https', hostname: 'image.tmdb.org' },
      { protocol: 'https', hostname: 'm.media-amazon.com' },
      { protocol: 'https', hostname: 't.mwm.moe' },
      { protocol: 'https', hostname: 'img.liangzipic.com' },
      { protocol: 'https', hostname: 'img.ffzyimg.com' },
      { protocol: 'https', hostname: 'pic.rmb.bdstatic.com' },
      { protocol: 'https', hostname: 'img.kuaikanzy.net' },
      { protocol: 'https', hostname: 'img1.doubanio.com' },
      { protocol: 'https', hostname: 'img2.doubanio.com' },
      { protocol: 'https', hostname: 'img3.doubanio.com' },
      { protocol: 'https', hostname: 'img9.doubanio.com' },
    ],
    deviceSizes: [640, 750, 1080, 1200, 1920],
    minimumCacheTTL: 3600 * 24,
  },

  allowedDevOrigins: [
    "localhost:*",
    "127.0.0.1:*",
    "192.168.*.*"
  ],

};

export default nextConfig;
</file>

<file path="src/app/feedback/page.tsx">
"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Send, MessageSquare, Loader2, Info, Clock, Globe, User as UserIcon, Trash2 } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { interactionApi, UserMessage } from "@/lib/api/interaction";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { useRouter } from "next/navigation";
import { cn, getErrorMessage } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { User } from "@/lib/types";

export default function FeedbackPage() {
    const { user } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();

    // Tab state: 'mine' | 'wall'
    const [activeTab, setActiveTab] = useState<'mine' | 'wall'>('mine');

    // Submission State
    const [content, setContent] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    // List State
    const [messages, setMessages] = useState<UserMessage[]>([]);
    const [wallMessages, setWallMessages] = useState<UserMessage[]>([]);
    const [isListLoading, setIsListLoading] = useState(false);
    const [isWallLoading, setIsWallLoading] = useState(false);

    // Fetch My Messages
    useEffect(() => {
        if (activeTab === 'mine' && user) {
            setIsListLoading(true);
            interactionApi.getMyMessages(0, 20)
                .then(res => setMessages(res.content))
                .catch(console.error)
                .finally(() => setIsListLoading(false));
        }
    }, [user, activeTab]);

    // Fetch Public Wall (No Auth Required)
    useEffect(() => {
        if (activeTab === 'wall') {
            setIsWallLoading(true);
            interactionApi.getPublicWall(0, 50)
                .then(res => setWallMessages(res.content))
                .catch(console.error)
                .finally(() => setIsWallLoading(false));
        }
    }, [activeTab]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!user) {
            addToast("请先登录", "error");
            router.push("/login");
            return;
        }
        if (!content.trim()) return;

        setIsSubmitting(true);
        try {
            const newMessage = await interactionApi.submitMessage(content);
            setMessages(prev => [newMessage, ...prev]);
            setContent("");
            addToast("反馈提交成功", "success");
            setActiveTab('mine'); // Auto switch to mine to see result
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    // [New] Delete Handler
    const handleDelete = async (id: string) => {
        if (!confirm("确定要删除这条留言吗？")) return;

        try {
            await interactionApi.deleteMessage(id);
            setMessages(prev => prev.filter(m => m.id !== id));
            addToast("删除成功", "success");
        } catch (err) {
            addToast(getErrorMessage(err), "error");
        }
    };

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-4xl mx-auto relative z-10 grid grid-cols-1 md:grid-cols-3 gap-8">
                <div className="md:col-span-1 space-y-6">
                    <div>
                        <h1 className="text-3xl font-bold text-foreground mb-2 flex items-center gap-3">
                            <MessageSquare className="w-8 h-8 text-primary" />
                            留言反馈
                        </h1>
                        <p className="text-muted-foreground text-sm">
                            遇到了问题？想看新片？<br/>请告诉我们，管理员会尽快回复。
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="relative">
                            <textarea
                                value={content}
                                onChange={(e) => setContent(e.target.value)}
                                placeholder={user ? "请描述您的问题或需求..." : "请先登录后留言..."}
                                className="w-full h-40 bg-secondary/50 border border-border-subtle/10 rounded-2xl p-4 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all resize-none text-sm"
                                maxLength={500}
                                disabled={!user}
                                required
                            />
                            <div className="absolute bottom-3 right-3 text-xs text-muted-foreground/60">
                                {content.length}/500
                            </div>
                        </div>
                        <GlowButton
                            className="w-full"
                            icon={isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                            disabled={isSubmitting || !content.trim() || !user}
                        >
                            {isSubmitting ? "提交中..." : (user ? "发送留言" : "登录后留言")}
                        </GlowButton>
                    </form>

                    <div className="p-4 bg-primary/10 border border-primary/20 rounded-xl">
                        <h4 className="text-primary font-bold text-sm mb-2 flex items-center gap-2">
                            <Info className="w-4 h-4" /> 温馨提示
                        </h4>
                        <ul className="text-xs text-primary/80 space-y-1 list-disc pl-4">
                            <li>每日限提交 5 条留言</li>
                            <li>求片请提供准确的片名和年份</li>
                            <li>优秀建议将被展示在留言墙上</li>
                        </ul>
                    </div>
                </div>

                <div className="md:col-span-2">
                    <div className="flex items-center gap-4 mb-6 border-b border-border-subtle/10 pb-4">
                        <button
                            onClick={() => setActiveTab('mine')}
                            className={cn(
                                "flex items-center gap-2 pb-4 -mb-4 border-b-2 text-sm font-bold transition-all",
                                activeTab === 'mine'
                                    ? "text-foreground border-primary"
                                    : "text-muted-foreground border-transparent hover:text-foreground"
                            )}
                        >
                            <Clock className="w-4 h-4" />
                            我的记录
                        </button>
                        <button
                            onClick={() => setActiveTab('wall')}
                            className={cn(
                                "flex items-center gap-2 pb-4 -mb-4 border-b-2 text-sm font-bold transition-all",
                                activeTab === 'wall'
                                    ? "text-foreground border-primary"
                                    : "text-muted-foreground border-transparent hover:text-foreground"
                            )}
                        >
                            <Globe className="w-4 h-4" />
                            留言墙
                        </button>
                    </div>

                    <div className="min-h-[400px]">
                        {activeTab === 'mine' ? (
                            <MyMessagesList loading={isListLoading} messages={messages} user={user} onDelete={handleDelete} />
                        ) : (
                            <PublicWallList loading={isWallLoading} messages={wallMessages} />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

function MyMessagesList({ loading, messages, user, onDelete }: { loading: boolean, messages: UserMessage[], user: User | null, onDelete: (id: string) => void }) {
    if (!user) {
        return (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <UserIcon className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">登录后查看我的历史记录</p>
            </div>
        );
    }

    if (loading) {
        return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>;
    }

    if (messages.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 border border-dashed border-border-subtle/10 rounded-2xl bg-secondary/5 text-muted-foreground">
                <MessageSquare className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">暂无留言记录</p>
            </div>
        );
    }

    return (
        <div className="space-y-4">
            <AnimatePresence mode="popLayout">
                {messages.map((msg) => (
                    <motion.div
                        key={msg.id}
                        layout
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9 }}
                        className="bg-card border border-border-subtle/10 rounded-2xl p-5 hover:border-border-subtle/20 transition-all shadow-sm group"
                    >
                        <div className="flex justify-between items-start mb-3">
                            <div className="flex items-center gap-2">
                                <span className={cn(
                                    "w-2 h-2 rounded-full",
                                    msg.status === 'REPLIED' ? "bg-green-500 shadow-[0_0_8px_#22c55e]" :
                                        msg.status === 'CLOSED' ? "bg-muted-foreground" : "bg-amber-500 shadow-[0_0_8px_#f59e0b]"
                                )} />
                                <span className="text-xs font-bold text-muted-foreground">
                                    {new Date(msg.createdAt).toLocaleString()}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className={cn(
                                    "text-[10px] px-2 py-0.5 rounded border",
                                    msg.status === 'REPLIED' ? "bg-green-500/10 text-green-500 border-green-500/20" :
                                        msg.status === 'CLOSED' ? "bg-secondary text-muted-foreground border-border" :
                                            "bg-amber-500/10 text-amber-500 border-amber-500/20"
                                )}>
                                    {msg.status === 'REPLIED' ? '已回复' : msg.status === 'CLOSED' ? '已关闭' : '待处理'}
                                </span>
                                <button
                                    onClick={() => onDelete(msg.id)}
                                    className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                                    title="删除留言"
                                >
                                    <Trash2 className="w-3.5 h-3.5" />
                                </button>
                            </div>
                        </div>

                        <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                            {msg.content}
                        </p>

                        {msg.reply && (
                            <div className="mt-4 pt-4 border-t border-border-subtle/10">
                                <div className="flex items-center gap-2 mb-2">
                                    <div className="w-5 h-5 rounded-full bg-primary flex items-center justify-center text-[10px] font-bold text-white">
                                        管
                                    </div>
                                    <span className="text-xs font-bold text-primary">管理员回复</span>
                                </div>
                                <p className="text-sm text-muted-foreground bg-secondary/50 p-3 rounded-lg border border-border-subtle/5">
                                    {msg.reply}
                                </p>
                            </div>
                        )}
                    </motion.div>
                ))}
            </AnimatePresence>
        </div>
    );
}

function PublicWallList({ loading, messages }: { loading: boolean, messages: UserMessage[] }) {
    if (loading) {
        return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>;
    }

    if (messages.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 border border-dashed border-border-subtle/10 rounded-2xl bg-secondary/5 text-muted-foreground">
                <Globe className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">暂无上墙留言</p>
            </div>
        );
    }

    return (
        <div className="columns-1 md:columns-2 gap-4 space-y-4">
            <AnimatePresence mode="popLayout">
                {messages.map((msg, idx) => (
                    <motion.div
                        key={msg.id}
                        layout
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: idx * 0.05 }}
                        className="break-inside-avoid bg-card border border-border-subtle/10 rounded-2xl p-5 mb-4 hover:border-border-subtle/20 transition-colors shadow-sm"
                    >
                        <div className="flex items-center gap-3 mb-3">
                            <div className="w-8 h-8 rounded-full bg-secondary overflow-hidden relative border border-border-subtle/10">
                                {msg.memberAvatarUrl ? (
                                    <QuantumImage src={msg.memberAvatarUrl} alt="avatar" fill className="object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center bg-primary/10 text-primary text-xs font-bold">
                                        {msg.memberNickname ? msg.memberNickname[0] : 'U'}
                                    </div>
                                )}
                            </div>
                            <div>
                                <div className="text-sm font-bold text-foreground">{msg.memberNickname || '匿名用户'}</div>
                                <div className="text-[10px] text-muted-foreground">{new Date(msg.createdAt).toLocaleDateString()}</div>
                            </div>
                        </div>

                        <p className="text-sm text-foreground/90 leading-relaxed mb-4">
                            {msg.content}
                        </p>

                        {msg.reply && (
                            <div className="bg-primary/5 p-3 rounded-xl border border-primary/10">
                                <div className="flex items-center gap-2 mb-1">
                                    <div className="w-4 h-4 rounded-full bg-primary flex items-center justify-center text-[8px] font-bold text-white">
                                        管
                                    </div>
                                    <span className="text-xs font-bold text-primary">回复</span>
                                </div>
                                <p className="text-xs text-muted-foreground">
                                    {msg.reply}
                                </p>
                            </div>
                        )}
                    </motion.div>
                ))}
            </AnimatePresence>
        </div>
    );
}
</file>

<file path="src/app/watch/[id]/page.tsx">
"use client";

import { use, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { contentApi } from "@/lib/api/content";
import { VideoPlayer } from "@/components/features/VideoPlayer";
import { WatchSidebar } from "@/components/features/WatchSidebar";
import { GlowButton } from "@/components/ui/GlowButton";
import { AlertCircle, ArrowLeft, Film, RefreshCw } from "lucide-react";
import { motion } from "framer-motion";
import { MovieDetail } from "@/lib/types";
import { useToast } from "@/lib/context/ToastContext";

type Props = {
    params: Promise<{ id: string }>;
};

export default function WatchPage({ params }: Props) {
    const { id: unifiedVideoId } = use(params);
    const searchParams = useSearchParams();
    const router = useRouter();
    const { addToast } = useToast();

    const [isLoading, setIsLoading] = useState(true);
    const [movie, setMovie] = useState<MovieDetail | null>(null);
    const [error, setError] = useState<string | null>(null);

    const sourceId = searchParams.get("source");
    const episodeId = searchParams.get("episode");

    // 解析时间戳 (默认 0)
    const initialTimeStr = searchParams.get("t");
    const initialTime = initialTimeStr ? parseInt(initialTimeStr, 10) : 0;

    useEffect(() => {
        const fetchMovie = async () => {
            try {
                const data = await contentApi.getMovieDetail(unifiedVideoId);
                setMovie(data);

                if (!data || !data.sources || data.sources.length === 0) {
                    setError("该影片暂无可用播放资源");
                    return;
                }

                const targetSource = sourceId ? data.sources.find(s => s.id === sourceId) : null;

                if (!targetSource) {
                    const defaultSource = data.sources[0];
                    const defaultEp = defaultSource.episodes[0];

                    if (defaultSource && defaultEp) {
                        if (sourceId) {
                            addToast("原播放线路已失效，已为您切换至最佳线路", "info");
                        }

                        router.replace(`/watch/${unifiedVideoId}?source=${defaultSource.id}&episode=${defaultEp.id}`);
                    } else {
                        setError("资源解析异常：线路为空");
                    }
                    return;
                }

                const targetEp = episodeId ? targetSource.episodes.find(e => e.id === episodeId) : null;
                if (!targetEp) {
                    const firstEp = targetSource.episodes[0];
                    if (firstEp) {
                        addToast("所选集数已失效，正在播放第一集", "info");
                        router.replace(`/watch/${unifiedVideoId}?source=${targetSource.id}&episode=${firstEp.id}`);
                    } else {
                        setError("该线路暂无集数");
                    }
                    return;
                }

            } catch (err: unknown) {
                console.error(err);
                setError("无法加载影片信息，请检查网络");
            } finally {
                setIsLoading(false);
            }
        };

        fetchMovie();
    }, [unifiedVideoId, sourceId, episodeId, router, addToast]);

    if (isLoading) {
        return (
            <div className="min-h-screen pt-20 flex items-center justify-center bg-[#030305]">
                <div className="flex flex-col items-center gap-4">
                    <div className="relative">
                        <div className="w-12 h-12 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin" />
                        <div className="absolute inset-0 flex items-center justify-center">
                            <RefreshCw className="w-4 h-4 text-indigo-500 animate-pulse" />
                        </div>
                    </div>
                    <span className="text-neutral-500 text-sm animate-pulse font-mono">
                        ANALYZING STREAM...
                    </span>
                </div>
            </div>
        );
    }

    if (error || !movie || !sourceId || !episodeId) {
        return (
            <div className="min-h-screen pt-20 flex flex-col items-center justify-center text-center px-4 bg-[#030305]">
                <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center mb-6 border border-red-500/20">
                    <AlertCircle className="w-10 h-10 text-red-500" />
                </div>
                <h1 className="text-2xl font-bold text-white mb-2">信号中断</h1>
                <p className="text-neutral-400 mb-8 max-w-md">{error || "连接至媒体服务器失败"}</p>
                <GlowButton onClick={() => router.push(`/movie/${unifiedVideoId}`)} icon={<ArrowLeft className="w-4 h-4" />}>
                    返回详情页
                </GlowButton>
            </div>
        );
    }

    const currentSource = movie.sources.find(s => s.id === sourceId);
    const currentEpisode = currentSource?.episodes.find(e => e.id === episodeId);

    if (!currentSource || !currentEpisode) {
        return null;
    }

    return (
        <div className="min-h-screen bg-[#030305] pt-20 flex flex-col relative overflow-hidden">
            <div className="absolute inset-0 -z-10">
                <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-indigo-900/10 to-transparent pointer-events-none" />
            </div>

            <main className="flex-1 flex flex-col items-center px-6 pb-12 w-full max-w-[1600px] mx-auto">
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="w-full flex items-center justify-between mb-6 border-b border-white/5 pb-4"
                >
                    <div className="flex items-center gap-4">
                        <button
                            onClick={() => router.push(`/movie/${unifiedVideoId}`)}
                            className="p-2 -ml-2 text-neutral-400 hover:text-white hover:bg-white/5 rounded-full transition-all"
                        >
                            <ArrowLeft className="w-5 h-5" />
                        </button>
                        <div>
                            <h1 className="text-xl md:text-2xl font-bold text-white flex items-center gap-3">
                                {movie.title}
                                <span className="hidden md:inline-flex px-2 py-0.5 rounded text-[10px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                                    {currentSource.name}
                                </span>
                            </h1>
                            <p className="text-sm text-neutral-400 font-mono mt-0.5 flex items-center gap-2">
                                <Film className="w-3 h-3" />
                                正在播放: <span className="text-white">{currentEpisode.title}</span>
                            </p>
                        </div>
                    </div>
                </motion.div>

                <motion.div
                    layout
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="relative w-full aspect-video max-h-[80vh] bg-black rounded-xl overflow-hidden shadow-2xl ring-1 ring-white/10 z-10"
                >
                    <VideoPlayer
                        src={currentEpisode.url}
                        posterUrl={movie.posterUrl}
                        title={currentEpisode.title}
                        unifiedVideoId={unifiedVideoId}
                        videoId={currentSource.id}
                        episodeId={currentEpisode.id}
                        episodeName={currentEpisode.title}
                        initialTime={initialTime}
                    />
                </motion.div>
            </main>

            <WatchSidebar
                movie={movie}
                currentSourceId={sourceId}
                currentEpisodeId={episodeId}
            />
        </div>
    );
}
</file>

<file path="src/lib/utils.ts">
import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}

export function formatDuration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${h}小时 ${m}分`;
}

export function getErrorMessage(error: unknown): string {
    if (!error) return "发生未知错误";

    if (error instanceof Error) {
        if (error.message === "Failed to fetch" || error.message.includes("NetworkError")) {
            return "网络连接失败，请检查您的网络设置";
        }
        if (error.message === "Session expired") {
            return "登录已过期，请重新登录";
        }
        return error.message;
    }

    if (typeof error === 'string') return error;
    if (typeof error === 'object' && 'message' in error) {
        return String((error as { message: unknown }).message);
    }

    return "系统繁忙，请稍后重试";
}

export function buildForwardedHeaders(reqHeaders: Headers): Record<string, string> {
    const cfIp = reqHeaders.get("cf-connecting-ip");
    const realIp = reqHeaders.get("x-real-ip");
    const forwardedFor = reqHeaders.get("x-forwarded-for");

    const userAgent = reqHeaders.get("user-agent");
    const headers: Record<string, string> = {};

    let clientIp = "";

    if (cfIp) {
        clientIp = cfIp;
    } else if (realIp) {
        clientIp = realIp;
    } else if (forwardedFor) {
        clientIp = forwardedFor.split(',')[0].trim();
    }

    if (clientIp) {
        headers["X-Forwarded-For"] = clientIp;
        headers["X-Real-IP"] = clientIp;
        if (cfIp) headers["CF-Connecting-IP"] = cfIp;
    }

    if (userAgent) {
        headers["User-Agent"] = userAgent;
    }

    headers["X-Source-Component"] = "Huawai-SSR";

    return headers;
}
</file>

<file path="src/app/activate/page.tsx">
"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { GlowButton } from "@/components/ui/GlowButton";
import { CheckCircle2, Loader2, Mail, ShieldCheck, ArrowRight, ArrowLeft } from "lucide-react";
import { motion } from "framer-motion";
import { getErrorMessage } from "@/lib/utils";

export default function ActivatePage() {
    const { user, updateUser } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();

    const [email, setEmail] = useState("");
    const [code, setCode] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isResending, setIsResending] = useState(false);
    const [countdown, setCountdown] = useState(0);

    useEffect(() => {
        if (user) {
            if (user.status === "ACTIVE") {
                router.push("/");
                return;
            }
            setEmail(user.email);
        } else {
            const cachedEmail = sessionStorage.getItem("pending_activation_email");
            if (cachedEmail) {
                setEmail(cachedEmail);
            }
        }
    }, [user, router]);

    useEffect(() => {
        let timer: NodeJS.Timeout;
        if (countdown > 0) {
            timer = setInterval(() => {
                setCountdown((prev) => {
                    if (prev <= 1) return 0;
                    return prev - 1;
                });
            }, 1000);
        }
        return () => clearInterval(timer);
    }, [countdown]);

    const handleActivate = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!email) {
            addToast("请输入邮箱地址", "error");
            return;
        }

        setIsSubmitting(true);
        try {
            const updatedUserVO = await authApi.activate(email, code);

            if (updatedUserVO.token) {
                localStorage.setItem("huawai_token", updatedUserVO.token);
                updateUser(updatedUserVO);
            }

            sessionStorage.removeItem("pending_activation_email");

            addToast("账号激活成功！", "success");

            setTimeout(() => {
                router.push("/");
                router.refresh();
            }, 500);

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "激活失败，请检查验证码", "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleResend = async () => {
        if (!email || countdown > 0) {
            if (!email) addToast("请先填写邮箱地址", "error");
            return;
        }

        setIsResending(true);
        try {
            await authApi.resendCode(email);
            addToast("验证码已重新发送至 " + email, "success");
            setCountdown(60);
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "发送失败", "error");
        } finally {
            setIsResending(false);
        }
    };

    const showEmailInput = !user && !sessionStorage.getItem("pending_activation_email");

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-20%] left-[20%] w-[60%] h-[60%] bg-primary/5 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <Mail className="w-6 h-6 text-primary" />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">激活您的账号</h1>
                    <p className="text-muted-foreground text-sm leading-relaxed">
                        为了保障账号安全，我们需要验证您的邮箱。<br />
                        {email && (
                            <span className="mt-1 block">
                                已发送验证码至 <span className="text-foreground font-mono font-bold">{email}</span>
                            </span>
                        )}
                    </p>
                </div>

                <form onSubmit={handleActivate} className="space-y-6">
                    {showEmailInput && (
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">注册邮箱</label>
                            <input
                                type="email"
                                required
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-4 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background transition-all"
                                placeholder="name@example.com"
                            />
                        </div>
                    )}

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">验证码</label>
                        <input
                            type="text"
                            required
                            maxLength={6}
                            value={code}
                            onChange={(e) => setCode(e.target.value)}
                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-4 text-center text-3xl tracking-[0.5em] font-mono text-foreground placeholder:text-muted-foreground/30 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                            placeholder="000000"
                        />
                    </div>

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isSubmitting ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                        >
                            {isSubmitting ? "验证中..." : "确认激活"}
                        </GlowButton>
                    </div>
                </form>

                <div className="mt-8 text-center space-y-6">
                    <button
                        onClick={handleResend}
                        disabled={isResending || countdown > 0 || !email}
                        className="text-sm text-muted-foreground hover:text-foreground transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {countdown > 0
                            ? `${countdown} 秒后可重新发送`
                            : isResending
                                ? "发送中..."
                                : "没有收到？点击重新发送"
                        }
                    </button>

                    {!user && (
                        <div className="block pt-2 border-t border-border-subtle/10">
                            <a href="/login" className="inline-flex items-center gap-2 text-xs text-muted-foreground/60 hover:text-primary transition-colors py-2">
                                <ArrowLeft className="w-3 h-3" /> 返回登录界面
                            </a>
                        </div>
                    )}
                </div>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>画外安全中心</span>
                </div>
            </motion.div>
        </div>
    );
}
</file>

<file path="src/app/explore/ExploreClient.tsx">
"use client";

import { useState } from "react";
import { useAuth } from "@/lib/context/AuthContext";
import { MovieCard as MovieCardType, PortalMetadata } from "@/lib/types";
import dynamic from 'next/dynamic';

import { ExploreHeader } from "@/components/features/explore/ExploreHeader";
import { ExploreControls } from "@/components/features/explore/ExploreControls";
import { ExploreFilterPanel } from "@/components/features/explore/ExploreFilterPanel";
import { ExploreGrid } from "@/components/features/explore/ExploreGrid";

// Hook
import { useContentSearch, FilterState, DEFAULT_VALUE } from "@/hooks/useContentSearch";

const FilterModal = dynamic(
    () => import('@/components/features/search/FilterModal').then(mod => mod.FilterModal),
    { ssr: false }
);

interface ExploreClientProps {
    initialResults: MovieCardType[];
    initialTotal: number;
    initialMetadata: PortalMetadata | null;
    initialParams: FilterState;
}

export default function ExploreClient(props: ExploreClientProps) {
    const { user } = useAuth();
    const canSearch = user?.status === "ACTIVE";

    // 使用核心抽象 Hook
    const {
        results,
        totalResults,
        isLoading,
        filters,
        query,
        filterGroups,
        allCategories,
        setQuery,
        handleFilterChange,
        resetFilters,
        getDisplayOption
    } = useContentSearch({
        ...props,
        pageSize: 70 // Explore 页面单页加载更多
    });

    // UI State
    const [showFilters, setShowFilters] = useState(false);
    const [activeModalGroup, setActiveModalGroup] = useState<string | null>(null);

    const activeGroupConfig = activeModalGroup ? filterGroups.find(g => g.key === activeModalGroup) : null;
    const currentCategoryName = allCategories.find(c => c.slug === filters.category)?.name || "全站";

    const hasAdvancedFilters =
        filters.genre !== DEFAULT_VALUE ||
        filters.area !== DEFAULT_VALUE ||
        filters.year !== DEFAULT_VALUE ||
        filters.language !== DEFAULT_VALUE;

    // [Filter Logic]: Exclude 'category' from the expand panel, as it is handled by ExploreControls (Tabs).
    const panelFilterGroups = filterGroups.filter(g => g.key !== 'category');

    return (
        <div className="flex-none max-w-[1800px] mx-auto w-full z-20 space-y-6">
            <div className="mb-8 space-y-6">
                <ExploreHeader
                    canSearch={canSearch}
                    query={query}
                    setQuery={setQuery}
                    currentCategoryName={currentCategoryName}
                />

                <ExploreControls
                    showFilters={showFilters}
                    setShowFilters={setShowFilters}
                    hasAdvancedFilters={hasAdvancedFilters}
                    categories={allCategories}
                    currentCategorySlug={filters.category}
                    onCategoryChange={(slug) => handleFilterChange("category", slug)}
                    isLoading={isLoading}
                    totalResults={totalResults}
                    currentSort={filters.sort}
                    onSortChange={(val) => handleFilterChange("sort", val)}
                />

                <ExploreFilterPanel
                    isOpen={showFilters}
                    filterGroups={panelFilterGroups}
                    getCurrentValue={(key) => filters[key as keyof FilterState] || ""}
                    onFilterChange={handleFilterChange}
                    onShowMore={(key) => setActiveModalGroup(key)}
                    onClearAll={() => resetFilters()}
                />
            </div>

            <ExploreGrid
                isLoading={isLoading}
                results={results}
                query={query}
                onClearAll={() => resetFilters()}
            />

            {activeGroupConfig && (
                <FilterModal
                    isOpen={!!activeModalGroup}
                    onClose={() => setActiveModalGroup(null)}
                    title={activeGroupConfig.label}
                    options={activeGroupConfig.options}
                    currentValue={filters[activeGroupConfig.key as keyof FilterState] || ""}
                    onSelect={(val) => handleFilterChange(activeGroupConfig.key, val)}
                    formatLabel={(val) => getDisplayOption(activeGroupConfig.key, val)}
                />
            )}
        </div>
    );
}
</file>

<file path="src/app/page.tsx">
import { contentApi } from "@/lib/api/content";
import { FeaturedBento } from "@/components/features/FeaturedBento";
import { MovieRail } from "@/components/features/MovieRail";
import { CategoryNav } from "@/components/features/CategoryNav";
import { SITE_NAME, BRAND_SLOGAN } from "@/lib/seo";

export const revalidate = 3600;

export default async function Home() {
    const data = await contentApi.getHomeData();

    return (
        <div className="flex flex-col min-h-screen bg-background pb-20">
            <h1 className="sr-only">
                {SITE_NAME} - {BRAND_SLOGAN} - 4K HDR 在线流媒体
            </h1>

            <div className="relative z-10">
                <FeaturedBento
                    spotlights={data.spotlight}
                    trending={data.trending}
                />
            </div>

            <div className="flex flex-col gap-8 relative z-20 mt-6 md:mt-10">
                <div className="px-2 max-w-[1600px] mx-auto w-full">
                    <CategoryNav />
                </div>

                <div className="space-y-2 md:space-y-6 mt-4">
                    {data.sections.map((category, index) => (
                        <div key={category.id} className={index === 0 ? "pt-2" : ""}>
                            <MovieRail category={category} />
                        </div>
                    ))}
                </div>
            </div>

            <div className="h-20 bg-gradient-to-t from-background to-transparent pointer-events-none" />

            <div className="sr-only">
                在画外(Huawai)探索最新电影、高分美剧、热门日漫。我们提供无广告、不卡顿的极致观影体验。
                区别于传统视频网站，画外 Huawai 专注于视觉艺术与叙事深度的结合。
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/bento/SpotlightSection.tsx">
"use client";

import { motion, AnimatePresence } from "framer-motion";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useCarousel } from "@/hooks/useCarousel";
import { SpotlightCard } from "./SpotlightCard";

interface SpotlightSectionProps {
    movies: MovieCard[];
}

const CAROUSEL_INTERVAL = 8000;

export function SpotlightSection({ movies }: SpotlightSectionProps) {
    const {
        currentIndex,
        setIsPaused,
        nextSlide,
        prevSlide,
        goToSlide
    } = useCarousel({
        totalItems: movies.length,
        interval: CAROUSEL_INTERVAL
    });

    const currentMovie = movies[currentIndex];

    if (!currentMovie) return null;

    return (
        <div
            className={cn(
                "lg:col-span-9 relative rounded-3xl overflow-hidden group shadow-2xl h-[550px] lg:h-full select-none",
                "bg-card border border-border-subtle/10"
            )}
            onMouseEnter={() => setIsPaused(true)}
            onMouseLeave={() => setIsPaused(false)}
        >
            <div className="absolute inset-0 border border-transparent group-hover:border-border-subtle/20 rounded-3xl transition-colors duration-500 z-50 pointer-events-none" />

            <AnimatePresence initial={false} mode="sync">
                <motion.div
                    key={currentMovie.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 1.2, ease: "easeInOut" }}
                    className="absolute inset-0 w-full h-full bg-card"
                >
                    <SpotlightCard
                        movie={currentMovie}
                        index={currentIndex}
                        total={movies.length}
                        duration={CAROUSEL_INTERVAL}
                    />
                </motion.div>
            </AnimatePresence>

            <div className="absolute inset-y-0 left-0 w-28 flex items-center justify-start pl-6 opacity-0 group-hover:opacity-100 transition-opacity duration-500 z-40 pointer-events-none bg-gradient-to-r from-background/80 to-transparent">
                <button
                    onClick={(e) => { e.preventDefault(); prevSlide(); }}
                    className="pointer-events-auto p-3 rounded-full bg-secondary/80 backdrop-blur-xl border border-border-subtle/20 hover:bg-foreground hover:text-background text-foreground transition-all shadow-2xl hover:scale-110 active:scale-95"
                >
                    <ChevronLeft className="w-6 h-6" />
                </button>
            </div>

            <div className="absolute inset-y-0 right-0 w-28 flex items-center justify-end pr-6 opacity-0 group-hover:opacity-100 transition-opacity duration-500 z-40 pointer-events-none bg-gradient-to-l from-background/80 to-transparent">
                <button
                    onClick={(e) => { e.preventDefault(); nextSlide(); }}
                    className="pointer-events-auto p-3 rounded-full bg-secondary/80 backdrop-blur-xl border border-border-subtle/20 hover:bg-foreground hover:text-background text-foreground transition-all shadow-2xl hover:scale-110 active:scale-95"
                >
                    <ChevronRight className="w-6 h-6" />
                </button>
            </div>

            <div className="absolute bottom-6 left-6 md:left-10 z-50 flex gap-2">
                {movies.map((_, idx) => (
                    <button
                        key={idx}
                        onClick={(e) => { e.preventDefault(); goToSlide(idx); }}
                        className="group/dot p-2 -m-2 cursor-pointer"
                    >
                        <div className={cn(
                            "h-1 rounded-full transition-all duration-500 ease-out shadow-sm",
                            idx === currentIndex
                                ? "w-8 bg-foreground shadow-[0_0_8px_currentColor]"
                                : "w-2 bg-foreground/20 group-hover/dot:bg-foreground/40"
                        )} />
                    </button>
                ))}
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/player/IframePlayer.tsx">
"use client";

import { useState } from "react";
import { Loader2, ExternalLink } from "lucide-react";
import { useVideoHeartbeat } from "@/hooks/useVideoHeartbeat";

interface IframePlayerProps {
    src: string;
    title: string;
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    initialTime?: number;
}

export function IframePlayer({
                                 src,
                                 title,
                                 unifiedVideoId,
                                 videoId,
                                 episodeId,
                                 episodeName,
                                 initialTime = 0
                             }: IframePlayerProps) {
    const [loading, setLoading] = useState(true);

    let finalSrc = src;
    if (initialTime > 0) {
        finalSrc += finalSrc.includes('?') ? `&seek=${initialTime}` : `?seek=${initialTime}`;
    }
    finalSrc += finalSrc.includes('?') ? `&autoplay=1` : `?autoplay=1`;

    // Use Abstraction Hook
    useVideoHeartbeat({
        unifiedVideoId,
        videoId,
        episodeId,
        episodeName,
        getPlaybackState: () => ({
            currentTime: 0,
            duration: 1, // Dummy duration to pass check
            isPlaying: true // Assume playing if iframe is loaded
        })
    });

    return (
        <div className="relative w-full h-full bg-black rounded-xl overflow-hidden border border-white/5 shadow-2xl">
            {loading && (
                <div className="absolute inset-0 flex flex-col items-center justify-center bg-neutral-900 z-10">
                    <Loader2 className="w-8 h-8 animate-spin text-indigo-500 mb-2" />
                    <span className="text-xs text-neutral-500 animate-pulse">正在加载第三方资源...</span>
                </div>
            )}

            <iframe
                src={finalSrc}
                title={title}
                className="w-full h-full border-none"
                allowFullScreen
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                sandbox="allow-scripts allow-same-origin allow-forms allow-presentation"
                onLoad={() => setLoading(false)}
            />

            <div className="absolute bottom-0 left-0 right-0 bg-black/60 backdrop-blur-sm py-1 px-3 text-[10px] text-neutral-500 flex justify-between items-center opacity-0 hover:opacity-100 transition-opacity pointer-events-none">
                <span>外部播放源: {new URL(src).hostname}</span>
                <span className="flex items-center gap-1">
                    如无法播放，请尝试 <a href={src} target="_blank" rel="noopener noreferrer" className="text-indigo-400 hover:underline pointer-events-auto flex items-center gap-0.5">新窗口打开 <ExternalLink className="w-2 h-2"/></a>
                </span>
            </div>
        </div>
    );
}
</file>

<file path="src/components/features/ResourceSelector.tsx">
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { Play, Download, Cloud } from "lucide-react";
import { MovieDetail } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useToast } from "@/lib/context/ToastContext";
import { useAuth } from "@/lib/context/AuthContext";

import { OnlinePanel } from "./resource/OnlinePanel";
import { DownloadPanel } from "./resource/DownloadPanel";
import { CloudPanel } from "./resource/CloudPanel";

interface ResourceSelectorProps {
    movie: MovieDetail;
}

type TabType = "online" | "download" | "cloud";

export function ResourceSelector({ movie }: ResourceSelectorProps) {
    const router = useRouter();
    const { addToast } = useToast();
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState<TabType>("online");

    const handlePlay = (episodeId: string, sourceId: string) => {
        if (!user) {
            addToast("请先登录后观看", "error");
            router.push("/login");
            return;
        }
        if (user.status === "PENDING_VERIFICATION") {
            addToast("请先激活账号", "error");
            router.push("/activate");
            return;
        }
        router.push(`/watch/${movie.id}?source=${sourceId}&episode=${episodeId}`);
    };

    return (
        <div id="resources" className="w-full bg-card border border-border-subtle/10 rounded-3xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/5">
            <div className="flex items-center border-b border-border-subtle/10 bg-secondary/5 backdrop-blur-md sticky top-0 z-20">
                {[
                    { id: "online", label: "在线播放", icon: <Play className="w-4 h-4" /> },
                    { id: "download", label: "磁力下载", icon: <Download className="w-4 h-4" /> },
                    { id: "cloud", label: "网盘资源", icon: <Cloud className="w-4 h-4" /> },
                ].map((tab) => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id as TabType)}
                        className={cn(
                            "flex-1 flex items-center justify-center gap-2 py-6 text-sm font-bold tracking-wide transition-all relative outline-none group select-none",
                            activeTab === tab.id
                                ? "text-foreground bg-card"
                                : "text-muted-foreground hover:text-foreground hover:bg-secondary/10"
                        )}
                    >
                        <span className={cn("transition-colors duration-300", activeTab === tab.id ? "text-primary" : "group-hover:text-foreground")}>
                            {tab.icon}
                        </span>
                        {tab.label}
                        {activeTab === tab.id && (
                            <motion.div
                                layoutId="activeTabIndicator"
                                className="absolute bottom-0 left-0 right-0 h-[2px] bg-gradient-to-r from-primary via-purple-500 to-primary shadow-[0_0_15px_rgba(99,102,241,0.6)]"
                            />
                        )}
                    </button>
                ))}
            </div>

            <div className="min-h-[400px] relative">
                <AnimatePresence mode="wait">
                    {activeTab === "online" && (
                        <OnlinePanel key="online" movie={movie} onPlay={handlePlay} />
                    )}
                    {activeTab === "download" && (
                        <DownloadPanel key="download" movie={movie} />
                    )}
                    {activeTab === "cloud" && (
                        <CloudPanel key="cloud" movie={movie} />
                    )}
                </AnimatePresence>
            </div>
        </div>
    );
}
</file>

<file path="src/app/layout.tsx">
// src/app/layout.tsx

import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Navbar } from "@/components/layout/Navbar";
import { ToastProvider } from "@/lib/context/ToastContext";
import { AuthProvider } from "@/lib/context/AuthContext";
import { ThemeProvider } from "@/lib/context/ThemeContext";
import { Toaster } from "@/components/ui/Toaster";
import { RouteGuard } from "@/components/layout/RouteGuard";
import { CinematicGrain } from "@/components/ui/CinematicGrain";
import { CommandMenu } from "@/components/features/CommandMenu";
import React from "react";
import { generateWebsiteSchema, generateBrandSchema, SITE_URL, SITE_NAME, BRAND_SLOGAN } from "@/lib/seo";
import { cn } from "@/lib/utils";

const geistSans = Geist({
    variable: "--font-geist-sans",
    subsets: ["latin"],
});

const geistMono = Geist_Mono({
    variable: "--font-geist-mono",
    subsets: ["latin"],
});

export const metadata: Metadata = {
    metadataBase: new URL(SITE_URL),
    title: {
        default: `${SITE_NAME} - 4K HDR 沉浸式影视平台`,
        template: `%s | ${SITE_NAME}`
    },
    description: `${BRAND_SLOGAN}。画外 Huawai 是新一代高保真流媒体平台，拒绝算法噪音，专注提供 4K HDR、杜比音效的电影、剧集与动漫。在这里，找回纯粹的观影感动。`,
    keywords: [
        "画外", "Huawai", "画外 Huawai", "画外影视", "画外流媒体", "画外APP",
        "画外观影", "Huawai TV", "Huawai Movie",
        "4K电影", "HDR在线观看", "杜比视界", "无广告影视", "沉浸式影院",
        "Netflix平替", "高分美剧", "动漫生肉"
    ],
    icons: {
        icon: "/window.svg",
        shortcut: "/window.svg",
        apple: "/window.svg",
    },
    verification: {
        google: "BK8DhjzL0bOviLoKorgTUCJJQDZk0q6cBBGrS8Knbqc",
        other: {
            "baidu-site-verification": "code-placeholder"
        }
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
                url: "/og.png",
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
        images: ["/og.png"],
    },
    alternates: {
        canonical: "./",
    }
};

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    const websiteSchema = generateWebsiteSchema();
    const brandSchema = generateBrandSchema();

    return (
        <html lang="zh-CN" className="dark" suppressHydrationWarning>
        <body
            className={`${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground font-sans overflow-x-hidden`}
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

        <ThemeProvider>
            <ToastProvider>
                <AuthProvider>
                    <RouteGuard>
                        <div className={cn(
                            "min-h-screen flex flex-col max-w-[1600px] mx-auto",
                            "bg-background shadow-2xl my-0 lg:my-4 lg:rounded-[32px] overflow-hidden border border-border-subtle/10 relative"
                        )}>

                            <Navbar />

                            <main className="flex-1 flex flex-col relative z-0">
                                {children}
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
                    <CommandMenu />
                </AuthProvider>
            </ToastProvider>
        </ThemeProvider>
        </body>
        </html>
    );
}
</file>

<file path="src/app/login/page.tsx">
"use client";

import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { authApi } from "@/lib/api/auth";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, Lock, ArrowRight, ShieldCheck } from "lucide-react";
import { useToast } from "@/lib/context/ToastContext";
import { usePoW } from "@/hooks/usePoW";
import { useAuth } from "@/lib/context/AuthContext";
import { getErrorMessage, cn } from "@/lib/utils";

export default function LoginPage() {
    const router = useRouter();
    const { addToast } = useToast();
    const { updateUser } = useAuth();
    const { solve, isCalculating: isPoWCalculating } = usePoW();
    const [isApiLoading, setIsApiLoading] = useState(false);

    const [formData, setFormData] = useState({
        email: "",
        password: ""
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        try {
            setIsApiLoading(true);

            const verification = await solve();

            const data = await authApi.login(formData.email, formData.password, verification);

            if (data.token) {
                updateUser(data);
                if (data.status === "PENDING_VERIFICATION") {
                    sessionStorage.setItem("pending_activation_email", formData.email);
                    addToast("登录成功，请激活您的账号", "info");
                    await new Promise(resolve => setTimeout(resolve, 800));
                    router.push("/activate");
                } else {
                    addToast(`欢迎回来，${data.nickname || data.email}`, "success");
                    await new Promise(resolve => setTimeout(resolve, 800));
                    router.push("/");
                }
            }
        } catch (error: unknown) {
            const errorMsg = getErrorMessage(error);

            if (errorMsg.includes("激活") || errorMsg.toLowerCase().includes("verify") || errorMsg.toLowerCase().includes("pending")) {
                addToast("账号未激活，正在跳转至激活页面...", "info");

                sessionStorage.setItem("pending_activation_email", formData.email);

                setTimeout(() => {
                    router.push("/activate");
                }, 1500);
                return;
            }

            addToast(errorMsg || "登录失败", "error");
        } finally {
            setIsApiLoading(false);
        }
    };

    const isLoading = isApiLoading || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/10 blur-[120px] rounded-full animate-pulse" />
                <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 blur-[120px] rounded-full animate-pulse delay-1000" />
            </div>

            <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <div className="relative w-16 h-16 mx-auto mb-6 hover:scale-105 transition-transform duration-300">
                        <Image
                            src="/logo.svg"
                            alt="Huawai"
                            fill
                            className="drop-shadow-xl dark:invert transition-all duration-300"
                            priority
                        />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">欢迎回来</h1>
                    <p className="text-muted-foreground">请登录以同步您的观影进度</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-6">
                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">电子邮箱</label>
                        <div className="relative group">
                            <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="email"
                                required
                                value={formData.email}
                                onChange={(e) => setFormData({...formData, email: e.target.value})}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="name@example.com"
                            />
                        </div>
                    </div>

                    <div className="space-y-2">
                        <div className="flex items-center justify-between ml-1">
                            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">密码</label>
                            <Link href="/forgot-password" className="text-xs text-primary hover:text-primary/80 transition-colors">忘记密码?</Link>
                        </div>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.password}
                                onChange={(e) => setFormData({...formData, password: e.target.value})}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="••••••••"
                            />
                        </div>
                    </div>

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ArrowRight className="w-5 h-5" />}
                        >
                            {isPoWCalculating ? "安全环境计算中..." : isApiLoading ? "正在登录..." : "立即登录"}
                        </GlowButton>
                    </div>
                </form>

                <p className="mt-8 text-center text-sm text-muted-foreground">
                    还没有账号?{" "}
                    <Link href="/signup" className="text-foreground font-medium hover:underline underline-offset-4 decoration-primary">
                        免费注册
                    </Link>
                </p>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>本站采用 PoW 算力验证防御技术</span>
                </div>
            </motion.div>
        </div>
    );
}
</file>

<file path="src/lib/api/interaction.ts">
import { PageResponse, InteractionRecord } from "@/lib/types";

const API_BASE = "/api/portal/interaction";
const FEEDBACK_BASE = "/api/portal/feedback";

export interface UserMessage {
    id: string;
    content: string;
    reply: string;
    status: 'PENDING' | 'REPLIED' | 'CLOSED';
    createdAt: string;
    // For public wall
    memberNickname?: string;
    memberAvatarUrl?: string;
}

export const interactionApi = {
    async toggleFavorite(unifiedVideoId: string): Promise<boolean> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/favorites/${unifiedVideoId}`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to toggle favorite");
        const data = await res.json();
        return data.favorited;
    },

    async checkFavorite(unifiedVideoId: string): Promise<boolean> {
        const token = localStorage.getItem("huawai_token");
        if (!token) return false;

        const res = await fetch(`${API_BASE}/favorites/${unifiedVideoId}/status`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) return false;
        const data = await res.json();
        return data.favorited;
    },

    async getFavorites(page = 0, size = 20): Promise<PageResponse<InteractionRecord>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/favorites?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch favorites");
        return res.json();
    },

    async reportProgress(
        unifiedVideoId: string,
        videoId: string,
        episodeId: string | undefined,
        episodeName: string,
        progress: number,
        duration: number,
        useKeepAlive = false
    ): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        if (!token) return;

        await fetch(`${API_BASE}/history`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({
                unifiedVideoId,
                videoId,
                episodeId,
                episodeName,
                progress,
                duration
            }),
            keepalive: useKeepAlive
        });
    },

    async getHistory(page = 0, size = 20): Promise<PageResponse<InteractionRecord>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/history?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch history");
        return res.json();
    },

    async clearHistory(): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        await fetch(`${API_BASE}/history`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
    },

    async deleteRecord(historyId: string): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        await fetch(`${API_BASE}/history/${historyId}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
    },

    // --- Feedback / Messages ---

    async submitMessage(content: string): Promise<UserMessage> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({ content })
        });
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || "Failed to submit message");
        }
        return res.json();
    },

    async getMyMessages(page = 0, size = 10): Promise<PageResponse<UserMessage>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch messages");
        return res.json();
    },

    async getPublicWall(page = 0, size = 20): Promise<PageResponse<UserMessage>> {
        const res = await fetch(`${FEEDBACK_BASE}/wall?page=${page}&size=${size}`, {
            method: "GET"
        });
        if (!res.ok) throw new Error("Failed to fetch message wall");
        return res.json();
    },

    async deleteMessage(messageId: string): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}/${messageId}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to delete message");
    }
};
</file>

<file path="src/components/features/player/VidstackPlayer.tsx">
"use client";

import '@vidstack/react/player/styles/default/theme.css';
import '@vidstack/react/player/styles/default/layouts/video.css';

import { useEffect, useRef } from 'react';
import {
    MediaPlayer,
    MediaProvider,
    MediaPlayerInstance,
    isHLSProvider,
    type MediaProviderAdapter
} from '@vidstack/react';
import { DefaultVideoLayout, defaultLayoutIcons } from '@vidstack/react/player/layouts/default';
import Hls from 'hls.js';
import { useToast } from '@/lib/context/ToastContext';
import { formatDuration } from '@/lib/utils';
import { useVideoHeartbeat } from '@/hooks/useVideoHeartbeat';

const CHINESE_TRANSLATIONS = {
    'Play': '播放',
    'Pause': '暂停',
    'Replay': '重播',
    'Mute': '静音',
    'Unmute': '取消静音',
    'Volume': '音量',
    'Enter Fullscreen': '进入全屏',
    'Exit Fullscreen': '退出全屏',
    'Settings': '设置',
    'Captions': '字幕',
    'Off': '关闭',
    'Quality': '画质',
    'Speed': '速度',
    'Normal': '正常',
    'Auto': '自动',
    'Loop': '循环播放',
    'AirPlay': '隔空播放',
    'Google Cast': '投屏',
    'Seek Backward': '快退',
    'Seek Forward': '快进',
    'Mini Player': '画中画',
    'Live': '直播',
    'Audio': '音轨',
    'Chapters': '章节',
};

interface VidstackPlayerProps {
    src: string;
    title: string;
    poster?: string;
    unifiedVideoId?: string;
    videoId?: string;
    episodeId?: string;
    episodeName?: string;
    initialTime?: number;
    onEnded?: () => void;
}

export function VidstackPlayer({
                                   src,
                                   title,
                                   poster,
                                   unifiedVideoId,
                                   videoId,
                                   episodeId,
                                   episodeName,
                                   initialTime = 0,
                                   onEnded
                               }: VidstackPlayerProps) {
    const playerRef = useRef<MediaPlayerInstance>(null);
    const { addToast } = useToast();
    const restoredSrcRef = useRef<string | null>(null);

    // Use Abstraction Hook
    const { report } = useVideoHeartbeat({
        unifiedVideoId,
        videoId,
        episodeId,
        episodeName,
        getPlaybackState: () => {
            const player = playerRef.current;
            if (!player) return { currentTime: 0, duration: 0, isPlaying: false };
            return {
                currentTime: player.currentTime,
                duration: player.duration,
                isPlaying: !player.paused
            };
        }
    });

    const handleCanPlay = () => {
        if (restoredSrcRef.current !== src && initialTime > 0 && playerRef.current) {
            const player = playerRef.current;
            if (initialTime < player.duration) {
                player.currentTime = initialTime;
                if (initialTime > 60 && initialTime < player.duration * 0.95) {
                    addToast(`已为您恢复至 ${formatDuration(Math.floor(initialTime / 60))} 处`, "info");
                }
            }
            restoredSrcRef.current = src;
        }
    };

    const onProviderChange = (provider: MediaProviderAdapter | null) => {
        if (isHLSProvider(provider)) {
            provider.library = Hls;
            if (!provider.config) provider.config = {};
            provider.config = {
                ...provider.config,
                xhrSetup: (xhr, url) => {
                    xhr.withCredentials = false;
                },
                lowLatencyMode: false,
                maxBufferHole: 2.0,
                maxMaxBufferLength: 60,
                manifestLoadingMaxRetry: 4,
                manifestLoadingTimeOut: 20000,
            };
        }
    };

    return (
        <MediaPlayer
            ref={playerRef}
            title={title}
            src={src}
            poster={poster}
            aspectRatio="16/9"
            autoPlay={true}
            onEnded={onEnded}
            onCanPlay={handleCanPlay}
            onProviderChange={onProviderChange}
            crossOrigin="anonymous"
            playsInline
            // Manual report on Pause
            onPause={() => report(false)}
            load="eager"
            streamType="on-demand"
            className="w-full h-full bg-black rounded-xl overflow-hidden shadow-2xl ring-1 ring-white/10"
        >
            <MediaProvider />
            <DefaultVideoLayout
                icons={defaultLayoutIcons}
                translations={CHINESE_TRANSLATIONS}
            />
        </MediaPlayer>
    );
}
</file>

<file path="src/app/history/page.tsx">
"use client";

import { useEffect, useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { CalendarClock, Trash2, Loader2, PlayCircle } from "lucide-react";
import { interactionApi } from "@/lib/api/interaction";
import { contentApi } from "@/lib/api/content";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { InteractionRecord, MovieDetail, VideoSource, Episode } from "@/lib/types";
import { HistoryItem } from "@/components/features/history/HistoryItem";
import { getErrorMessage } from "@/lib/utils";

export default function HistoryPage() {
    const { user } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();
    const [history, setHistory] = useState<InteractionRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [playingId, setPlayingId] = useState<string | null>(null);

    useEffect(() => {
        if (user) {
            interactionApi.getHistory()
                .then(res => setHistory(res.content))
                .catch(console.error)
                .finally(() => setIsLoading(false));
        } else {
            setIsLoading(false);
        }
    }, [user]);

    const groupedHistory = useMemo(() => {
        const groups: Record<string, InteractionRecord[]> = {};
        const now = new Date();
        const today = now.toISOString().split('T')[0];
        const yesterday = new Date(now.setDate(now.getDate() - 1)).toISOString().split('T')[0];

        history.forEach(item => {
            if (!item.lastWatchedAt) return;
            const date = item.lastWatchedAt.split('T')[0];
            let label = date;
            if (date === today) label = "今天";
            else if (date === yesterday) label = "昨天";
            else label = "更早之前";

            if (!groups[label]) groups[label] = [];
            groups[label].push(item);
        });
        return groups;
    }, [history]);

    const handleClearAll = async () => {
        if (window.confirm("确定清空所有历史记录？此操作不可恢复。")) {
            await interactionApi.clearHistory();
            setHistory([]);
            addToast("历史记录已清空", "success");
        }
    };

    const handleDeleteSingle = async (id: string) => {
        setHistory(prev => prev.filter(item => item.id !== id));
        try {
            await interactionApi.deleteRecord(id);
        } catch (e) {
            console.error("Failed to delete record", e);
        }
    };

    const handleSmartPlay = async (record: InteractionRecord) => {
        if (playingId) return;
        setPlayingId(record.id);

        try {
            const movie: MovieDetail | null = await contentApi.getMovieDetail(record.unifiedVideoId);

            if (!movie) throw new Error("影片信息未找到");
            if (!movie.sources || movie.sources.length === 0) {
                throw new Error("该影片暂无可用播放源");
            }

            let foundSource: VideoSource | undefined;
            let foundEpisode: Episode | undefined;
            let matchType = "";

            if (record.lastVideoId) {
                foundSource = movie.sources.find(s => s.id === record.lastVideoId);
                if (foundSource && record.lastEpisodeId) {
                    foundEpisode = foundSource.episodes.find(e => e.id === record.lastEpisodeId);
                    if (foundEpisode) matchType = "EXACT_ID";
                }
            }

            if (!foundEpisode && record.episodeName) {
                if (foundSource) {
                    foundEpisode = findEpisodeByName(foundSource, record.episodeName);
                    if (foundEpisode) matchType = "SAME_SOURCE_SEMANTIC";
                }

                if (!foundEpisode) {
                    for (const source of movie.sources) {
                        const ep = findEpisodeByName(source, record.episodeName);
                        if (ep) {
                            foundSource = source;
                            foundEpisode = ep;
                            matchType = "CROSS_SOURCE_SEMANTIC";
                            break;
                        }
                    }
                }
            }

            if (foundSource && foundEpisode) {
                let targetEpisodeId = foundEpisode.id;
                let startTime = record.progressSeconds;
                let autoNext = false;

                const progress = record.durationSeconds > 0
                    ? (record.progressSeconds / record.durationSeconds)
                    : 0;

                if (progress > 0.90) {
                    const currentIndex = foundSource.episodes.findIndex(e => e.id === foundEpisode!.id);
                    if (currentIndex !== -1 && currentIndex < foundSource.episodes.length - 1) {
                        const nextEp = foundSource.episodes[currentIndex + 1];
                        targetEpisodeId = nextEp.id;
                        startTime = 0;
                        autoNext = true;
                    }
                }

                if (autoNext) {
                    addToast("上集已看完，为您自动播放下一集", "success");
                } else if (matchType !== "EXACT_ID") {
                    addToast(matchType === "SAME_SOURCE_SEMANTIC"
                        ? "资源已更新，自动定位到对应集数"
                        : "原线路已失效，自动切换至备用线路", "info");
                }

                const timeParam = startTime > 0 ? `&t=${startTime}` : "";
                router.push(`/watch/${movie.id}?source=${foundSource.id}&episode=${targetEpisodeId}${timeParam}`);

            } else {
                addToast("无法自动定位历史进度，请手动选择集数", "info");
                router.push(`/movie/${movie.id}`);
            }

        } catch (e: unknown) {
            addToast(getErrorMessage(e) || "无法跳转播放", "error");
            router.push(`/movie/${record.unifiedVideoId}`);
        } finally {
            setTimeout(() => setPlayingId(null), 2000);
        }
    };

    const findEpisodeByName = (source: VideoSource, targetName: string): Episode | undefined => {
        let match = source.episodes.find(e => e.title === targetName);
        if (match) return match;

        const normalize = (s: string) => s.replace(/[第集\s\[\]【】()]/g, "").toLowerCase();
        const cleanTarget = normalize(targetName);

        match = source.episodes.find(e => normalize(e.title) === cleanTarget);
        if (match) return match;

        if (cleanTarget.length > 1) {
            match = source.episodes.find(e => normalize(e.title).includes(cleanTarget));
        }
        return match;
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-background pt-32 flex justify-center items-center">
                <div className="flex flex-col items-center gap-4">
                    <Loader2 className="w-8 h-8 animate-spin text-primary" />
                    <p className="text-xs text-muted-foreground font-mono tracking-widest">LOADING HISTORY...</p>
                </div>
            </div>
        );
    }

    const totalRecords = history.length;

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-4xl mx-auto relative z-10">
                <div className="flex flex-col md:flex-row md:items-end justify-between mb-12 gap-6">
                    <div>
                        <motion.h1
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="text-4xl font-bold text-foreground mb-2 flex items-center gap-3"
                        >
                            <CalendarClock className="w-8 h-8 text-primary" />
                            观看历史
                        </motion.h1>
                        <motion.p
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            transition={{ delay: 0.1 }}
                            className="text-muted-foreground text-sm"
                        >
                            共记录 <span className="text-foreground font-bold">{totalRecords}</span> 条观看记录
                        </motion.p>
                    </div>

                    {totalRecords > 0 && (
                        <motion.button
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            onClick={handleClearAll}
                            className="flex items-center gap-2 px-4 py-2 rounded-full bg-secondary/50 hover:bg-destructive/10 text-muted-foreground hover:text-destructive border border-border-subtle/10 hover:border-destructive/20 transition-all text-xs font-bold"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                            清空历史
                        </motion.button>
                    )}
                </div>

                <div className="space-y-12">
                    <AnimatePresence mode="popLayout">
                        {Object.entries(groupedHistory).map(([dateLabel, items], groupIndex) => (
                            items.length > 0 && (
                                <motion.section
                                    key={dateLabel}
                                    layout
                                    initial={{ opacity: 0, y: 20 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0 }}
                                    transition={{ delay: groupIndex * 0.1 }}
                                    className="relative pl-4"
                                >
                                    <div className="sticky top-24 z-20 flex items-center mb-6 -ml-4">
                                        <div className="w-2 h-2 rounded-full bg-primary mr-4 shadow-[0_0_10px_rgba(99,102,241,0.5)]" />
                                        {/* 时间标签：背景需适配双色 */}
                                        <h3 className="text-sm font-bold text-foreground uppercase tracking-widest bg-background/80 backdrop-blur-md px-3 py-1 rounded-full border border-border-subtle/10 shadow-sm">
                                            {dateLabel}
                                        </h3>
                                        <div className="h-px bg-border-subtle/10 flex-1 ml-4" />
                                    </div>

                                    <div className="space-y-6">
                                        {items.map((rec, idx) => (
                                            <HistoryItem
                                                key={rec.id}
                                                rec={rec}
                                                isPlaying={playingId === rec.id}
                                                onPlay={handleSmartPlay}
                                                onDelete={handleDeleteSingle}
                                                index={idx}
                                            />
                                        ))}
                                    </div>
                                </motion.section>
                            )
                        ))}
                    </AnimatePresence>

                    {totalRecords === 0 && (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.9 }}
                            animate={{ opacity: 1, scale: 1 }}
                            className="flex flex-col items-center justify-center py-24 border border-dashed border-border-subtle/10 rounded-3xl bg-secondary/5"
                        >
                            <div className="w-24 h-24 rounded-full bg-secondary border border-border-subtle/10 flex items-center justify-center mb-6 relative group cursor-default">
                                <div className="absolute inset-0 bg-primary/20 blur-[30px] rounded-full opacity-0 group-hover:opacity-100 transition-opacity" />
                                <PlayCircle className="w-10 h-10 text-muted-foreground group-hover:text-primary transition-colors" />
                            </div>
                            <h3 className="text-xl font-bold text-foreground mb-2">暂无观看记录</h3>
                            <p className="text-muted-foreground text-sm max-w-xs text-center mb-8">
                                还没有留下任何观影痕迹。开始探索，点亮您的第一段记忆。
                            </p>
                            <button
                                onClick={() => router.push("/explore")}
                                className="px-8 py-3 rounded-full bg-foreground text-background font-bold hover:bg-foreground/80 transition-colors shadow-lg active:scale-95"
                            >
                                前往探索
                            </button>
                        </motion.div>
                    )}
                </div>
            </div>
        </div>
    );
}
</file>

<file path="src/app/profile/page.tsx">
"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { Lock, Clock, Flame, Coins, Heart, Ghost, EyeOff, Shield } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { interactionApi } from "@/lib/api/interaction";
import { cn, getErrorMessage } from "@/lib/utils";
import dynamic from 'next/dynamic';

import { IdentityCard } from "@/components/features/profile/IdentityCard";
import { StatCard } from "@/components/features/profile/StatCard";

const PasswordModal = dynamic(
    () => import('@/components/features/profile/PasswordModal').then(mod => mod.PasswordModal),
    { ssr: false }
);

export default function ProfilePage() {
    const { user, logout, isLoading, updateUser, isStealthMode, toggleStealthMode } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();

    const [isEditing, setIsEditing] = useState(false);
    const [editNickname, setEditNickname] = useState("");
    const [isUploading, setIsUploading] = useState(false);
    const [showPwModal, setShowPwModal] = useState(false);
    const [isCheckingIn, setIsCheckingIn] = useState(false);
    const [favCount, setFavCount] = useState(0);

    useEffect(() => {
        if (!isLoading && !user) {
            router.push("/login");
        }
        if (user) {
            setEditNickname(user.nickname);
            interactionApi.getFavorites(0, 1)
                .then(res => setFavCount(res.totalElements ?? 0))
                .catch(() => {});
        }
    }, [user, isLoading, router]);

    if (!user) return null;

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file || !user.token) return;
        if (file.size > 2 * 1024 * 1024) {
            addToast("图片大小不能超过 2MB", "error");
            return;
        }
        setIsUploading(true);
        try {
            const { url } = await authApi.uploadAvatar(user.token, file);
            updateUser({ ...user, avatarUrl: url });
            addToast("头像更新成功", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err) || "上传失败", "error");
        } finally {
            setIsUploading(false);
        }
    };

    const handleSaveProfile = async () => {
        if (!user.token) return;
        try {
            const updatedUser = await authApi.updateProfile(user.token, editNickname);
            updateUser({ ...user, ...updatedUser });
            setIsEditing(false);
            addToast("资料已更新", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        }
    };

    const handleChangePassword = async (old: string, newP: string, confirm: string) => {
        if (!user.token) return;
        if (newP !== confirm) {
            addToast("两次新密码输入不一致", "error");
            return;
        }
        try {
            await authApi.changePassword(user.token, old, newP);
            setShowPwModal(false);
            addToast("密码修改成功", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        }
    };

    const handleCheckIn = async () => {
        if (!user.token || isCheckingIn) return;
        setIsCheckingIn(true);
        try {
            const result = await authApi.checkIn(user.token);
            updateUser({
                ...user,
                points: result.currentPoints,
                experience: result.experience,
                level: result.level,
                checkInStreak: result.checkInStreak,
                lastCheckInDate: new Date().toISOString().split('T')[0]
            });
            addToast(`签到成功！积分 +${result.earnedPoints}`, "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        } finally {
            setIsCheckingIn(false);
        }
    };

    const isCheckedInToday = user.lastCheckInDate === new Date().toISOString().split('T')[0];

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-5xl mx-auto relative z-10">
                <IdentityCard
                    user={user}
                    isEditing={isEditing}
                    editNickname={editNickname}
                    isUploading={isUploading}
                    isCheckingIn={isCheckingIn}
                    isCheckedInToday={isCheckedInToday}
                    onSetEditNickname={setEditNickname}
                    onSaveProfile={handleSaveProfile}
                    onStartEdit={() => setIsEditing(true)}
                    onCancelEdit={() => setIsEditing(false)}
                    onUploadAvatar={handleFileChange}
                    onCheckIn={handleCheckIn}
                    onLogout={logout}
                />

                {/* 2. Stats Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-12">
                    <StatCard
                        label="我的积分"
                        value={user.points}
                        icon={<Coins className="w-5 h-5" />}
                        color="from-yellow-500/20 to-orange-500/20"
                        borderColor="group-hover:border-yellow-500/30"
                    />
                    <StatCard
                        label="连续签到"
                        value={`${user.checkInStreak} 天`}
                        icon={<Flame className="w-5 h-5" />}
                        color="from-red-500/20 to-rose-500/20"
                        borderColor="group-hover:border-red-500/30"
                    />
                    <StatCard
                        label="我的收藏"
                        value={favCount}
                        icon={<Heart className="w-5 h-5" />}
                        color="from-pink-500/20 to-rose-500/20"
                        borderColor="group-hover:border-pink-500/30"
                    />
                    <StatCard
                        label="累计观影"
                        value="0h"
                        icon={<Clock className="w-5 h-5" />}
                        color="from-blue-500/20 to-cyan-500/20"
                        borderColor="group-hover:border-blue-500/30"
                    />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                    <section className="bg-card/40 border border-border-subtle/10 rounded-3xl p-8 backdrop-blur-sm h-full shadow-lg">
                        <div className="flex items-center gap-3 mb-8">
                            <div className="p-2 bg-secondary rounded-lg text-foreground border border-border-subtle/10">
                                <Shield className="w-5 h-5" />
                            </div>
                            <h2 className="text-xl font-bold text-foreground">账户安全</h2>
                        </div>

                        <div className="flex items-center justify-between p-6 bg-secondary/20 border border-border-subtle/10 rounded-2xl hover:bg-secondary/40 transition-all group">
                            <div className="flex items-center gap-4">
                                <div className="p-3 bg-muted rounded-full text-muted-foreground group-hover:text-foreground transition-colors">
                                    <Lock className="w-5 h-5" />
                                </div>
                                <div>
                                    <h4 className="text-foreground font-medium">登录密码</h4>
                                    <p className="text-xs text-muted-foreground mt-0.5">定期修改密码可以保护账号安全</p>
                                </div>
                            </div>
                            <button
                                onClick={() => setShowPwModal(true)}
                                className="px-6 py-2.5 text-sm font-medium text-muted-foreground hover:text-foreground border border-border-subtle/10 hover:border-border-subtle/30 rounded-xl bg-card hover:bg-secondary/80 transition-all shadow-sm"
                            >
                                修改密码
                            </button>
                        </div>
                    </section>

                    <section className="bg-card/40 border border-border-subtle/10 rounded-3xl p-8 backdrop-blur-sm h-full shadow-lg">
                        <div className="flex items-center gap-3 mb-8">
                            <div className="p-2 bg-secondary rounded-lg text-foreground border border-border-subtle/10">
                                <Ghost className="w-5 h-5" />
                            </div>
                            <h2 className="text-xl font-bold text-foreground">隐私设置</h2>
                        </div>

                        <div className={cn(
                            "flex items-center justify-between p-6 border rounded-2xl transition-all group",
                            isStealthMode
                                ? "bg-primary/10 border-primary/30"
                                : "bg-secondary/20 border-border-subtle/10 hover:bg-secondary/40"
                        )}>
                            <div className="flex items-center gap-4">
                                <div className={cn(
                                    "p-3 rounded-full transition-colors",
                                    isStealthMode ? "bg-primary/20 text-primary" : "bg-muted text-muted-foreground"
                                )}>
                                    <EyeOff className="w-5 h-5" />
                                </div>
                                <div>
                                    <h4 className={cn("font-medium", isStealthMode ? "text-primary" : "text-foreground")}>
                                        隐身模式
                                    </h4>
                                    <p className="text-xs text-muted-foreground mt-0.5">
                                        开启后将暂停记录您的观影历史进度
                                    </p>
                                </div>
                            </div>
                            <button
                                onClick={toggleStealthMode}
                                className={cn(
                                    "relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background",
                                    isStealthMode ? "bg-primary" : "bg-muted-foreground/30"
                                )}
                            >
                                <span
                                    className={cn(
                                        "inline-block h-4 w-4 transform rounded-full bg-white transition-transform",
                                        isStealthMode ? "translate-x-6" : "translate-x-1"
                                    )}
                                />
                            </button>
                        </div>
                    </section>
                </div>
            </div>

            <PasswordModal
                isOpen={showPwModal}
                onClose={() => setShowPwModal(false)}
                onSubmit={handleChangePassword}
            />
        </div>
    );
}
</file>

<file path="src/components/features/bento/SpotlightCard.tsx">
"use client";

import Link from "next/link";
import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { Play, Star, Calendar, Tag, Plus, Check, Loader2 } from "lucide-react";
import { MovieCard } from "@/lib/types";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { GlowButton } from "@/components/ui/GlowButton";
import { useRef } from "react";
import React from "react";
import { cn } from "@/lib/utils";
import { useMediaInteraction } from "@/hooks/useMediaInteraction";

interface SpotlightCardProps {
    movie: MovieCard;
    index: number;
    total: number;
    duration?: number;
}

export function SpotlightCard({ movie, index, total, duration = 8000 }: SpotlightCardProps) {
    const containerRef = useRef<HTMLDivElement>(null);

    // Abstracted Logic
    const { isFavorite, isLoading: isLoadingFav, toggleFavorite } = useMediaInteraction(movie.id);

    const x = useMotionValue(0);
    const y = useMotionValue(0);

    const mouseX = useSpring(x, { stiffness: 150, damping: 20 });
    const mouseY = useSpring(y, { stiffness: 150, damping: 20 });

    const bgX = useTransform(mouseX, [-0.5, 0.5], ["1%", "-1%"]);
    const bgY = useTransform(mouseY, [-0.5, 0.5], ["1%", "-1%"]);
    const posterRotateX = useTransform(mouseY, [-0.5, 0.5], [4, -4]);
    const posterRotateY = useTransform(mouseX, [-0.5, 0.5], [-4, 4]);

    const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
        const rect = containerRef.current?.getBoundingClientRect();
        if (rect) {
            const width = rect.width;
            const height = rect.height;
            const mouseXPos = e.clientX - rect.left;
            const mouseYPos = e.clientY - rect.top;
            x.set((mouseXPos / width) - 0.5);
            y.set((mouseYPos / height) - 0.5);
        }
    };

    const handleMouseLeave = () => {
        x.set(0);
        y.set(0);
    };

    return (
        <Link href={`/movie/${movie.id}`} className="block w-full h-full">
            <motion.div
                ref={containerRef}
                onMouseMove={handleMouseMove}
                onMouseLeave={handleMouseLeave}
                className="relative w-full h-full overflow-hidden bg-background group"
            >
                <motion.div
                    style={{ x: bgX, y: bgY, scale: 1.05 }}
                    className="absolute inset-0 -z-30 pointer-events-none"
                >
                    <QuantumImage
                        src={movie.posterUrl}
                        alt=""
                        fill
                        className="object-cover blur-[60px] brightness-[0.4] dark:brightness-[0.3] saturate-[1.2]"
                        loading="eager"
                    />
                </motion.div>

                <div className="absolute inset-0 bg-gradient-to-t from-background via-background/80 to-transparent z-0" />
                <div className="absolute inset-0 bg-gradient-to-r from-background via-background/90 to-transparent z-0" />

                <div className="absolute inset-0 z-10 grid grid-cols-1 md:grid-cols-12 gap-6 p-6 md:p-10 lg:p-12 items-end">

                    <div className="md:col-span-7 lg:col-span-7 flex flex-col justify-end h-full pb-4 order-2 md:order-1 relative z-20 pointer-events-none">
                        <motion.div
                            initial="hidden"
                            animate="visible"
                            variants={{
                                visible: { transition: { staggerChildren: 0.08 } }
                            }}
                        >
                            <motion.div
                                variants={{ hidden: { opacity: 0, y: 10 }, visible: { opacity: 1, y: 0 } }}
                                className="flex items-center gap-4 mb-4"
                            >
                                <div className="flex items-center gap-2 bg-secondary/20 backdrop-blur-md border border-border-subtle/10 px-3 py-1 rounded-full shadow-sm">
                                    <span className="relative flex h-2 w-2">
                                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                                      <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                                    </span>
                                    <span className="text-[10px] font-bold text-foreground uppercase tracking-widest">Featured</span>
                                </div>
                            </motion.div>

                            <div className="h-[88px] md:h-[128px] lg:h-[140px] flex flex-col justify-end mb-4">
                                <motion.h2
                                    variants={{ hidden: { opacity: 0, y: 15 }, visible: { opacity: 1, y: 0 } }}
                                    className="text-4xl md:text-6xl lg:text-7xl font-black text-foreground leading-[1.1] tracking-tighter drop-shadow-lg line-clamp-2"
                                >
                                    {movie.title}
                                </motion.h2>
                                {movie.subtitle && (
                                    <motion.p
                                        variants={{ hidden: { opacity: 0 }, visible: { opacity: 1 } }}
                                        className="text-lg md:text-2xl text-foreground/80 font-bold tracking-wide mt-2 line-clamp-1"
                                    >
                                        {movie.subtitle}
                                    </motion.p>
                                )}
                            </div>

                            <motion.div
                                variants={{ hidden: { opacity: 0 }, visible: { opacity: 1 } }}
                                className="flex flex-wrap items-center gap-3 mb-6"
                            >
                                {movie.rating > 0 && (
                                    <Badge icon={<Star className="w-3 h-3 fill-amber-400 text-amber-400" />} text={String(movie.rating)} className="bg-amber-500/10 border-amber-500/20 text-amber-500 dark:text-amber-200" />
                                )}
                                {movie.season && movie.season > 0 && (
                                    <Badge icon={<span className="text-[10px] font-black">S{movie.season}</span>} text={`第${movie.season}季`} className="bg-purple-500/10 border-purple-500/20 text-purple-500" />
                                )}
                                <Badge icon={<Calendar className="w-3 h-3" />} text={String(movie.releaseYear)} />
                                <Badge icon={<Tag className="w-3 h-3" />} text={movie.categoryName} />
                                {movie.genres?.slice(0, 2).map(g => (
                                    <Badge key={g} text={g} className="text-muted-foreground bg-secondary/50" />
                                ))}
                            </motion.div>

                            <div className="h-[64px] md:h-[80px] mb-8 overflow-hidden hidden md:block">
                                <motion.p
                                    variants={{ hidden: { opacity: 0 }, visible: { opacity: 0.95 } }}
                                    className="text-sm md:text-lg text-muted-foreground leading-relaxed font-normal line-clamp-2 md:line-clamp-3 text-ellipsis drop-shadow-sm max-w-3xl"
                                >
                                    {movie.description}
                                </motion.p>
                            </div>

                            <motion.div
                                variants={{ hidden: { opacity: 0, y: 10 }, visible: { opacity: 1, y: 0 } }}
                                className="flex items-center gap-4 pointer-events-auto"
                            >
                                <GlowButton variant="primary" icon={<Play className="w-5 h-5 fill-current" />} className="px-8 py-4 text-base font-bold rounded-full hover:scale-105 shadow-xl">
                                    立即观看
                                </GlowButton>

                                <button
                                    onClick={toggleFavorite}
                                    disabled={isLoadingFav}
                                    className={cn(
                                        "p-4 rounded-full border transition-all backdrop-blur-md group/btn relative overflow-hidden",
                                        isFavorite
                                            ? "bg-green-500 text-white border-green-500 hover:bg-green-600"
                                            : "bg-secondary/50 text-foreground border-border-subtle/20 hover:bg-foreground hover:text-background"
                                    )}
                                    title={isFavorite ? "取消收藏" : "加入待看清单"}
                                >
                                    {isLoadingFav ? (
                                        <Loader2 className="w-5 h-5 animate-spin" />
                                    ) : isFavorite ? (
                                        <Check className="w-5 h-5" />
                                    ) : (
                                        <Plus className="w-5 h-5 group-hover/btn:rotate-90 transition-transform" />
                                    )}
                                </button>
                            </motion.div>
                        </motion.div>
                    </div>

                    <div className="md:col-span-5 lg:col-span-5 h-full flex items-center justify-center md:justify-center order-1 md:order-2 perspective-1000">
                        <motion.div
                            style={{
                                rotateX: posterRotateX,
                                rotateY: posterRotateY,
                                transformStyle: "preserve-3d"
                            }}
                            className="relative w-[200px] md:w-[260px] lg:w-[320px] aspect-[2/3] transform-gpu"
                        >
                            <div className="absolute inset-0 bg-primary/20 blur-[40px] rounded-full translate-y-10 -z-10" />

                            <div className="w-full h-full rounded-xl overflow-hidden shadow-2xl ring-1 ring-border-subtle/10 relative bg-neutral-900">
                                <QuantumImage
                                    src={movie.posterUrl}
                                    alt={movie.title}
                                    fill
                                    className="object-cover"
                                    loading="eager"
                                />
                                <div className="absolute inset-0 bg-gradient-to-tr from-black/20 to-transparent z-10" />
                            </div>
                        </motion.div>
                    </div>
                </div>

                <div className="absolute bottom-0 left-0 h-1 bg-border/10 w-full z-20">
                    <motion.div
                        initial={{ width: "0%" }}
                        animate={{ width: "100%" }}
                        transition={{ duration: duration / 1000, ease: "linear" }}
                        className="h-full bg-foreground/40 origin-left"
                    />
                </div>
            </motion.div>
        </Link>
    );
}

function Badge({ icon, text, className }: { icon?: React.ReactNode, text: string, className?: string }) {
    return (
        <div className={cn(
            "flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold backdrop-blur-sm transition-colors",
            className || "bg-secondary/30 border-border-subtle/10 text-foreground"
        )}>
            {icon}
            <span>{text}</span>
        </div>
    );
}
</file>

<file path="src/components/features/MovieCard.tsx">
"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { Star, Play } from "lucide-react";
import { MovieCard as MovieCardType } from "@/lib/types";
import { cn } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";

interface MovieCardProps {
    movie: MovieCardType;
    className?: string;
    index?: number;
}

export function MovieCard({ movie, className, index = 0 }: MovieCardProps) {
    const seoAlt = `${movie.title} ${movie.season ? `S${movie.season}` : ''} - ${movie.releaseYear} ${movie.categoryName || ''}`;

    return (
        <motion.div
            initial={{ opacity: 0, y: 15 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-20px" }}
            transition={{ delay: index * 0.02, duration: 0.4, ease: "easeOut" }}
            className={cn(
                "group relative aspect-[2/3] rounded-lg overflow-hidden density-card select-none",
                className
            )}
        >
            <Link href={`/movie/${movie.id}`} className="block w-full h-full relative" title={`观看 ${movie.title}`}>
                <QuantumImage
                    src={movie.posterUrl}
                    alt={seoAlt}
                    fill
                    sizes="(max-width: 640px) 33vw, (max-width: 1024px) 20vw, 160px"
                    className="object-cover transition-transform duration-500 group-hover:scale-105"
                />

                {/* Season Badge (Top Left) */}
                {movie.season && movie.season > 0 && (
                    <div className="absolute top-1.5 left-1.5 z-30">
                        <span className="px-1.5 py-0.5 rounded text-[9px] font-black text-white bg-indigo-600/90 backdrop-blur-sm border border-indigo-500/20 shadow-sm tracking-tight font-mono">
                            S{movie.season}
                        </span>
                    </div>
                )}

                {/* Remarks Badge (Top Right) */}
                {movie.remarks && (
                    <div className="absolute top-1.5 right-1.5 z-30">
                        <span className="px-1.5 py-0.5 rounded text-[9px] font-bold text-white bg-black/60 backdrop-blur-sm border border-white/10 shadow-sm tracking-tight">
                            {movie.remarks}
                        </span>
                    </div>
                )}

                <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/40 to-transparent opacity-90 z-20" />

                <div className="absolute inset-0 p-3 flex flex-col justify-end z-30">
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 scale-90 group-hover:scale-100 transition-all duration-300 ease-out">
                        <div className="w-10 h-10 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center border border-white/20 shadow-xl">
                            <Play className="w-4 h-4 text-white fill-white ml-0.5" />
                        </div>
                    </div>

                    <div className="transform translate-y-1 group-hover:translate-y-0 transition-transform duration-300 ease-out">
                        <h3 className="text-white font-bold text-sm leading-tight line-clamp-1 mb-1 drop-shadow-sm group-hover:text-indigo-300 transition-colors">
                            {movie.title}
                        </h3>

                        {movie.subtitle ? (
                            <p className="text-[10px] text-neutral-300 line-clamp-1 mb-1.5 font-medium">
                                {movie.subtitle}
                            </p>
                        ) : movie.aliasTitle ? (
                            <p className="text-[10px] text-neutral-400 line-clamp-1 mb-1.5 hidden sm:block">
                                {movie.aliasTitle}
                            </p>
                        ) : null}

                        <div className="flex items-center justify-between text-[10px] text-neutral-300 font-medium">
                            <div className="flex items-center gap-2">
                                {movie.rating > 0 ? (
                                    <span className="flex items-center gap-0.5 text-amber-400 font-bold">
                                        <Star className="w-2.5 h-2.5 fill-current" />
                                        {movie.rating}
                                    </span>
                                ) : (
                                    <span className="text-neutral-500">暂无评分</span>
                                )}
                            </div>

                            <div className="flex items-center gap-2">
                                <span>{movie.releaseYear}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="absolute inset-0 border border-border-subtle/10 group-hover:border-border-subtle/30 rounded-lg transition-colors duration-300 z-40 pointer-events-none" />
            </Link>
        </motion.div>
    );
}
</file>

<file path="src/lib/api/auth.ts">
import { RegisterResponse, User } from "@/lib/types";

const API_BASE = "/api/portal/auth";
const PROFILE_BASE = "/api/portal/profile";

export interface PoWVerification {
    id: string;
    nonce: string;
}

export interface PoWChallenge {
    id: string;
    difficulty: number;
}

export interface CheckInResult {
    earnedPoints: number;
    currentPoints: number;
    checkInStreak: number;
    experience: number;
    level: number;
}

export interface MemberTokenVO {
    token: string;
    id: string;
    email: string;
    nickname: string;
    avatarUrl: string;
    role: string;
    status: User['status'];

    experience: number;
    points: number;
    level: number;
    nextLevelXp: number;
    checkInStreak: number;
    title: string;
    lastCheckInDate: string | null;
}

async function handleResponse<T>(res: Response): Promise<T> {
    if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));

        if (errorData && errorData.message) {
            throw new Error(errorData.message);
        }

        switch (res.status) {
            case 401: throw new Error("认证失败或登录过期");
            case 403: throw new Error("您没有权限执行此操作");
            case 404: throw new Error("请求的资源不存在");
            case 429: throw new Error("请求过于频繁，请稍后再试");
            case 500: throw new Error("服务器内部错误");
            case 502: throw new Error("网关错误，服务可能暂时不可用");
            case 504: throw new Error("请求超时");
            default: throw new Error(`请求失败 (${res.status})`);
        }
    }

    if (res.status === 204) {
        return {} as T;
    }
    return res.json();
}

export const authApi = {
    async getPoWChallenge(): Promise<PoWChallenge> {
        const res = await fetch(`${API_BASE}/pow`, { method: "GET" });
        return handleResponse<PoWChallenge>(res);
    },

    async login(email: string, password: string, powVerification: PoWVerification): Promise<MemberTokenVO> {
        const res = await fetch(`${API_BASE}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password, powVerification }),
        });
        return handleResponse<MemberTokenVO>(res);
    },

    async register(email: string, password: string, nickname: string, powVerification: PoWVerification): Promise<RegisterResponse> {
        const res = await fetch(`${API_BASE}/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password, nickname, powVerification }),
        });
        return handleResponse<RegisterResponse>(res);
    },

    async activate(email: string, code: string): Promise<MemberTokenVO> {
        const res = await fetch(`${API_BASE}/activate`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, code }),
        });
        return handleResponse<MemberTokenVO>(res);
    },

    async resendCode(email: string): Promise<void> {
        const res = await fetch(`${API_BASE}/resend-code`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email }),
        });
        return handleResponse<void>(res);
    },

    async forgotPassword(email: string, nickname: string, powVerification: PoWVerification): Promise<void> {
        const res = await fetch(`${API_BASE}/forgot-password`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, nickname, powVerification }),
        });
        return handleResponse<void>(res);
    },

    async resetPassword(token: string, newPassword: string): Promise<void> {
        const res = await fetch(`${API_BASE}/reset-password`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token, newPassword }),
        });
        return handleResponse<void>(res);
    },

    async getMe(token: string): Promise<User> {
        const res = await fetch(`${PROFILE_BASE}`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (res.status === 401 || res.status === 403 || res.status === 404) {
            throw new Error("Session expired");
        }

        return handleResponse<User>(res);
    },

    async updateProfile(token: string, nickname: string): Promise<User> {
        const res = await fetch(`${PROFILE_BASE}`, {
            method: "PUT",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ nickname })
        });
        return handleResponse<User>(res);
    },

    async changePassword(token: string, oldPass: string, newPass: string): Promise<void> {
        const res = await fetch(`${PROFILE_BASE}/password`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ oldPassword: oldPass, newPassword: newPass })
        });
        return handleResponse<void>(res);
    },

    async uploadAvatar(token: string, file: File): Promise<{ url: string }> {
        const formData = new FormData();
        formData.append("file", file);

        const res = await fetch(`${PROFILE_BASE}/avatar`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` },
            body: formData
        });
        return handleResponse<{ url: string }>(res);
    },

    async checkIn(token: string): Promise<CheckInResult> {
        const res = await fetch(`${PROFILE_BASE}/check-in`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
        });
        return handleResponse<CheckInResult>(res);
    }
};
</file>

<file path="src/lib/api/content.ts">
import { ExploreOptions, HomeData, MovieDetail, PageResponse, PortalMetadata } from "@/lib/types";

const API_PROXY_BASE = "/api/portal";

const getBaseUrl = (): string => {
    if (process.env.BACKEND_URL) {
        return `${process.env.BACKEND_URL}/api/portal`;
    }

    if (process.env.API_INTERNAL_URL) {
        return process.env.API_INTERNAL_URL;
    }

    if (typeof window !== 'undefined') {
        return API_PROXY_BASE;
    }

    return 'http://localhost:8080/api/portal';
};

const getAuthHeaders = (extraHeaders?: Record<string, string>) => {
    const headers: Record<string, string> = {
        "Content-Type": "application/json",
        ...extraHeaders
    };
    if (typeof window !== 'undefined') {
        const token = localStorage.getItem("huawai_token");
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }
    }
    return headers;
};

async function fetchWithRetry(url: string, options: RequestInit, retries = 2, backoff = 500): Promise<Response> {
    try {
        const res = await fetch(url, options);
        if (!res.ok) {
            if (res.status >= 500 && retries > 0) {
                console.warn(`[API] Fetch failed (${res.status}), retrying in ${backoff}ms...`);
                await new Promise(r => setTimeout(r, backoff));
                return fetchWithRetry(url, options, retries - 1, backoff * 2);
            }
        }
        return res;
    } catch (err) {
        if (retries > 0) {
            console.warn(`[API] Network error, retrying in ${backoff}ms...`, err);
            await new Promise(r => setTimeout(r, backoff));
            return fetchWithRetry(url, options, retries - 1, backoff * 2);
        }
        throw err;
    }
}

export const contentApi = {
    async getHomeData(headers?: Record<string, string>): Promise<HomeData> {
        const baseUrl = getBaseUrl();
        const url = `${baseUrl}/home`;

        const res = await fetchWithRetry(url, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 60 }
        });

        if (!res.ok) throw new Error(`Failed to fetch home data: ${res.status}`);
        return res.json();
    },

    async getMetadata(headers?: Record<string, string>): Promise<PortalMetadata> {
        const baseUrl = getBaseUrl();

        const res = await fetchWithRetry(`${baseUrl}/metadata`, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 3600 }
        });
        if (!res.ok) throw new Error("Failed to fetch metadata");
        return res.json();
    },

    async explore(options: ExploreOptions, headers?: Record<string, string>): Promise<PageResponse<any>> {
        const params = new URLSearchParams({
            page: (options.page || 0).toString(),
            size: (options.size || 20).toString(),
        });

        if (options.category && options.category !== 'all') params.append("type", options.category);
        if (options.sort) params.append("sort", options.sort);
        if (options.keyword) params.append("keyword", options.keyword);
        if (options.genre && options.genre !== '全部') params.append("genre", options.genre);
        if (options.area && options.area !== '全部') params.append("area", options.area);
        if (options.year && options.year !== '全部') params.append("year", options.year);
        if (options.language && options.language !== '全部') params.append("language", options.language);

        const baseUrl = getBaseUrl();

        const res = await fetchWithRetry(`${baseUrl}/explore?${params.toString()}`, {
            headers: getAuthHeaders(headers),
            cache: 'no-store'
        });

        if (!res.ok) throw new Error("Failed to fetch explore data");
        return res.json();
    },

    async getMovieDetail(id: string, headers?: Record<string, string>): Promise<MovieDetail | null> {
        const baseUrl = getBaseUrl();
        const url = `${baseUrl}/movies/${id}`;

        const res = await fetchWithRetry(url, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 60 }
        });

        if (!res.ok) {
            if (res.status === 404) return null;
            throw new Error(`Failed to fetch movie detail: ${res.status}`);
        }
        return res.json();
    }
};
</file>

<file path="src/app/explore/page.tsx">
import { Suspense } from "react";
import { contentApi } from "@/lib/api/content";
import ExploreClient from "./ExploreClient";
import { Loader2 } from "lucide-react";
import { PortalMetadata } from "@/lib/types";
import { Metadata } from "next";

type SearchParams = Promise<{ [key: string]: string | string[] | undefined }>;

type Props = {
    searchParams: SearchParams;
};

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
    const params = await searchParams;

    const keyword = typeof params.keyword === 'string' ? params.keyword : '';
    const category = typeof params.type === 'string' && params.type !== 'all' ? params.type : '';
    const genre = typeof params.genre === 'string' && params.genre !== '全部' ? params.genre : '';
    const year = typeof params.year === 'string' && params.year !== '全部' ? params.year : '';
    const area = typeof params.area === 'string' && params.area !== '全部' ? params.area : '';

    const categoryMap: Record<string, string> = {
        'movie': '电影',
        'series': '剧集',
        'anime': '动漫',
        'short-drama': '短剧',
        'variety': '综艺'
    };

    const titleParts = [];
    if (keyword) titleParts.push(`"${keyword}"的搜索结果`);
    if (year) titleParts.push(`${year}年`);
    if (area) titleParts.push(area);
    if (genre) titleParts.push(genre);
    if (category) titleParts.push(categoryMap[category] || category);
    if (titleParts.length === 0) titleParts.push("探索");

    const title = titleParts.join(" ");

    return {
        title: title,
        description: `在画外探索${titleParts.join("、")}。提供海量4K、HDR影视资源，沉浸式观影体验。`,
        openGraph: {
            title: `${title} - 画外 Huawai`,
            description: `发现更多 ${title} 相关的精彩内容。`
        },
        alternates: {
            canonical: `/explore`,
        }
    };
}

export default async function ExplorePage(props: { searchParams: SearchParams }) {
    const searchParams = await props.searchParams;

    const category = (searchParams.type as string) || "all";
    const sort = (searchParams.sort as string) || "latest";
    const genre = (searchParams.genre as string) || "全部";
    const area = (searchParams.area as string) || "全部";
    const year = (searchParams.year as string) || "全部";
    const language = (searchParams.language as string) || "全部";
    const keyword = (searchParams.keyword as string) || "";

    const [metadata, searchData] = await Promise.all([
        contentApi.getMetadata().catch(() => null),
        contentApi.explore({
            page: 0,
            size: 50,
            category,
            sort,
            keyword,
            genre,
            area,
            year,
            language
        }).catch(() => ({ content: [], totalElements: 0 }))
    ]);

    return (
        <div className="min-h-screen bg-background pt-28 pb-20 px-4 md:px-8 flex flex-col">
            <Suspense fallback={
                <div className="min-h-screen bg-background pt-32 flex justify-center">
                    <Loader2 className="w-10 h-10 animate-spin text-muted-foreground" />
                </div>
            }>
                <ExploreClient
                    initialResults={searchData.content || []}
                    initialTotal={searchData.totalElements || 0}
                    initialMetadata={metadata as PortalMetadata}
                    initialParams={{ category, sort, genre, area, year, language, keyword }}
                />
            </Suspense>
        </div>
    );
}
</file>

<file path="src/app/movie/[id]/page.tsx">
import React from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Metadata } from "next";
import { headers } from "next/headers";
import { Clapperboard, Hash } from "lucide-react";

// Components
import { CastList } from "@/components/features/CastList";
import { ResourceSelector } from "@/components/features/ResourceSelector";
import { MovieRail } from "@/components/features/MovieRail";
import { MovieHero } from "@/components/features/movie/MovieHero";
import { MovieStructuredData } from "@/components/features/movie/MovieStructuredData";

// Libs & APIs
import { contentApi } from "@/lib/api/content";
import { buildForwardedHeaders } from "@/lib/utils";
import { MovieCard, MovieDetail } from "@/lib/types";
import { SITE_URL, SITE_NAME } from "@/lib/seo";

type Props = {
    params: Promise<{ id: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
    const { id } = await params;
    const headersList = await headers();
    const forwardedHeaders = buildForwardedHeaders(headersList);

    try {
        const movie = await contentApi.getMovieDetail(id, forwardedHeaders);
        if (!movie) return { title: "页面未找到 - 画外 Huawai" };

        const hasMagnet = movie.magnets && movie.magnets.length > 0;
        const hasCloud = movie.cloudLinks && movie.cloudLinks.length > 0;
        const hasOnline = movie.sources && movie.sources.length > 0;
        const isSeries = movie.categoryName === '剧集' || movie.categoryName === '动漫';

        const keywordsBuffer: string[] = [];

        if (hasOnline) keywordsBuffer.push("免费在线观看", "高清播放");
        if (hasCloud) keywordsBuffer.push("夸克网盘", "阿里云盘");
        if (hasMagnet) keywordsBuffer.push("迅雷下载", "BT种子");

        keywordsBuffer.push("4K HDR", "1080P", "无删减");

        let statusText = "";
        if (isSeries) {
            statusText = movie.remarks ? ` ${movie.remarks}` : " 全集";
        } else {
            statusText = " 完整版";
        }

        const titleSuffix = keywordsBuffer.slice(0, 3).join("/");
        const title = `${movie.title}${statusText} - ${titleSuffix} - ${SITE_NAME}`;

        const castNames = movie.cast?.slice(0, 4).map(c => c.name).join("/");
        const cleanDesc = movie.description
            ? movie.description.substring(0, 100).replace(/[\r\n\s]+/g, '')
            : `${movie.title}剧情介绍`;

        const description = `【${SITE_NAME}】提供《${movie.title}》${keywordsBuffer.slice(0, 4).join("、")}。${isSeries ? '剧集' : '电影'}由${castNames}等主演。剧情简介：${cleanDesc}...[点击查看完整资源]`;

        const seoKeywords = [
            movie.title,
            `${movie.title}下载`,
            `${movie.title}在线看`,
            `${movie.title}免费`,
            `${movie.title}剧情`,
            `${movie.title}4k`,
            ...(movie.aliasTitle ? movie.aliasTitle.split(/[\/,]/) : []),
            ...(movie.genres || []),
            movie.releaseYear,
            "画外影视",
            "蓝光原盘"
        ];

        const ogParams = new URLSearchParams();
        ogParams.set('title', movie.title);
        ogParams.set('rating', movie.rating?.toString() || '');
        ogParams.set('year', movie.releaseYear);
        if (movie.posterUrl) ogParams.set('poster', movie.posterUrl);
        const ogImageUrl = `${SITE_URL}/api/og?${ogParams.toString()}`;

        return {
            title: title,
            description: description,
            keywords: seoKeywords,
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
    const headersList = await headers();
    const forwardedHeaders = buildForwardedHeaders(headersList);

    let movie: MovieDetail | null = null;
    try {
        movie = await contentApi.getMovieDetail(id, forwardedHeaders);
    } catch (e) {
        console.error("Movie fetch error:", e);
        return notFound();
    }

    if (!movie) return notFound();

    const relatedData = await contentApi.explore({
        page: 0,
        size: 12,
        genre: movie.genres?.[0] || '',
        category: movie.categoryName === '剧集' ? 'series' : 'movie'
    }).catch(() => ({ content: [] }));

    const relatedMovies: MovieCard[] = (relatedData.content || [])
        .filter((m: MovieCard) => m.id !== movie.id);

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

            {relatedMovies.length > 0 && (
                <div className="max-w-[1600px] mx-auto px-0 md:px-4 mb-16 relative z-10">
                    <div className="px-6 md:px-8 flex items-center gap-3 mb-2">
                        <div className="w-1.5 h-6 bg-emerald-500 rounded-full shadow-[0_0_15px_#10b981]" />
                        <h2 className="text-2xl font-bold text-foreground tracking-wide">
                            喜欢《{movie.title}》的人也爱看
                        </h2>
                    </div>
                    <MovieRail category={{ id: 'related', title: '', movies: relatedMovies }} />
                </div>
            )}

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
</file>

<file path="src/lib/types/index.ts">
// 通用分页响应
export interface PageResponse<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
    last: boolean;
    first: boolean;
    empty: boolean;
}

export interface CategoryItem {
    name: string;
    slug: string;
}

export interface PortalMetadata {
    categories: CategoryItem[];
    genres: string[];
    areas: string[];
    languages: string[];
    years: string[];
}

export interface MagnetLink {
    id?: string;
    title: string;
    link: string;
    size?: string;
    date?: string;
    uploadDate?: string;
    tags?: string[];
}

export interface CloudLink {
    id?: string;
    title: string;
    link: string;
    code?: string;
    provider?: string;
}

export interface MovieCard {
    id: string;
    title: string;
    aliasTitle?: string;
    posterUrl: string;
    rating: number;
    releaseYear: string;
    categoryName: string;
    area: string;
    description?: string;
    genres?: string[];
    lastTrendAt?: string;

    remarks?: string;
    totalEpisodes?: string;
    duration?: string;

    // 新增字段
    season?: number;
    subtitle?: string;

    uniqueId?: string;
}

export interface CastMember {
    id: string;
    name: string;
    role: string;
}

export interface Episode {
    id: string;
    title: string;
    url: string;
}

export interface VideoSource {
    id: string;
    name: string;
    episodes: Episode[];
}

export interface MovieDetail {
    id: string;
    title: string;
    aliasTitle?: string;
    tagline?: string;
    description: string;
    rating: number;
    releaseYear: string;

    duration?: string;
    totalEpisodes?: string;
    remarks?: string;
    lastTrendAt?: string;

    // 新增字段
    season?: number;
    subtitle?: string;

    posterUrl: string;

    area: string;
    language: string;
    categoryName: string;

    doubanId?: string;
    tmdbId?: string;
    imdbId?: string;

    genres: string[];
    cast: CastMember[];

    sources: VideoSource[];
    magnets: MagnetLink[];
    cloudLinks: CloudLink[];
}

// 对应后端 PortalHomeVO
export interface HomeData {
    spotlight: MovieCard[];
    trending: MovieCard[];
    sections: CategorySection[];
}

export interface CategorySection {
    id: string;
    title: string;
    movies: MovieCard[];
}

// 用户相关
export interface User {
    id: string;
    email: string;
    nickname: string;
    avatarUrl: string;
    role: string;
    status: "PENDING_VERIFICATION" | "ACTIVE" | "BANNED";
    token?: string;

    experience: number;
    points: number;
    level: number;
    nextLevelXp: number;
    lastCheckInDate: string | null;
    checkInStreak: number;
    title: string;
}

export interface RegisterResponse {
    message: string;
    verifyRequired: boolean;
}

export interface InteractionRecord {
    id: string;
    unifiedVideoId: string;
    title: string;
    coverImageUrl: string;
    score: number;

    lastVideoId?: string;
    lastEpisodeId?: string;
    episodeName?: string;
    progressSeconds: number;
    durationSeconds: number;
    lastWatchedAt: string;

    isFavorite: boolean;
}

export type VideoFormat = 'hls' | 'mp4' | 'embed';

export interface ExploreOptions {
    page?: number;
    size?: number;
    keyword?: string;
    category?: string;
    genre?: string;
    area?: string;
    year?: string;
    language?: string;
    sort?: string;
}
</file>

<file path="src/components/layout/Navbar.tsx">
"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname, useSearchParams } from "next/navigation";
import { motion, useScroll, useMotionValueEvent, AnimatePresence } from "framer-motion";
import { useState, useEffect } from "react";
import { Search, History, Menu, X, Home, Clapperboard, Tv, Sparkles, Mic2, Ghost, Heart, EyeOff, MessageSquare, Sun, Moon } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/lib/context/AuthContext";
import { useTheme } from "@/lib/context/ThemeContext";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { MobileMenu } from "./navbar/MobileMenu";
import React from "react";

const NAV_ITEMS = [
    { name: "首页", href: "/", icon: <Home className="w-4 h-4" /> },
    { name: "电影", href: "/explore?type=movie", icon: <Clapperboard className="w-4 h-4" /> },
    { name: "剧集", href: "/explore?type=series", icon: <Tv className="w-4 h-4" /> },
    { name: "短剧", href: "/explore?type=short-drama", icon: <Sparkles className="w-4 h-4 text-amber-500" /> },
    { name: "综艺", href: "/explore?type=variety", icon: <Mic2 className="w-4 h-4" /> },
    { name: "动漫", href: "/explore?type=anime", icon: <Ghost className="w-4 h-4" /> },
    { name: "收藏", href: "/my-list", icon: <Heart className="w-4 h-4" /> },
    { name: "留言", href: "/feedback", icon: <MessageSquare className="w-4 h-4" /> },
];

export function Navbar() {
    const { user, isLoading, logout, isStealthMode } = useAuth();
    const { setTheme, resolvedTheme } = useTheme();
    const { scrollY } = useScroll();

    const [scrolled, setScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

    const pathname = usePathname();
    const searchParams = useSearchParams();

    const [prevPath, setPrevPath] = useState(pathname);
    const [prevSearch, setPrevSearch] = useState(searchParams?.toString());

    if (pathname !== prevPath || searchParams?.toString() !== prevSearch) {
        setPrevPath(pathname);
        setPrevSearch(searchParams?.toString());
        setIsMobileMenuOpen(false);
    }

    useMotionValueEvent(scrollY, "change", (latest) => {
        const isScrolledNow = latest > 20;
        if (isScrolledNow !== scrolled) {
            setScrolled(isScrolledNow);
        }
    });

    useEffect(() => {
        if (isMobileMenuOpen) {
            document.body.style.overflow = "hidden";
        } else {
            document.body.style.overflow = "unset";
        }
    }, [isMobileMenuOpen]);

    const checkActive = (href: string) => {
        if (href === "/") return pathname === "/";
        const [path, query] = href.split("?");
        if (pathname !== path) return false;
        if (!query) return true;
        const targetParams = new URLSearchParams(query);
        let matches = true;
        targetParams.forEach((val, key) => {
            if (searchParams.get(key) !== val) matches = false;
        });
        return matches;
    };

    const toggleTheme = () => {
        setTheme(resolvedTheme === 'dark' ? 'light' : 'dark');
    };

    const canUseSearch = user && user.status === "ACTIVE";

    if (pathname === "/login" || pathname === "/signup") return null;

    return (
        <>
            <motion.header
                initial={{ y: 0, opacity: 1 }}
                animate={{ y: 0, opacity: 1 }}
                className={cn(
                    "fixed top-0 left-0 w-full z-50 flex justify-center pointer-events-none transition-all duration-200 ease-out",
                    scrolled ? "pt-4" : "pt-0 md:pt-6"
                )}
            >
                <div
                    onMouseLeave={() => setHoveredIndex(null)}
                    className={cn(
                        "pointer-events-auto flex items-center justify-between gap-4 transition-all duration-200 ease-out relative isolate",
                        scrolled || isMobileMenuOpen
                            ? "px-6 py-3 rounded-full bg-background/80 backdrop-blur-xl shadow-lg border border-border-subtle/10 w-[95%] md:w-full max-w-7xl text-foreground"
                            : "px-6 py-4 bg-transparent border-transparent w-full max-w-[1600px] border-b border-white/0 text-foreground"
                    )}
                >
                    <div className="flex items-center gap-6">
                        <Link href="/" className="flex items-center gap-3 group select-none">
                            <div className="relative w-10 h-10 group-hover:scale-105 transition-transform duration-500 ease-out">
                                <Image
                                    src="/logo.svg"
                                    alt="Huawai Logo"
                                    fill
                                    className="drop-shadow-sm transition-all duration-300 dark:invert"
                                    priority
                                />
                            </div>

                            <div className="flex flex-col">
                                <span className={cn(
                                    "text-lg font-bold tracking-tight transition-colors font-serif leading-none text-foreground",
                                )}>
                                    画 外
                                </span>
                                <span className={cn(
                                    "text-[9px] font-sans font-medium tracking-[0.2em] uppercase leading-none mt-1 opacity-60 text-muted-foreground",
                                )}>
                                    HUAWAI
                                </span>
                            </div>
                        </Link>
                    </div>

                    <nav className="hidden lg:flex items-center relative">
                        {NAV_ITEMS.map((item, index) => {
                            const isActive = checkActive(item.href);
                            return (
                                <Link
                                    key={item.name}
                                    href={item.href}
                                    onMouseEnter={() => setHoveredIndex(index)}
                                    className={cn(
                                        "relative px-4 py-2 text-sm font-bold transition-all z-10 flex items-center gap-2",
                                        isActive
                                            ? "text-primary"
                                            : "text-muted-foreground hover:text-foreground",
                                        item.name === "短剧" && !isActive && "text-amber-500/80"
                                    )}
                                >
                                    {isActive && (
                                        <motion.span
                                            layoutId="active-dot"
                                            className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary shadow-[0_0_8px_currentColor]"
                                        />
                                    )}
                                    <span className={cn(
                                        "transition-colors",
                                        isActive
                                            ? "text-primary"
                                            : "text-muted-foreground group-hover:text-foreground"
                                    )}>
                                        {item.icon}
                                    </span>
                                    {item.name}
                                </Link>
                            );
                        })}
                    </nav>

                    <div className="flex items-center gap-3">
                        <button
                            onClick={toggleTheme}
                            className="group relative p-2.5 rounded-full transition-all active:scale-95 text-muted-foreground hover:text-foreground hover:bg-secondary"
                            title="切换主题"
                        >
                            <Sun className="w-4 h-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0 dark:hidden block" />
                            <Moon className="w-4 h-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100 hidden dark:block" />
                        </button>

                        {canUseSearch && (
                            <Link href="/search">
                                <ActionBtn
                                    icon={<Search className="w-4 h-4" />}
                                    tooltip="全站搜索"
                                />
                            </Link>
                        )}

                        <div className="hidden lg:flex items-center gap-3">
                            {user && (
                                <Link href="/history">
                                    <ActionBtn
                                        icon={<History className="w-4 h-4" />}
                                        tooltip="观看历史"
                                    />
                                </Link>
                            )}

                            <div className={cn(
                                "w-px h-4 mx-1 bg-border-subtle/20",
                            )} />

                            {!isLoading && user ? (
                                <Link href="/profile" className="group relative pl-1">
                                    <div className={cn(
                                        "flex items-center gap-3 p-1 pr-3 rounded-full transition-all border",
                                        "bg-secondary/50 border-transparent hover:border-border-subtle/20 text-foreground"
                                    )}>
                                        <div className="relative">
                                            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 p-[1.5px]">
                                                <div className="w-full h-full rounded-full bg-background overflow-hidden relative">
                                                    <QuantumImage
                                                        src={user.avatarUrl || "https://i.pravatar.cc/150?u=a"}
                                                        alt={user.nickname}
                                                        fill
                                                        className="object-cover"
                                                    />
                                                </div>
                                            </div>
                                            {isStealthMode && (
                                                <div className="absolute -bottom-1 -right-1 bg-background rounded-full p-0.5 border border-border" title="隐身模式已开启">
                                                    <div className="bg-indigo-600 rounded-full p-0.5">
                                                        <EyeOff className="w-2.5 h-2.5 text-white" />
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                        <div className="flex flex-col text-left">
                                            <span className="text-xs font-bold leading-none">{user.nickname}</span>
                                            <span className="text-[10px] text-primary font-mono leading-none mt-1">Lv.{user.level}</span>
                                        </div>
                                    </div>
                                </Link>
                            ) : (
                                <div className="flex items-center gap-2">
                                    <Link href="/login">
                                        <button className="px-5 py-2 rounded-full text-xs font-bold transition-all active:scale-95 border bg-transparent text-muted-foreground border-transparent hover:bg-secondary hover:text-foreground">
                                            登录
                                        </button>
                                    </Link>
                                    <Link href="/signup">
                                        <button className="px-5 py-2 rounded-full text-xs font-bold transition-all shadow-lg active:scale-95 border bg-primary text-primary-foreground border-primary hover:bg-primary/90">
                                            注册
                                        </button>
                                    </Link>
                                </div>
                            )}
                        </div>

                        <button
                            className="lg:hidden p-2 transition-colors text-muted-foreground hover:text-foreground"
                            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                        >
                            {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                        </button>
                    </div>
                </div>
            </motion.header>

            <AnimatePresence>
                <MobileMenu
                    isOpen={isMobileMenuOpen}
                    user={user}
                    isLoading={isLoading}
                    onLogout={logout}
                    navItems={NAV_ITEMS}
                    checkActive={checkActive}
                />
            </AnimatePresence>
        </>
    );
}

function ActionBtn({ icon, tooltip }: { icon: React.ReactNode, tooltip: string }) {
    return (
        <button className="group relative p-2.5 rounded-full transition-all active:scale-95 text-muted-foreground hover:text-foreground hover:bg-secondary">
            {icon}
            <span className="absolute top-full left-1/2 -translate-x-1/2 mt-3 px-2 py-1 bg-popover border border-border rounded text-[10px] text-popover-foreground opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-50 shadow-xl">
                {tooltip}
            </span>
        </button>
    );
}
</file>

</files>
