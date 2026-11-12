"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2, ShieldCheck, TriangleAlert } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";

export default function OAuthCallbackPage() {
    const router = useRouter();
    const { updateUser } = useAuth();
    const { addToast } = useToast();
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    useEffect(() => {
        const finishLogin = async () => {
            const query = new URLSearchParams(window.location.search);
            const error = query.get("error");
            if (error) {
                const message = query.get("message") || "第三方登录失败，请重新尝试";
                setErrorMessage(message);
                addToast(message, "error");
                return;
            }

            const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
            const token = hash.get("token");
            if (!token) {
                const message = "第三方登录回调缺少登录凭证";
                setErrorMessage(message);
                addToast(message, "error");
                return;
            }

            try {
                const user = await authApi.getMe(token);
                updateUser({ ...user, token });
                addToast(`欢迎回来，${user.nickname || user.email}`, "success");
                router.replace("/");
            } catch {
                const message = "第三方登录凭证校验失败，请重新登录";
                setErrorMessage(message);
                addToast(message, "error");
            }
        };

        void finishLogin();
    }, [addToast, router, updateUser]);

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/10 blur-[120px] rounded-full animate-pulse" />
                <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 blur-[120px] rounded-full animate-pulse delay-1000" />
            </div>

            <div className="w-full max-w-md relative z-10 text-center">
                <div className="relative w-16 h-16 mx-auto mb-6">
                    <Image
                        src="/logo.svg"
                        alt="Huawai"
                        fill
                        className="drop-shadow-xl dark:invert transition-all duration-300"
                        priority
                    />
                </div>

                {errorMessage ? (
                    <div className="space-y-5">
                        <div className="mx-auto h-12 w-12 rounded-full bg-destructive/10 flex items-center justify-center">
                            <TriangleAlert className="h-6 w-6 text-destructive" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-foreground mb-2">登录未完成</h1>
                            <p className="text-sm text-muted-foreground">{errorMessage}</p>
                        </div>
                        <Link
                            href="/login"
                            className="inline-flex min-h-11 items-center justify-center rounded-xl border border-border-subtle/10 bg-secondary/50 px-5 py-3 text-sm font-medium text-foreground transition-all hover:border-primary/30 hover:bg-background"
                        >
                            返回登录
                        </Link>
                    </div>
                ) : (
                    <div className="space-y-5">
                        <div className="mx-auto h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                            <Loader2 className="h-6 w-6 animate-spin text-primary" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-foreground mb-2">正在完成登录</h1>
                            <p className="text-sm text-muted-foreground">请稍候，正在校验第三方账号</p>
                        </div>
                        <div className="flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                            <ShieldCheck className="h-3 w-3" />
                            <span>登录凭证不会出现在服务端日志查询参数中</span>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
