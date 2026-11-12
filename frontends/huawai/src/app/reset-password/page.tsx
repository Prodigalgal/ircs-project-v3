"use client";

import { useState, Suspense, useEffect } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Lock, ArrowLeft, CheckCircle2, AlertCircle } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { getErrorMessage } from "@/lib/utils";
import { usePoW } from "@/hooks/usePoW";

function ResetPasswordContent() {
    const searchParams = useSearchParams();
    const router = useRouter();
    const { addToast } = useToast();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const token = searchParams.get("token");

    const [isLoading, setIsLoading] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);
    const [formData, setFormData] = useState({
        newPassword: "",
        confirmPassword: ""
    });

    // 如果没有 Token，直接显示错误提示
    useEffect(() => {
        if (!token) {
            addToast("无效的重置链接：缺少令牌", "error");
        }
    }, [token, addToast]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!token) {
            addToast("重置令牌丢失，请重新申请", "error");
            return;
        }

        if (formData.newPassword.length < 6) {
            addToast("密码长度至少为 6 位", "error");
            return;
        }

        if (formData.newPassword !== formData.confirmPassword) {
            addToast("两次输入的密码不一致", "error");
            return;
        }

        try {
            setIsLoading(true);
            const powHeaders = await solve("portal.reset-password");
            await authApi.resetPassword(token, formData.newPassword, powHeaders);
            setIsSuccess(true);
            addToast("密码重置成功", "success");

            // 3秒后自动跳转
            setTimeout(() => {
                router.push("/login");
            }, 3000);

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "重置失败，链接可能已过期", "error");
        } finally {
            setIsLoading(false);
        }
    };

    const isBusy = isLoading || isPoWCalculating;

    if (!token) {
        return (
            <div className="text-center">
                <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-4 border border-red-500/20">
                    <AlertCircle className="w-8 h-8 text-red-500" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-2">链接无效</h3>
                <p className="text-muted-foreground mb-6">
                    重置链接无效或已过期。请重新发起申请。
                </p>
                <Link href="/forgot-password">
                    <GlowButton>重新申请</GlowButton>
                </Link>
            </div>
        );
    }

    return (
        <AnimatePresence mode="wait">
            {!isSuccess ? (
                <motion.form
                    key="reset-form"
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: -20 }}
                    onSubmit={handleSubmit}
                    className="space-y-6"
                >
                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">新密码</label>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.newPassword}
                                onChange={(e) => setFormData({ ...formData, newPassword: e.target.value })}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="设置新密码 (至少6位)"
                            />
                        </div>
                    </div>

                    <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">确认新密码</label>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.confirmPassword}
                                onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="再次输入以确认"
                            />
                        </div>
                    </div>

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isBusy ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                            disabled={isBusy}
                        >
                            {isPoWCalculating ? "安全环境计算中..." : isLoading ? "重置中..." : "确认修改"}
                        </GlowButton>
                    </div>
                </motion.form>
            ) : (
                <motion.div
                    key="success"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="bg-green-500/10 border border-green-500/20 rounded-2xl p-8 text-center"
                >
                    <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4">
                        <CheckCircle2 className="w-8 h-8 text-green-500" />
                    </div>
                    <h3 className="text-xl font-bold text-foreground mb-2">密码重置成功</h3>
                    <p className="text-muted-foreground text-sm mb-6">
                        您的密码已更新。即将为您跳转至登录页面...
                    </p>
                    <Link href="/login">
                        <button className="text-sm font-bold text-primary hover:underline underline-offset-4">
                            立即登录
                        </button>
                    </Link>
                </motion.div>
            )}
        </AnimatePresence>
    );
}

export default function ResetPasswordPage() {
    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 blur-[120px] rounded-full" />
                <div className="absolute bottom-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/10 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.5 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <Lock className="w-6 h-6 text-primary" />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">重置密码</h1>
                    <p className="text-muted-foreground">请设置您的新安全密码</p>
                </div>

                <Suspense fallback={
                    <div className="flex justify-center py-10">
                        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
                    </div>
                }>
                    <ResetPasswordContent />
                </Suspense>

                <div className="mt-8 text-center">
                    <Link
                        href="/login"
                        className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors group"
                    >
                        <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-1" />
                        返回登录界面
                    </Link>
                </div>
            </motion.div>
        </div>
    );
}
