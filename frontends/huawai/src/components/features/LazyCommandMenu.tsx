"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useState } from "react";

const CommandMenu = dynamic(
    () => import("@/components/features/CommandMenu").then((module) => module.CommandMenu),
    { ssr: false }
);

export function LazyCommandMenu() {
    const [shouldLoad, setShouldLoad] = useState(false);

    const loadMenu = useCallback(() => {
        setShouldLoad(true);
    }, []);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "k" && (event.metaKey || event.ctrlKey)) {
                loadMenu();
            }
        };

        document.addEventListener("keydown", handleKeyDown);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [loadMenu]);

    return shouldLoad ? <CommandMenu initialOpen /> : null;
}
