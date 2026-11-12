"use client";

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
    const baseStyles = "relative flex items-center justify-center gap-2 px-6 py-3 rounded-full font-medium text-sm transition-all duration-300 overflow-hidden outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 active:scale-95";

    const variants = {
        primary: "bg-primary text-primary-foreground hover:bg-primary/90 shadow-lg shadow-primary/25 border border-primary/20",
        secondary: "bg-secondary/80 text-secondary-foreground backdrop-blur-md border border-border-subtle/10 hover:bg-secondary hover:border-border-subtle/20",
        ghost: "bg-transparent text-muted-foreground hover:text-foreground hover:bg-secondary/50"
    };

    return (
        <button
            className={cn(
                baseStyles,
                variants[variant],
                !disabled && "hover:scale-105",
                disabled && "opacity-50 cursor-not-allowed pointer-events-none grayscale",
                className
            )}
            onClick={disabled ? undefined : onClick}
            disabled={disabled}
        >
            {icon && <span className="w-4 h-4">{icon}</span>}
            {children}
        </button>
    );
}
