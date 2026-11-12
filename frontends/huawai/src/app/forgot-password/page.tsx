"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, ArrowLeft, CheckCircle2, User, ShieldCheck } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { usePoW } from "@/hooks/usePoW";
import { getErrorMessage } from "@/lib/utils";

export default function ForgotPasswordPage() {
    const { addToast } = useToast();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const [isLoading, setIsLoading] = useState(false);
    const [isSubmitted, setIsSubmitted] = useState(false);
    const [formData, setFormData] = useState({
        email: "",
        nickname: ""
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        try {
            setIsLoading(true);

            const powHeaders = await solve("portal.forgot-password");

            await authApi.forgotPassword(formData.email, formData.nickname, powHeaders);

            setIsSubmitted(true);
            addToast("重置邮件已发送", "success");

        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "请求失败，请检查输入信息", "error");
        } finally {
            setIsLoading(false);
        }
    };

    const isBusy = isLoading || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-0 left-1/4 w-[40%] h-[40%] bg-primary/5 blur-[120px] rounded-full" />
                <div className="absolute bottom-0 right-1/4 w-[40%] h-[40%] bg-blue-500/10 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.5 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-secondary border border-border-subtle/10 mb-6">
                        <span className="text-foreground font-bold text-2xl">?</span>
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">忘记密码?</h1>
                    <p className="text-muted-foreground">验证您的身份以获取重置链接。</p>
                </div>

                <AnimatePresence mode="wait">
                    {!isSubmitted ? (
                        <motion.form
                            key="form"
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            exit={{ opacity: 0, x: -20 }}
                            onSubmit={handleSubmit}
                            className="space-y-6"
                        >
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">注册邮箱</label>
                                <div className="relative group">
                                    <Mail className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="email"
                                        required
                                        value={formData.email}
                                        onChange={(e) => setFormData({...formData, email: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="name@example.com"
                                    />
                                </div>
                            </div>

                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">账号昵称</label>
                                <div className="relative group">
                                    <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="text"
                                        required
                                        value={formData.nickname}
                                        onChange={(e) => setFormData({...formData, nickname: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="请输入注册时的昵称"
                                    />
                                </div>
                                <p className="text-[10px] text-muted-foreground/60 ml-1">为了账号安全，我们需要验证您的昵称是否匹配。</p>
                            </div>

                            <div className="pt-2">
                                <GlowButton
                                    className="w-full py-4 text-base"
                                    icon={isBusy ? <Loader2 className="w-5 h-5 animate-spin" /> : <Mail className="w-5 h-5" />}
                                    disabled={isBusy}
                                >
                                    {isPoWCalculating ? "安全环境计算中..." : isLoading ? "验证中..." : "发送重置链接"}
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
                            <h3 className="text-xl font-bold text-foreground mb-2">邮件已发送</h3>
                            <p className="text-muted-foreground text-sm mb-6">
                                我们已向 <strong>{formData.email}</strong> 发送了重置指南。请查收您的收件箱（包括垃圾邮件文件夹）。
                            </p>
                            <button
                                onClick={() => setIsSubmitted(false)}
                                className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                            >
                                没收到？返回重试
                            </button>
                        </motion.div>
                    )}
                </AnimatePresence>

                <div className="mt-8 text-center">
                    <Link
                        href="/login"
                        className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors group"
                    >
                        <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-1" />
                        返回登录界面
                    </Link>
                </div>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>本站采用 PoW 算力验证防御技术</span>
                </div>
            </motion.div>
        </div>
    );
}
