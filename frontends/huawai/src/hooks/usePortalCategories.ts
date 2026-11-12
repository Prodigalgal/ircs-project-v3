"use client";

import { useEffect, useState } from "react";
import { contentApi } from "@/lib/api/content";
import type { CategoryItem } from "@/lib/types";

let metadataPromise: Promise<CategoryItem[]> | null = null;

function loadCategories(): Promise<CategoryItem[]> {
    metadataPromise ??= contentApi.getMetadata()
        .then(metadata => metadata.categories || [])
        .catch(error => {
            metadataPromise = null;
            console.warn("[PortalCategories] Failed to hydrate categories", error);
            return [];
        });
    return metadataPromise;
}

export function usePortalCategories(initialCategories?: CategoryItem[] | null): CategoryItem[] | null {
    const hasInitialCategories = Boolean(initialCategories?.length);
    const [categories, setCategories] = useState<CategoryItem[] | null>(null);

    useEffect(() => {
        if (hasInitialCategories) {
            return;
        }
        let cancelled = false;
        loadCategories().then(nextCategories => {
            if (!cancelled && nextCategories.length > 0) {
                setCategories(nextCategories);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [hasInitialCategories]);

    return hasInitialCategories ? initialCategories! : categories;
}
