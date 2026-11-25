"use client";

import { useState } from "react";
import { Share2, Loader2, ArrowDown, Heart } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { useToast } from "@/lib/context/ToastContext";
import { usePathname } from "next/navigation";
import { useMediaInteraction } from "@/hooks/useMediaInteraction";

export function MovieActions() {
    const { addToast } = useToast();
    const pathname = usePathname();
    const unifiedVideoId = pathname.split("/").pop() || "";

    const { isFavorite, isLoading: isLoadingList, toggleFavorite } = useMediaInteraction(unifiedVideoId);

    const [isSharing, setIsSharing] = useState(false);

    const handleScrollToPlay = () => {
        const resourceSection = document.getElementById("resources");
        if (resourceSection) {
            resourceSection.scrollIntoView({ behavior: "smooth", block: "start" });
        } else {
            addToast("未找到资源列表", "error");
        }
    };

    const handleShare = async () => {
        if (isSharing) return;
        setIsSharing(true);
        try {
            await navigator.clipboard.writeText(window.location.href);
            addToast("链接已复制", "success");
        } catch {
            addToast("复制失败", "error");
        } finally {
            setIsSharing(false);
        }
    };

    return (
        <div className="flex flex-col sm:flex-row gap-5">
            <GlowButton
                onClick={handleScrollToPlay}
                icon={<ArrowDown className="w-5 h-5" />}
                className="px-8"
            >
                选择播放源
            </GlowButton>

            <GlowButton
                variant={isFavorite ? "ghost" : "secondary"}
                onClick={toggleFavorite}
                className={isFavorite ? "text-pink-400 border-pink-500/30 bg-pink-500/10" : ""}
                icon={
                    isLoadingList ? <Loader2 className="w-5 h-5 animate-spin" /> :
                        <Heart className={`w-5 h-5 ${isFavorite ? "fill-current" : ""}`} />
                }
            >
                {isLoadingList ? "处理中..." : isFavorite ? "已收藏" : "收藏"}
            </GlowButton>

            <GlowButton
                variant="ghost"
                onClick={handleShare}
                icon={isSharing ? <Loader2 className="w-5 h-5 animate-spin" /> : <Share2 className="w-5 h-5" />}
            >
                分享
            </GlowButton>
        </div>
    );
}