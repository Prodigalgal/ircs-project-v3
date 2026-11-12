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