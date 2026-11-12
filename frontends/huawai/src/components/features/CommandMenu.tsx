"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { Search, Film, User, LogOut, Laptop, History, X } from "lucide-react";
import { useAuth } from "@/lib/context/AuthContext";
import { cn } from "@/lib/utils";

interface CommandMenuProps {
    initialOpen?: boolean;
}

export function CommandMenu({ initialOpen = false }: CommandMenuProps) {
    const [open, setOpen] = React.useState(initialOpen);
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
