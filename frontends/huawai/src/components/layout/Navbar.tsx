"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname, useSearchParams } from "next/navigation";
import { useState, useEffect } from "react";
import { Menu, X, Heart, EyeOff, Sun, Moon, Clapperboard } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/lib/context/AuthContext";
import { useTheme } from "@/lib/context/ThemeContext";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { MobileMenu } from "./navbar/MobileMenu";
import type { CategoryItem } from "@/lib/types";
import { CATEGORY_NAV_STYLES, categoryDisplayName, normalizePortalCategories } from "@/lib/portalCategories";
import { usePortalCategories } from "@/hooks/usePortalCategories";

interface NavbarProps {
    categories?: CategoryItem[] | null;
}

const STATIC_NAV_ITEMS = [
    { name: "收藏", href: "/my-list", icon: <Heart className="w-4 h-4" /> },
    { name: "求片", href: "/requests", icon: <Clapperboard className="w-4 h-4" /> },
];

const TOP_NAV_CATEGORY_SLUGS = ["movie", "series", "short-drama", "anime", "variety"];

export function Navbar({ categories }: NavbarProps) {
    const { user, isLoading, logout, isStealthMode } = useAuth();
    const { setTheme, resolvedTheme } = useTheme();

    const [scrolled, setScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

    const pathname = usePathname();
    const searchParams = useSearchParams();
    const hydratedCategories = usePortalCategories(categories);
    const navItems = [
        ...normalizePortalCategories(hydratedCategories)
            .filter(category => TOP_NAV_CATEGORY_SLUGS.includes(category.slug))
            .map(category => {
                const style = CATEGORY_NAV_STYLES[category.slug] || CATEGORY_NAV_STYLES.documentary;
                const Icon = style.icon;
                return {
                    name: categoryDisplayName(category.slug),
                    href: `/explore?type=${category.slug}`,
                    icon: <Icon className={cn("w-4 h-4", category.slug === "short-drama" && "text-amber-500")} />
                };
            }),
        ...STATIC_NAV_ITEMS
    ];

    useEffect(() => {
        let frame = 0;

        const syncScrolled = () => {
            frame = 0;
            setScrolled(window.scrollY > 20);
        };

        const handleScroll = () => {
            if (frame === 0) {
                frame = window.requestAnimationFrame(syncScrolled);
            }
        };

        syncScrolled();
        window.addEventListener("scroll", handleScroll, { passive: true });
        return () => {
            window.removeEventListener("scroll", handleScroll);
            if (frame !== 0) {
                window.cancelAnimationFrame(frame);
            }
        };
    }, []);

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

    if (pathname === "/login" || pathname === "/signup") return null;

    return (
        <>
            <header
                className={cn(
                    "fixed top-0 left-0 w-full z-50 flex justify-center pointer-events-none transition-all duration-200 ease-out",
                    scrolled ? "pt-4" : "pt-0 md:pt-6"
                )}
            >
                <div
                    className={cn(
                        "pointer-events-auto flex items-center justify-between gap-4 transition-all duration-200 ease-out relative isolate",
                        scrolled || isMobileMenuOpen
                            ? "px-6 py-3 rounded-full bg-background/80 backdrop-blur-xl shadow-lg border border-border-subtle/10 w-[95%] md:w-full max-w-7xl text-foreground"
                            : "px-6 py-4 bg-transparent border-transparent w-full max-w-[1600px] border-b border-white/0 text-foreground"
                    )}
                >
                    <div className="flex items-center gap-6">
                        <Link href="/" prefetch={false} className="flex items-center gap-3 group select-none">
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
                        {navItems.map((item) => {
                            const isActive = checkActive(item.href);
                            return (
                                <Link
                                    key={item.name}
                                    href={item.href}
                                    prefetch={false}
                                    className={cn(
                                        "relative px-4 py-2 text-sm font-bold transition-all z-10 flex items-center gap-2",
                                        isActive
                                            ? "text-primary"
                                            : "text-muted-foreground hover:text-foreground",
                                        item.name === "短剧" && !isActive && "text-amber-500/80"
                                    )}
                                >
                                    {isActive && (
                                        <span
                                            className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary shadow-[0_0_8px_currentColor] transition-all duration-200"
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

                        <div className="hidden lg:flex items-center gap-3">
                            <div className={cn(
                                "w-px h-4 mx-1 bg-border-subtle/20",
                            )} />

                            {!isLoading && user ? (
                                <Link href="/profile" prefetch={false} className="group relative pl-1">
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
                                    <Link href="/login" prefetch={false}>
                                        <button className="px-5 py-2 rounded-full text-xs font-bold transition-all active:scale-95 border bg-transparent text-muted-foreground border-transparent hover:bg-secondary hover:text-foreground">
                                            登录
                                        </button>
                                    </Link>
                                    <Link href="/signup" prefetch={false}>
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
            </header>

            <MobileMenu
                isOpen={isMobileMenuOpen}
                user={user}
                isLoading={isLoading}
                onLogout={logout}
                onNavigate={() => setIsMobileMenuOpen(false)}
                navItems={navItems}
                checkActive={checkActive}
            />
        </>
    );
}
