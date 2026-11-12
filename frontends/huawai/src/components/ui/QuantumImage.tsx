"use client";

import { ImgHTMLAttributes, memo, useMemo, useState, useSyncExternalStore } from "react";
import { cn } from "@/lib/utils";
import { ImageOff } from "lucide-react";

interface QuantumImageProps extends Omit<
    ImgHTMLAttributes<HTMLImageElement>,
    "src" | "alt" | "onLoad" | "onError" | "loading"
> {
    src?: string | null;
    alt: string;
    containerClassName?: string;
    fallbackText?: string;
    preload?: boolean;
    fill?: boolean;
    sizes?: string;
    loading?: "eager" | "lazy";
}

function QuantumImageComponent({
                                   src,
                                   alt,
                                   className,
                                   containerClassName,
                                   fallbackText = "画外",
                                   preload = false,
                                   sizes,
                                   loading,
                                   fill = false,
                                   ...props
                               }: QuantumImageProps) {
    const hydrated = useSyncExternalStore(
        subscribeToHydration,
        getHydratedSnapshot,
        getServerSnapshot
    );
    const hasValidSrc = useMemo(() => {
        if (!src || typeof src !== "string" || src.trim() === "") return false;
        if (src.startsWith("/")) return true;
        if (src.startsWith("data:image")) return true;
        try {
            new URL(src);
            return true;
        } catch {
            return false;
        }
    }, [src]);
    const [imageState, setImageState] = useState<{
        src: string | null | undefined;
        status: "loading" | "success" | "error";
    }>({
        src,
        status: hasValidSrc ? "loading" : "error",
    });

    const displayStatus = src === imageState.src
        ? imageState.status
        : hasValidSrc ? "loading" : "error";

    const defaultSizes = fill
        ? "(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
        : undefined;
    const shouldLoadImage = hydrated && hasValidSrc && typeof src === "string";

    return (
        <div className={cn("relative overflow-hidden bg-neutral-900 w-full h-full isolate", containerClassName)}>
            {displayStatus === "loading" && hasValidSrc && (
                <div className="absolute inset-0 z-20 flex items-center justify-center bg-neutral-900">
                    <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent -translate-x-full animate-[shimmer_1.5s_infinite]" />
                    <div className="w-full h-full bg-neutral-800/50" />
                </div>
            )}

            {(!hasValidSrc || displayStatus === "error") && (
                <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-neutral-900 p-4 border border-white/5">
                    <ImageOff className="w-8 h-8 text-neutral-700 mb-2" />
                    <span className="text-xs text-neutral-600 font-medium tracking-widest uppercase">
                        {fallbackText}
                    </span>
                    <div className="absolute inset-0 opacity-20 bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.14),transparent_55%)]" />
                </div>
            )}

            {hasValidSrc && shouldLoadImage && (
                // eslint-disable-next-line @next/next/no-img-element -- 封面由用户端直接 lazy 加载，避免 Next 图片代理拖慢首屏。
                <img
                    src={src}
                    alt={alt}
                    loading={preload ? "eager" : loading ?? "lazy"}
                    decoding="async"
                    sizes={sizes || defaultSizes}
                    className={cn(
                        fill && "absolute inset-0 h-full w-full",
                        "transition-all duration-700 ease-out relative z-10",
                        displayStatus === "success"
                            ? "opacity-100 scale-100 blur-0 grayscale-0"
                            : "opacity-0 scale-110 blur-xl grayscale",
                        className
                    )}
                    onLoad={() => setImageState({ src, status: "success" })}
                    onError={() => setImageState({ src, status: "error" })}
                    {...props}
                />
            )}
        </div>
    );
}

const subscribeToHydration = () => () => {};
const getHydratedSnapshot = () => true;
const getServerSnapshot = () => false;

export const QuantumImage = memo(QuantumImageComponent);
