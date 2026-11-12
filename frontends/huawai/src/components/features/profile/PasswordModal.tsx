"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Lock, Save } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { useState } from "react";

interface PasswordModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (oldPass: string, newPass: string, confirmPass: string) => Promise<void>;
}

export function PasswordModal({ isOpen, onClose, onSubmit }: PasswordModalProps) {
    const [data, setData] = useState({ old: "", new: "", confirm: "" });

    const handleSubmit = () => {
        onSubmit(data.old, data.new, data.confirm);
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
                    <motion.div
                        initial={{ opacity: 0, scale: 0.95, y: 20 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.95, y: 20 }}
                        className="w-full max-w-md bg-[#1a1a1c] border border-white/10 rounded-3xl p-8 shadow-2xl relative overflow-hidden"
                    >
                        {/* 顶部装饰条 */}
                        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-indigo-500" />

                        <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
                            <Lock className="w-5 h-5 text-indigo-500"/>
                            修改密码
                        </h3>

                        <div className="space-y-4">
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">当前密码</label>
                                <input
                                    type="password"
                                    value={data.old}
                                    onChange={e => setData({...data, old: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                />
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">新密码</label>
                                <input
                                    type="password"
                                    value={data.new}
                                    onChange={e => setData({...data, new: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                    placeholder="至少 6 位字符"
                                />
                            </div>
                            <div className="space-y-2">
                                <label className="text-xs text-neutral-500 font-bold uppercase tracking-wider ml-1">确认新密码</label>
                                <input
                                    type="password"
                                    value={data.confirm}
                                    onChange={e => setData({...data, confirm: e.target.value})}
                                    className="w-full bg-black/20 border border-white/10 rounded-xl px-4 py-3 text-white focus:border-indigo-500 focus:bg-white/5 outline-none transition-all"
                                />
                            </div>
                        </div>

                        <div className="flex gap-3 mt-8">
                            <button
                                onClick={onClose}
                                className="flex-1 py-3 text-neutral-400 hover:text-white transition-colors text-sm font-medium hover:bg-white/5 rounded-xl"
                            >
                                取消
                            </button>
                            <GlowButton
                                className="flex-1"
                                onClick={handleSubmit}
                                icon={<Save className="w-4 h-4"/>}
                            >
                                确认修改
                            </GlowButton>
                        </div>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
}