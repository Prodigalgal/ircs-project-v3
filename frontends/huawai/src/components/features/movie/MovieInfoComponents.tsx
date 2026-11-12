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