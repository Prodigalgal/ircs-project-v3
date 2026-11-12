"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { authApi, type OAuthProviderSettings, type PoWChallenge } from "@/lib/api/auth";
import { GlowButton } from "@/components/ui/GlowButton";
import { Loader2, Mail, Lock, ArrowRight, ShieldCheck, ExternalLink } from "lucide-react";
import { useToast } from "@/lib/context/ToastContext";
import { usePoW } from "@/hooks/usePoW";
import { useAuth } from "@/lib/context/AuthContext";
import { getErrorMessage } from "@/lib/utils";

export default function LoginPage() {
    const router = useRouter();
    const { addToast } = useToast();
    const { updateUser } = useAuth();
    const { solve, isCalculating: isPoWCalculating } = usePoW();
    const [isApiLoading, setIsApiLoading] = useState(false);
    const [isChallengeLoading, setIsChallengeLoading] = useState(false);
    const [powChallenge, setPowChallenge] = useState<PoWChallenge | null>(null);
    const [oauthSettings, setOauthSettings] = useState<OAuthProviderSettings | null>(null);
    const [captchaAnswer, setCaptchaAnswer] = useState("");

    const [formData, setFormData] = useState({
        email: "",
        password: ""
    });

    const refreshChallenge = useCallback(async () => {
        setIsChallengeLoading(true);
        try {
            const challenge = await authApi.getPoWChallenge("portal.login");
            setPowChallenge(challenge);
            setCaptchaAnswer("");
        } catch (error: unknown) {
            addToast(getErrorMessage(error) || "安全挑战获取失败，请刷新后重试", "error");
        } finally {
            setIsChallengeLoading(false);
        }
    }, [addToast]);

    useEffect(() => {
        void refreshChallenge();
    }, [refreshChallenge]);

    useEffect(() => {
        let isMounted = true;

        authApi.getOAuthProviders()
            .then((settings) => {
                if (isMounted) {
                    setOauthSettings(settings);
                }
            })
            .catch(() => {
                if (isMounted) {
                    setOauthSettings({ enabled: false, providers: [] });
                }
            });

        return () => {
            isMounted = false;
        };
    }, []);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (powChallenge?.captchaRequired && !captchaAnswer.trim()) {
            addToast("请输入安全校验答案", "error");
            return;
        }

        try {
            setIsApiLoading(true);

            const powHeaders = await solve("portal.login", captchaAnswer, powChallenge ?? undefined);

            const data = await authApi.login(formData.email, formData.password, powHeaders);

            if (data.token) {
                updateUser(data);
                if (data.status === "PENDING_VERIFICATION") {
                    sessionStorage.setItem("pending_activation_email", formData.email);
                    addToast("登录成功，请激活您的账号", "info");
                    await new Promise(resolve => setTimeout(resolve, 800));
                    router.push("/activate");
                } else {
                    addToast(`欢迎回来，${data.nickname || data.email}`, "success");
                    await new Promise(resolve => setTimeout(resolve, 800));
                    router.push("/");
                }
            }
        } catch (error: unknown) {
            const errorMsg = getErrorMessage(error);

            if (errorMsg.includes("激活") || errorMsg.toLowerCase().includes("verify") || errorMsg.toLowerCase().includes("pending")) {
                addToast("账号未激活，正在跳转至激活页面...", "info");

                sessionStorage.setItem("pending_activation_email", formData.email);

                setTimeout(() => {
                    router.push("/activate");
                }, 1500);
                return;
            }

            addToast(errorMsg || "登录失败", "error");
            void refreshChallenge();
        } finally {
            setIsApiLoading(false);
        }
    };

    const isLoading = isApiLoading || isPoWCalculating || isChallengeLoading;
    const isSubmitDisabled = isLoading || !powChallenge || (powChallenge.captchaRequired && !captchaAnswer.trim());
    const oauthProviders = oauthSettings?.enabled ? oauthSettings.providers : [];

    return (
        <div className="min-h-screen w-full flex items-center justify-center bg-background relative overflow-hidden px-6">
            <div className="absolute inset-0 overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/10 blur-[120px] rounded-full animate-pulse" />
                <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-500/10 blur-[120px] rounded-full animate-pulse delay-1000" />
            </div>

            <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-10">
                    <div className="relative w-16 h-16 mx-auto mb-6 hover:scale-105 transition-transform duration-300">
                        <Image
                            src="/logo.svg"
                            alt="Huawai"
                            fill
                            className="drop-shadow-xl dark:invert transition-all duration-300"
                            priority
                        />
                    </div>
                    <h1 className="text-3xl font-bold text-foreground mb-2">欢迎回来</h1>
                    <p className="text-muted-foreground">请登录以同步您的观影进度</p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-6">
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

                    <div className="space-y-2">
                        <div className="flex items-center justify-between ml-1">
                            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">密码</label>
                            <Link href="/forgot-password" className="text-xs text-primary hover:text-primary/80 transition-colors">忘记密码?</Link>
                        </div>
                        <div className="relative group">
                            <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                            <input
                                type="password"
                                required
                                value={formData.password}
                                onChange={(e) => setFormData({...formData, password: e.target.value})}
                                className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                placeholder="••••••••"
                            />
                        </div>
                    </div>

                    {powChallenge?.captchaRequired && (
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider ml-1">安全校验</label>
                            <div className="relative group">
                                <ShieldCheck className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground group-focus-within:text-foreground transition-colors" />
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    required
                                    value={captchaAnswer}
                                    onChange={(e) => setCaptchaAnswer(e.target.value)}
                                    className="w-full bg-secondary/50 border border-border-subtle/10 rounded-xl px-4 py-3.5 pl-12 text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-primary/50 focus:bg-background focus:ring-1 focus:ring-primary/20 transition-all shadow-sm"
                                    placeholder={powChallenge.captchaQuestion ?? "请输入计算结果"}
                                />
                            </div>
                        </div>
                    )}

                    <div className="pt-2">
                        <GlowButton
                            className="w-full py-4 text-base"
                            icon={isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ArrowRight className="w-5 h-5" />}
                            disabled={isSubmitDisabled}
                        >
                            {isChallengeLoading ? "准备安全校验..." : isPoWCalculating ? "安全环境计算中..." : isApiLoading ? "正在登录..." : "立即登录"}
                        </GlowButton>
                    </div>
                </form>

                {oauthProviders.length > 0 && (
                    <div className="mt-6 space-y-4">
                        <div className="flex items-center gap-3 text-xs text-muted-foreground/60">
                            <span className="h-px flex-1 bg-border-subtle/10" />
                            <span>或使用第三方登录</span>
                            <span className="h-px flex-1 bg-border-subtle/10" />
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            {oauthProviders.map((provider) => (
                                <a
                                    key={provider.code}
                                    href={provider.loginUrl}
                                    className="min-h-11 inline-flex items-center justify-center gap-2 rounded-xl border border-border-subtle/10 bg-secondary/40 px-4 py-3 text-sm font-medium text-foreground shadow-sm transition-all hover:border-primary/30 hover:bg-background focus:outline-none focus:ring-1 focus:ring-primary/30"
                                >
                                    <span>使用 {provider.label} 登录</span>
                                    <ExternalLink className="h-4 w-4 text-muted-foreground" />
                                </a>
                            ))}
                        </div>
                    </div>
                )}

                <p className="mt-8 text-center text-sm text-muted-foreground">
                    还没有账号?{" "}
                    <Link href="/signup" className="text-foreground font-medium hover:underline underline-offset-4 decoration-primary">
                        免费注册
                    </Link>
                </p>

                <div className="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground/60">
                    <ShieldCheck className="w-3 h-3" />
                    <span>本站采用 PoW 算力验证防御技术</span>
                </div>
            </motion.div>
        </div>
    );
}
