"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/context/AuthContext";
import { Lock, Clock, Flame, Coins, Heart, Ghost, EyeOff, Shield } from "lucide-react";
import { authApi } from "@/lib/api/auth";
import { useToast } from "@/lib/context/ToastContext";
import { interactionApi } from "@/lib/api/interaction";
import { cn, getErrorMessage } from "@/lib/utils";
import dynamic from 'next/dynamic';

import { IdentityCard } from "@/components/features/profile/IdentityCard";
import { StatCard } from "@/components/features/profile/StatCard";
import { getTotalElements } from "@/lib/pagination";

const PasswordModal = dynamic(
    () => import('@/components/features/profile/PasswordModal').then(mod => mod.PasswordModal),
    { ssr: false }
);

export default function ProfilePage() {
    const { user, logout, isLoading, updateUser, isStealthMode, toggleStealthMode } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();

    const [isEditing, setIsEditing] = useState(false);
    const [editNickname, setEditNickname] = useState("");
    const [isUploading, setIsUploading] = useState(false);
    const [showPwModal, setShowPwModal] = useState(false);
    const [isCheckingIn, setIsCheckingIn] = useState(false);
    const [favCount, setFavCount] = useState(0);

    useEffect(() => {
        if (!isLoading && !user) {
            router.push("/login");
        }
        if (user) {
            setEditNickname(user.nickname);
            interactionApi.getFavorites(0, 1)
                .then(res => setFavCount(getTotalElements(res)))
                .catch(() => {});
        }
    }, [user, isLoading, router]);

    if (!user) return null;

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file || !user.token) return;
        if (file.size > 2 * 1024 * 1024) {
            addToast("图片大小不能超过 2MB", "error");
            return;
        }
        setIsUploading(true);
        try {
            const { url } = await authApi.uploadAvatar(user.token, file);
            updateUser({ ...user, avatarUrl: url });
            addToast("头像更新成功", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err) || "上传失败", "error");
        } finally {
            setIsUploading(false);
        }
    };

    const handleSaveProfile = async () => {
        if (!user.token) return;
        try {
            const updatedUser = await authApi.updateProfile(user.token, editNickname);
            updateUser({ ...user, ...updatedUser });
            setIsEditing(false);
            addToast("资料已更新", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        }
    };

    const handleChangePassword = async (old: string, newP: string, confirm: string) => {
        if (!user.token) return;
        if (newP !== confirm) {
            addToast("两次新密码输入不一致", "error");
            return;
        }
        try {
            await authApi.changePassword(user.token, old, newP);
            setShowPwModal(false);
            addToast("密码修改成功", "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        }
    };

    const handleCheckIn = async () => {
        if (!user.token || isCheckingIn) return;
        setIsCheckingIn(true);
        try {
            const result = await authApi.checkIn(user.token);
            updateUser({
                ...user,
                points: result.currentPoints,
                experience: result.experience,
                level: result.level,
                checkInStreak: result.checkInStreak,
                lastCheckInDate: new Date().toISOString().split('T')[0]
            });
            addToast(`签到成功！积分 +${result.earnedPoints}`, "success");
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        } finally {
            setIsCheckingIn(false);
        }
    };

    const isCheckedInToday = user.lastCheckInDate === new Date().toISOString().split('T')[0];

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative overflow-hidden">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-5xl mx-auto relative z-10">
                <IdentityCard
                    user={user}
                    isEditing={isEditing}
                    editNickname={editNickname}
                    isUploading={isUploading}
                    isCheckingIn={isCheckingIn}
                    isCheckedInToday={isCheckedInToday}
                    onSetEditNickname={setEditNickname}
                    onSaveProfile={handleSaveProfile}
                    onStartEdit={() => setIsEditing(true)}
                    onCancelEdit={() => setIsEditing(false)}
                    onUploadAvatar={handleFileChange}
                    onCheckIn={handleCheckIn}
                    onLogout={logout}
                />

                {/* 2. Stats Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-12">
                    <StatCard
                        label="我的积分"
                        value={user.points}
                        icon={<Coins className="w-5 h-5" />}
                        color="from-yellow-500/20 to-orange-500/20"
                        borderColor="group-hover:border-yellow-500/30"
                    />
                    <StatCard
                        label="连续签到"
                        value={`${user.checkInStreak} 天`}
                        icon={<Flame className="w-5 h-5" />}
                        color="from-red-500/20 to-rose-500/20"
                        borderColor="group-hover:border-red-500/30"
                    />
                    <StatCard
                        label="我的收藏"
                        value={favCount}
                        icon={<Heart className="w-5 h-5" />}
                        color="from-pink-500/20 to-rose-500/20"
                        borderColor="group-hover:border-pink-500/30"
                    />
                    <StatCard
                        label="累计观影"
                        value="0h"
                        icon={<Clock className="w-5 h-5" />}
                        color="from-blue-500/20 to-cyan-500/20"
                        borderColor="group-hover:border-blue-500/30"
                    />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                    <section className="bg-card/40 border border-border-subtle/10 rounded-3xl p-8 backdrop-blur-sm h-full shadow-lg">
                        <div className="flex items-center gap-3 mb-8">
                            <div className="p-2 bg-secondary rounded-lg text-foreground border border-border-subtle/10">
                                <Shield className="w-5 h-5" />
                            </div>
                            <h2 className="text-xl font-bold text-foreground">账户安全</h2>
                        </div>

                        <div className="flex items-center justify-between p-6 bg-secondary/20 border border-border-subtle/10 rounded-2xl hover:bg-secondary/40 transition-all group">
                            <div className="flex items-center gap-4">
                                <div className="p-3 bg-muted rounded-full text-muted-foreground group-hover:text-foreground transition-colors">
                                    <Lock className="w-5 h-5" />
                                </div>
                                <div>
                                    <h4 className="text-foreground font-medium">登录密码</h4>
                                    <p className="text-xs text-muted-foreground mt-0.5">定期修改密码可以保护账号安全</p>
                                </div>
                            </div>
                            <button
                                onClick={() => setShowPwModal(true)}
                                className="px-6 py-2.5 text-sm font-medium text-muted-foreground hover:text-foreground border border-border-subtle/10 hover:border-border-subtle/30 rounded-xl bg-card hover:bg-secondary/80 transition-all shadow-sm"
                            >
                                修改密码
                            </button>
                        </div>
                    </section>

                    <section className="bg-card/40 border border-border-subtle/10 rounded-3xl p-8 backdrop-blur-sm h-full shadow-lg">
                        <div className="flex items-center gap-3 mb-8">
                            <div className="p-2 bg-secondary rounded-lg text-foreground border border-border-subtle/10">
                                <Ghost className="w-5 h-5" />
                            </div>
                            <h2 className="text-xl font-bold text-foreground">隐私设置</h2>
                        </div>

                        <div className={cn(
                            "flex items-center justify-between p-6 border rounded-2xl transition-all group",
                            isStealthMode
                                ? "bg-primary/10 border-primary/30"
                                : "bg-secondary/20 border-border-subtle/10 hover:bg-secondary/40"
                        )}>
                            <div className="flex items-center gap-4">
                                <div className={cn(
                                    "p-3 rounded-full transition-colors",
                                    isStealthMode ? "bg-primary/20 text-primary" : "bg-muted text-muted-foreground"
                                )}>
                                    <EyeOff className="w-5 h-5" />
                                </div>
                                <div>
                                    <h4 className={cn("font-medium", isStealthMode ? "text-primary" : "text-foreground")}>
                                        隐身模式
                                    </h4>
                                    <p className="text-xs text-muted-foreground mt-0.5">
                                        开启后将暂停记录您的观影历史进度
                                    </p>
                                </div>
                            </div>
                            <button
                                onClick={toggleStealthMode}
                                className={cn(
                                    "relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background",
                                    isStealthMode ? "bg-primary" : "bg-muted-foreground/30"
                                )}
                            >
                                <span
                                    className={cn(
                                        "inline-block h-4 w-4 transform rounded-full bg-white transition-transform",
                                        isStealthMode ? "translate-x-6" : "translate-x-1"
                                    )}
                                />
                            </button>
                        </div>
                    </section>
                </div>
            </div>

            <PasswordModal
                isOpen={showPwModal}
                onClose={() => setShowPwModal(false)}
                onSubmit={handleChangePassword}
            />
        </div>
    );
}
