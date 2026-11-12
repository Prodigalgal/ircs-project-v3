"use client";

import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { Camera, Mail, Award, Crown, Check as CheckIcon, X, Edit2, CalendarCheck, LogOut } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { User } from "@/lib/types";
import { cn } from "@/lib/utils";
import React, { useRef } from "react";

interface IdentityCardProps {
    user: User;
    isEditing: boolean;
    editNickname: string;
    isUploading: boolean;
    isCheckingIn: boolean;
    isCheckedInToday: boolean;
    onSetEditNickname: (val: string) => void;
    onSaveProfile: () => void;
    onStartEdit: () => void;
    onCancelEdit: () => void;
    onUploadAvatar: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onCheckIn: () => void;
    onLogout: () => void;
}

export function IdentityCard({
                                 user,
                                 isEditing,
                                 editNickname,
                                 isUploading,
                                 isCheckingIn,
                                 isCheckedInToday,
                                 onSetEditNickname,
                                 onSaveProfile,
                                 onStartEdit,
                                 onCancelEdit,
                                 onUploadAvatar,
                                 onCheckIn,
                                 onLogout
                             }: IdentityCardProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const x = useMotionValue(0);
    const y = useMotionValue(0);
    const mouseX = useSpring(x, { stiffness: 100, damping: 30 });
    const mouseY = useSpring(y, { stiffness: 100, damping: 30 });

    const rotateX = useTransform(mouseY, [-0.5, 0.5], [7, -7]);
    const rotateY = useTransform(mouseX, [-0.5, 0.5], [-7, 7]);
    const sheenX = useTransform(mouseX, [-0.5, 0.5], ["-50%", "150%"]);
    const sheenOpacity = useTransform(mouseX, [-0.5, 0, 0.5], [0, 0.1, 0]);

    const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
        const rect = e.currentTarget.getBoundingClientRect();
        x.set((e.clientX - rect.left) / rect.width - 0.5);
        y.set((e.clientY - rect.top) / rect.height - 0.5);
    };

    const handleMouseLeave = () => {
        x.set(0);
        y.set(0);
    };

    const handleAvatarClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        fileInputRef.current?.click();
    };

    const currentLevelXp = (user.level - 1) * (user.level - 1) * 100;
    const nextLevelXp = user.nextLevelXp;
    const progressPercent = Math.min(100, Math.max(0, ((user.experience - currentLevelXp) / (nextLevelXp - currentLevelXp)) * 100));
    const isAdmin = user.role === 'ADMIN' || user.role === 'ROLE_ADMIN';

    return (
        <div className="perspective-1000 mb-12">
            <motion.div
                onMouseMove={handleMouseMove}
                onMouseLeave={handleMouseLeave}
                style={{
                    rotateX,
                    rotateY,
                    transformStyle: "preserve-3d"
                }}
                className="relative bg-card/60 border border-border-subtle/20 rounded-[32px] overflow-hidden shadow-2xl backdrop-blur-xl group h-auto md:h-64"
            >
                <motion.div
                    style={{
                        x: sheenX,
                        opacity: sheenOpacity,
                        background: "linear-gradient(105deg, transparent 20%, rgba(255,255,255,0.3) 40%, transparent 60%)"
                    }}
                    className="absolute inset-0 z-20 pointer-events-none mix-blend-overlay"
                />

                <div className="absolute -top-20 -right-20 w-64 h-64 bg-primary/20 blur-[80px] rounded-full pointer-events-none" />
                <div className="absolute -bottom-20 -left-20 w-64 h-64 bg-purple-500/20 blur-[80px] rounded-full pointer-events-none" />

                <div className="absolute inset-0 opacity-20 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] pointer-events-none z-0" />

                <div className="relative z-10 grid grid-cols-1 md:grid-cols-[auto_1fr_auto] gap-8 p-8 md:p-10 h-full items-center">

                    <div className="flex justify-center md:justify-start">
                        <div className="relative group/avatar cursor-pointer" onClick={handleAvatarClick}>
                            <div className="w-32 h-32 rounded-full p-1 bg-gradient-to-br from-primary/50 to-purple-500/50 shadow-[0_0_30px_rgba(99,102,241,0.3)] relative z-10 transition-transform duration-300 group-hover/avatar:scale-105">
                                <div className="w-full h-full rounded-full overflow-hidden bg-background relative">
                                    <QuantumImage
                                        src={user.avatarUrl || "https://i.pravatar.cc/300?u=default"}
                                        alt="User Avatar"
                                        fill
                                        className="object-cover"
                                    />
                                    <div className="absolute inset-0 bg-black/60 opacity-0 group-hover/avatar:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-sm">
                                        {isUploading ? (
                                            <div className="animate-spin w-8 h-8 border-2 border-white border-t-transparent rounded-full"/>
                                        ) : (
                                            <Camera className="w-8 h-8 text-white" />
                                        )}
                                    </div>
                                    <input
                                        type="file"
                                        ref={fileInputRef}
                                        className="hidden"
                                        accept="image/jpeg,image/png,image/webp"
                                        onChange={onUploadAvatar}
                                    />
                                </div>
                            </div>

                            <div className="absolute bottom-0 right-0 z-20 translate-x-1 translate-y-1">
                                <div className="bg-background p-1 rounded-full border border-border-subtle/10">
                                    <div className="bg-gradient-to-r from-amber-500 to-orange-600 text-white text-xs font-bold px-3 py-1 rounded-full flex items-center gap-1 shadow-lg border border-white/10">
                                        <span>Lv.{user.level}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-col items-center md:items-start space-y-5 min-w-0 transform-gpu translate-z-10">
                        <div className="flex items-center gap-3 w-full justify-center md:justify-start h-10">
                            {isEditing ? (
                                <div className="flex items-center gap-2 w-full max-w-sm animate-in fade-in zoom-in duration-200">
                                    <input
                                        value={editNickname}
                                        onChange={(e) => onSetEditNickname(e.target.value)}
                                        autoFocus
                                        className="flex-1 bg-secondary/50 border border-primary/50 rounded-lg px-3 py-1 text-2xl font-bold text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 h-10"
                                    />
                                    <button onClick={onSaveProfile} className="h-10 w-10 flex items-center justify-center bg-green-500/20 text-green-500 border border-green-500/30 rounded-lg hover:bg-green-500/30 transition-colors">
                                        <CheckIcon className="w-5 h-5"/>
                                    </button>
                                    <button onClick={onCancelEdit} className="h-10 w-10 flex items-center justify-center bg-red-500/20 text-red-500 border border-red-500/30 rounded-lg hover:bg-red-500/30 transition-colors">
                                        <X className="w-5 h-5"/>
                                    </button>
                                </div>
                            ) : (
                                <div className="group/name flex items-center gap-3">
                                    <h1 className="text-4xl font-black text-foreground tracking-tighter truncate max-w-[200px] md:max-w-[300px]" title={user.nickname}>
                                        {user.nickname}
                                    </h1>
                                    <button onClick={onStartEdit} className="p-1.5 rounded-lg bg-secondary/50 text-muted-foreground opacity-0 group-hover/name:opacity-100 hover:bg-secondary hover:text-foreground transition-all transform translate-y-1">
                                        <Edit2 className="w-4 h-4" />
                                    </button>
                                </div>
                            )}
                        </div>

                        <div className="flex flex-wrap items-center justify-center md:justify-start gap-3">
                            <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-secondary/50 border border-border-subtle/10 text-xs text-muted-foreground">
                                <Mail className="w-3 h-3" />
                                <span className="font-mono pt-0.5">{user.email}</span>
                            </div>

                            <div className={cn(
                                "flex items-center gap-1.5 px-3 py-1 rounded-full border text-xs font-bold uppercase tracking-wider shadow-sm",
                                isAdmin
                                    ? "bg-red-500/10 border-red-500/30 text-red-500"
                                    : "bg-primary/10 border-primary/30 text-primary"
                            )}>
                                {isAdmin ? <Award className="w-3 h-3" /> : <Crown className="w-3 h-3" />}
                                <span className="pt-0.5">{isAdmin ? '管理员' : (user.title || '萌新影迷')}</span>
                            </div>
                        </div>

                        <div className="w-full max-w-sm bg-secondary/50 rounded-full h-5 border border-border-subtle/10 relative overflow-hidden group/xp">
                            <div className="absolute inset-0 flex items-center justify-between px-3 text-[9px] font-bold font-mono text-foreground/60 z-20 pointer-events-none mix-blend-overlay">
                                <span>Lv.{user.level}</span>
                                <span>{progressPercent.toFixed(1)}%</span>
                                <span>Lv.{user.level + 1}</span>
                            </div>
                            <motion.div
                                initial={{ width: 0 }}
                                animate={{ width: `${progressPercent}%` }}
                                transition={{ duration: 1.5, ease: "circOut" }}
                                className="h-full bg-gradient-to-r from-indigo-600 via-purple-500 to-indigo-400 relative"
                            >
                                <div className="absolute inset-0 bg-[url('/noise.svg')] opacity-30" />
                                <div className="absolute top-0 right-0 bottom-0 w-px bg-white/50 shadow-[0_0_10px_white]" />
                            </motion.div>
                        </div>
                    </div>

                    <div className="flex flex-col gap-3 justify-center min-w-[140px]">
                        <GlowButton
                            variant={isCheckedInToday ? "ghost" : "primary"}
                            onClick={onCheckIn}
                            className={cn(
                                "w-full justify-center shadow-lg",
                                isCheckedInToday ? "opacity-60 cursor-not-allowed bg-secondary/30 hover:bg-secondary/30 shadow-none border border-border-subtle/10" : ""
                            )}
                            icon={isCheckingIn ? <div className="animate-spin w-4 h-4 border-2 border-current border-t-transparent rounded-full"/> : <CalendarCheck className="w-4 h-4" />}
                        >
                            {isCheckedInToday ? "今日已签" : "每日签到"}
                        </GlowButton>

                        <button
                            onClick={onLogout}
                            className="flex items-center justify-center gap-2 w-full py-3 rounded-full text-xs font-bold text-muted-foreground hover:text-foreground hover:bg-secondary/50 transition-all group/logout"
                        >
                            <LogOut className="w-4 h-4 group-hover/logout:-translate-x-0.5 transition-transform" />
                            安全登出
                        </button>
                    </div>
                </div>
            </motion.div>
        </div>
    );
}