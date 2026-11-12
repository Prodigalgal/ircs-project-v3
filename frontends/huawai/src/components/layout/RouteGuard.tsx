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
    "/forgot-password",
    "/reset-password"
];

const PUBLIC_PATH_PREFIXES = [
    "/movie/"
];

const PENDING_ALLOWED_PATHS = [
    "/profile"
];

const AUTH_PAGES = [
    "/login",
    "/signup",
    "/activate",
    "/forgot-password",
    "/reset-password"
];

export function RouteGuard({ children }: { children: React.ReactNode }) {
    const { user, isLoading } = useAuth();
    const router = useRouter();
    const pathname = usePathname();
    const isPublic = PUBLIC_PATHS.includes(pathname) ||
        PUBLIC_PATH_PREFIXES.some(prefix => pathname.startsWith(prefix));

    useEffect(() => {
        if (isLoading) return;

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

        if (AUTH_PAGES.includes(pathname)) {
            router.push("/");
        }

    }, [user, isLoading, isPublic, pathname, router]);

    if (isLoading && !isPublic) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-[#030305]">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-500" />
            </div>
        );
    }

    return <>{children}</>;
}
