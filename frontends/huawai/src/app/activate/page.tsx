"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { GlowButton } from "@/components/ui/GlowButton";
import { CheckCircle2, Loader2, Mail, ShieldCheck, ArrowLeft } from "lucide-react";
import { motion } from "framer-motion";
import { getErrorMessage } from "@/lib/utils";
import { usePoW } from "@/hooks/usePoW";

export default function ActivatePage() {
    const { user, updateUser } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const [email, setEmail] = useState("");
    const [code, setCode] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isResending, setIsResending] = useState(false);
    const [countdown, setCountdown] = useState(0);
    const [hasCachedActivationEmail, setHasCachedActivationEmail] = useState(false);

    useEffect(() => {
        if (user) {
            if (user.status === "ACTIVE") {
                router.push("/");
                return;
            }
            setEmail(user.email);
        } else {
            const cachedEmail = sessionStorage.getItem("pending_activation_email");
            setHasCachedActivationEmail(Boolean(cachedEmail));
            if (cachedEmail) {
                setEmail(cachedEmail);
            }
        }
    }, [user, router]);

    useEffect(() => {
        let timer: NodeJS.Timeout;
        if (countdown > 0) {
            timer = setInterval(() => {
                setCountdown((prev) => {
                    if (prev <= 1) return 0;
                    return prev - 1;
                });
            }, 1000);
        }
        return () => clearInterval(timer);
    }, [countdown]);

    const handleActivate = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!email) {
            addToast("请输入邮箱地址", "error");
            return;
        }

        setIsSubmitting(true);
        try {
            const powHeaders = await solve("portal.activate");
            const updatedUserVO = await authApi.activate(email, code, powHeaders);

            if (updatedUserVO.token) {
                localStorage.setItem("huawai_token", updatedUserVO.token);
                updateUser(updatedUserVO);
            }

            sessionStorage.removeItem("pending_activation_email");

            addToast("账号激活成功！", "success");

            setTimeout(() => {
                router.push("/");
                router.refresh();
            }, 500);

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "激活失败，请检查验证码", "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleResend = async () => {
        if (!email || countdown > 0) {
            if (!email) addToast("请先填写邮箱地址", "error");
            return;
        }

        setIsResending(true);
        try {
            const powHeaders = await solve("portal.resend-code");
            await authApi.resendCode(email, powHeaders);
            addToast("验证码已重新发送至 " + email, "success");
            setCountdown(60);
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "发送失败", "error");
        } finally {
            setIsResending(false);
        }
    };

    const showEmailInput = !user && !hasCachedActivationEmail;
    const isSubmitBusy = isSubmitting || isPoWCalculating;
    const isResendBusy = isResending || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-20%] left-[20%] w-[60%] h-[60%] bg-primary/5 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <Mail className="w-6 h-6 text-primary" />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">激活您的账号</h1>
                    <p className="text-muted-foreground text-sm leading-relaxed">
                        为了保障账号安全，我们需要验证您的邮箱。<br />
                        {email && (
                            <span className="mt-1 block">
                                已发送验证码至 <span className="text-foreground font-mono font-bold">{email}</span>
                            </span>
                        )}
                    </p>
                </div>

                <form onSubmit={handleActivate} className="space-y-6">
                    {showEmailInput && (
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">注册邮箱</label>
                            <input
                                type="email"
                                required
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-4 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background transition-all"
                                placeholder="name@example.com"
                            />
                        </div>
                    )}

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">验证码</label>
                        <input
                            type="text"
                            required
                            maxLength={6}
                            value={code}
                            onChange={(e) => setCode(e.target.value)}
                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-4 text-center text-3xl tracking-[0.5em] font-mono text-foreground placeholder:text-muted-foreground/30 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                            placeholder="000000"
                        />
                    </div>

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isSubmitBusy ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                            disabled={isSubmitBusy}
                        >
                            {isPoWCalculating ? "安全环境计算中..." : isSubmitting ? "验证中..." : "确认激活"}
                        </GlowButton>
                    </div>
                </form>

                <div className="mt-8 text-center space-y-6">
                    <button
                        onClick={handleResend}
                        disabled={isResendBusy || countdown > 0 || !email}
                        className="text-sm text-muted-foreground hover:text-foreground transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {countdown > 0
                            ? `${countdown} 秒后可重新发送`
                            : isResendBusy
                                ? "发送中..."
                                : "没有收到？点击重新发送"
                        }
                    </button>

                    {!user && (
                        <div className="block pt-2 border-t border-border-subtle/10">
                            <a href="/login" className="inline-flex items-center gap-2 text-xs text-muted-foreground/60 hover:text-primary transition-colors py-2">
                                <ArrowLeft className="w-3 h-3" /> 返回登录界面
                            </a>
                        </div>
                    )}
                </div>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>画外安全中心</span>
                </div>
            </motion.div>
        </div>
    );
}
