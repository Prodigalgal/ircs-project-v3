"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { authApi } from "@/lib/api/auth";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, Lock, User, ArrowRight, CheckCircle2, ShieldCheck } from "lucide-react";
import { useToast } from "@/lib/context/ToastContext";
import { useRouter } from "next/navigation";
import { usePoW } from "@/hooks/usePoW";
import { getErrorMessage } from "@/lib/utils";

export default function SignupPage() {
    const { addToast } = useToast();
    const router = useRouter();
    const { solve, isCalculating: isPoWCalculating } = usePoW();

    const [step, setStep] = useState(1); // 1: Info, 2: Activate
    const [isApiLoading, setIsApiLoading] = useState(false);

    const [formData, setFormData] = useState({
        name: "",
        email: "",
        password: "",
        confirmPassword: "",
        activationCode: ""
    });

    const handleSignup = async (e: React.FormEvent) => {
        e.preventDefault();
        if (formData.password !== formData.confirmPassword) {
            addToast("两次输入的密码不一致", "error");
            return;
        }

        try {
            setIsApiLoading(true);

            const powHeaders = await solve("portal.register");

            const response = await authApi.register(formData.email, formData.password, formData.name, powHeaders);

            if (response.verifyRequired) {
                setStep(2);
                addToast("验证码已发送至您的邮箱", "success");
            } else {
                addToast("注册成功，请直接登录", "success");
                setTimeout(() => {
                    router.push("/login");
                }, 1500);
            }
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "注册失败", "error");
        } finally {
            setIsApiLoading(false);
        }
    };

    const handleActivate = async () => {
        setIsApiLoading(true);
        try {
            const powHeaders = await solve("portal.activate");
            const data = await authApi.activate(formData.email, formData.activationCode, powHeaders);
            if (data.token) {
                localStorage.setItem("huawai_token", data.token);
            }
            addToast("账号激活成功！", "success");
            router.push("/");
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "激活失败", "error");
        } finally {
            setIsApiLoading(false);
        }
    };

    const isLoading = isApiLoading || isPoWCalculating;

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-20%] right-[20%] w-[60%] h-[60%] bg-primary/5 blur-[120px] rounded-full" />
            </div>

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <h1 className="text-3xl font-bold text-foreground mb-2">
                        {step === 1 ? "创建账号" : "激活账号"}
                    </h1>
                    <p className="text-muted-foreground">
                        {step === 1 ? "开启您的 4K 沉浸式体验之旅" : "请输入邮箱中的6位数字验证码"}
                    </p>
                </div>

                <AnimatePresence mode="wait">
                    {step === 1 ? (
                        <motion.form
                            key="step1"
                            initial={{ x: -20, opacity: 0 }}
                            animate={{ x: 0, opacity: 1 }}
                            exit={{ x: -20, opacity: 0 }}
                            onSubmit={handleSignup}
                            className="space-y-5"
                        >
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">昵称</label>
                                <div className="relative group">
                                    <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                    <input
                                        type="text"
                                        required
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                        placeholder="怎么称呼您?"
                                    />
                                </div>
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">电子邮箱</label>
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
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">密码</label>
                                    <div className="relative group">
                                        <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                        <input
                                            type="password"
                                            required
                                            value={formData.password}
                                            onChange={(e) => setFormData({...formData, password: e.target.value})}
                                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                            placeholder="••••••"
                                        />
                                    </div>
                                </div>
                                <div className="space-y-2">
                                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">确认密码</label>
                                    <div className="relative group">
                                        <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                        <input
                                            type="password"
                                            required
                                            value={formData.confirmPassword}
                                            onChange={(e) => setFormData({...formData, confirmPassword: e.target.value})}
                                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                            placeholder="••••••"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="pt-4">
                                <GlowButton
                                    className="w-full py-4 text-base"
                                    icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ArrowRight className="w-5 h-5" />}
                                >
                                    {isPoWCalculating ? "安全环境计算中..." : isApiLoading ? "正在提交..." : "立即注册"}
                                </GlowButton>
                            </div>
                        </motion.form>
                    ) : (
                        <motion.div
                            key="step2"
                            initial={{ x: 20, opacity: 0 }}
                            animate={{ x: 0, opacity: 1 }}
                            className="space-y-6"
                        >
                            <div className="p-4 bg-secondary/30 border border-border-subtle/10 rounded-xl text-center">
                                <p className="text-sm text-muted-foreground">我们已向 <span className="text-foreground font-bold">{formData.email}</span> 发送了邮件。</p>
                            </div>

                            <div className="space-y-2">
                                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">验证码</label>
                                <input
                                    type="text"
                                    required
                                    maxLength={6}
                                    value={formData.activationCode}
                                    onChange={(e) => setFormData({...formData, activationCode: e.target.value})}
                                    className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 text-center text-2xl tracking-[0.5em] text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 transition-all"
                                    placeholder="000000"
                                />
                            </div>

                            <GlowButton
                                onClick={handleActivate}
                                className="w-full py-4 text-base"
                                icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <CheckCircle2 className="w-5 h-5" />}
                                disabled={isLoading}
                            >
                                {isPoWCalculating ? "安全环境计算中..." : isApiLoading ? "激活中..." : "确认激活"}
                            </GlowButton>
                        </motion.div>
                    )}
                </AnimatePresence>

                {step === 1 && (
                    <>
                        <p className="mt-8 text-center text-sm text-muted-foreground">
                            已有账号?{" "}
                            <Link href="/login" className="text-foreground font-medium hover:underline underline-offset-4 decoration-primary">
                                直接登录
                            </Link>
                        </p>
                        <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                            <ShieldCheck className="w-3 h-3" />
                            <span>本站采用 PoW 算力验证防御技术</span>
                        </div>
                    </>
                )}
            </motion.div>
        </div>
    );
}
