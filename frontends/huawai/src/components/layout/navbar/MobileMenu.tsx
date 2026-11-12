"use client";

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
    onNavigate: () => void;
    navItems: { name: string; href: string; icon: React.ReactNode }[];
    checkActive: (href: string) => boolean;
}

export function MobileMenu({ isOpen, user, isLoading, onLogout, onNavigate, navItems, checkActive }: MobileMenuProps) {
    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-40 bg-background/95 backdrop-blur-3xl pt-28 px-6 lg:hidden flex flex-col animate-in fade-in slide-in-from-top-4 zoom-in-95 duration-300"
        >
            <div className="absolute top-0 right-0 w-64 h-64 bg-primary/10 blur-[100px] rounded-full pointer-events-none" />

            <nav className="flex flex-col space-y-2 relative z-10">
                {navItems.map((item) => {
                    const isActive = checkActive(item.href);
                    return (
                        <div
                            key={item.name}
                            className="animate-in fade-in slide-in-from-left-2 duration-300"
                        >
                            <Link
                                href={item.href}
                                prefetch={false}
                                onClick={onNavigate}
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
                        </div>
                    );
                })}
            </nav>

            <div
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
                            <Link href="/profile" prefetch={false} onClick={onNavigate} className="flex items-center justify-center gap-2 py-3 bg-card hover:bg-secondary rounded-xl text-sm font-bold text-foreground transition-colors border border-border-subtle/5">
                                <User className="w-4 h-4" /> 个人中心
                            </Link>
                            <button onClick={onLogout} className="flex items-center justify-center gap-2 py-3 bg-red-500/10 hover:bg-red-500/20 text-red-500 rounded-xl text-sm font-bold transition-colors">
                                <LogOut className="w-4 h-4" /> 退出登录
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="grid grid-cols-2 gap-4">
                        <Link href="/login" prefetch={false} onClick={onNavigate}>
                            <button className="w-full py-4 bg-foreground text-background font-bold rounded-xl hover:bg-foreground/80 transition-colors">
                                登录
                            </button>
                        </Link>
                        <Link href="/signup" prefetch={false} onClick={onNavigate}>
                            <button className="w-full py-4 bg-secondary text-foreground font-bold rounded-xl border border-border-subtle/10 hover:bg-secondary/80 transition-colors">
                                注册
                            </button>
                        </Link>
                    </div>
                )}
            </div>
        </div>
    );
}
