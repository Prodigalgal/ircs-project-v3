"use client";

import { useEffect } from "react";

const OPTIONAL_FONT_CLASS = "optional-external-fonts";
const OPTIONAL_FONT_STYLESHEET =
    process.env.NEXT_PUBLIC_OPTIONAL_FONT_STYLESHEET_URL ||
    "https://cdn.jsdelivr.net/npm/@fontsource/geist@5.2.9/latin.css";

function runWhenIdle(callback: () => void) {
    if ("requestIdleCallback" in window) {
        const idleId = window.requestIdleCallback(callback, { timeout: 3000 });
        return () => window.cancelIdleCallback(idleId);
    }

    const timeoutId = globalThis.setTimeout(callback, 1200);
    return () => globalThis.clearTimeout(timeoutId);
}

function timeout(ms: number) {
    return new Promise((resolve) => {
        window.setTimeout(resolve, ms);
    });
}

export function OptionalFontLoader() {
    useEffect(() => {
        if (document.documentElement.classList.contains(OPTIONAL_FONT_CLASS)) {
            return;
        }

        let cancelled = false;

        const cancelIdle = runWhenIdle(() => {
            const existingLink = document.querySelector<HTMLLinkElement>(
                `link[data-optional-font="geist"]`
            );
            if (existingLink) {
                return;
            }

            const link = document.createElement("link");
            link.rel = "stylesheet";
            link.href = OPTIONAL_FONT_STYLESHEET;
            link.media = "print";
            link.crossOrigin = "anonymous";
            link.dataset.optionalFont = "geist";

            link.onload = async () => {
                link.media = "all";

                try {
                    if ("fonts" in document) {
                        await Promise.race([
                            document.fonts.load("400 1em Geist"),
                            timeout(2500),
                        ]);
                    }

                    if (!cancelled) {
                        document.documentElement.classList.add(OPTIONAL_FONT_CLASS);
                    }
                } catch {
                    // External font is a progressive enhancement. Keep the system stack on failure.
                }
            };

            link.onerror = () => {
                link.remove();
            };

            document.head.appendChild(link);
        });

        return () => {
            cancelled = true;
            cancelIdle();
        };
    }, []);

    return null;
}
