"use client";

import { useEffect } from "react";
import { GlowButton } from "@/components/ui/GlowButton";
import { RefreshCw, WifiOff } from "lucide-react";

export default function Error({
                                  error,
                                  reset,
                              }: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        console.error("Home Page Error:", error);
    }, [error]);

    return (
        <div className="min-h-[70vh] flex flex-col items-center justify-center px-6 text-center">
            <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center mb-6 border border-red-500/20">
                <WifiOff className="w-10 h-10 text-red-500" />
            </div>

            <h2 className="text-2xl font-bold text-foreground mb-3">
                信号连接中断
            </h2>
            <p className="text-muted-foreground max-w-sm mb-8">
                无法获取首页推荐数据。这可能是暂时的网络波动或服务器正在维护。
            </p>

            <GlowButton
                onClick={
                    () => reset()
                }
                icon={<RefreshCw className="w-4 h-4" />}
            >
                重新连接
            </GlowButton>

            <div className="mt-8 text-[10px] font-mono text-muted-foreground/40">
                ERROR_DIGEST: {error.digest || "UNKNOWN"}
            </div>
        </div>
    );
}