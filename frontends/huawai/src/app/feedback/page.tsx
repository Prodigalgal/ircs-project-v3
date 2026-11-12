"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Send, MessageSquare, Loader2, Info, Clock, Globe, User as UserIcon, Trash2 } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { interactionApi, UserMessage } from "@/lib/api/interaction";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { useRouter } from "next/navigation";
import { cn, getErrorMessage } from "@/lib/utils";
import { QuantumImage } from "@/components/ui/QuantumImage";
import { User } from "@/lib/types";

export default function FeedbackPage() {
    const { user } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();

    // Tab state: 'mine' | 'wall'
    const [activeTab, setActiveTab] = useState<'mine' | 'wall'>('mine');

    // Submission State
    const [content, setContent] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    // List State
    const [messages, setMessages] = useState<UserMessage[]>([]);
    const [wallMessages, setWallMessages] = useState<UserMessage[]>([]);
    const [isListLoading, setIsListLoading] = useState(false);
    const [isWallLoading, setIsWallLoading] = useState(false);

    // Fetch My Messages
    useEffect(() => {
        if (activeTab === 'mine' && user) {
            setIsListLoading(true);
            interactionApi.getMyMessages(0, 20)
                .then(res => setMessages(res.content))
                .catch(console.error)
                .finally(() => setIsListLoading(false));
        }
    }, [user, activeTab]);

    // Fetch Public Wall (No Auth Required)
    useEffect(() => {
        if (activeTab === 'wall') {
            setIsWallLoading(true);
            interactionApi.getPublicWall(0, 50)
                .then(res => setWallMessages(res.content))
                .catch(console.error)
                .finally(() => setIsWallLoading(false));
        }
    }, [activeTab]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!user) {
            addToast("请先登录", "error");
            router.push("/login");
            return;
        }
        if (!content.trim()) return;

        setIsSubmitting(true);
        try {
            const newMessage = await interactionApi.submitMessage(content);
            setMessages(prev => [newMessage, ...prev]);
            setContent("");
            addToast("反馈提交成功", "success");
            setActiveTab('mine'); // Auto switch to mine to see result
        } catch (err: unknown) {
            addToast(getErrorMessage(err), "error");
        } finally {
            setIsSubmitting(false);
        }
    };

    // [New] Delete Handler
    const handleDelete = async (id: string) => {
        if (!confirm("确定要删除这条留言吗？")) return;

        try {
            await interactionApi.deleteMessage(id);
            setMessages(prev => prev.filter(m => m.id !== id));
            addToast("删除成功", "success");
        } catch (err) {
            addToast(getErrorMessage(err), "error");
        }
    };

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-4xl mx-auto relative z-10 grid grid-cols-1 md:grid-cols-3 gap-8">
                <div className="md:col-span-1 space-y-6">
                    <div>
                        <h1 className="text-3xl font-bold text-foreground mb-2 flex items-center gap-3">
                            <MessageSquare className="w-8 h-8 text-primary" />
                            留言反馈
                        </h1>
                        <p className="text-muted-foreground text-sm">
                            遇到了问题或建议？<br/>请告诉我们，管理员会尽快回复。
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="relative">
                            <textarea
                                value={content}
                                onChange={(e) => setContent(e.target.value)}
                                placeholder={user ? "请描述您的问题或需求..." : "请先登录后留言..."}
                                className="w-full h-40 bg-secondary/50 border border-border-subtle/10 rounded-2xl p-4 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all resize-none text-sm"
                                maxLength={500}
                                disabled={!user}
                                required
                            />
                            <div className="absolute bottom-3 right-3 text-xs text-muted-foreground/60">
                                {content.length}/500
                            </div>
                        </div>
                        <GlowButton
                            className="w-full"
                            icon={isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                            disabled={isSubmitting || !content.trim() || !user}
                        >
                            {isSubmitting ? "提交中..." : (user ? "发送留言" : "登录后留言")}
                        </GlowButton>
                    </form>

                    <div className="p-4 bg-primary/10 border border-primary/20 rounded-xl">
                        <h4 className="text-primary font-bold text-sm mb-2 flex items-center gap-2">
                            <Info className="w-4 h-4" /> 温馨提示
                        </h4>
                        <ul className="text-xs text-primary/80 space-y-1 list-disc pl-4">
                            <li>每日限提交 5 条留言</li>
                            <li>留言会消耗积分，积分可通过每日签到获得</li>
                            <li>优秀建议将被展示在留言墙上</li>
                        </ul>
                    </div>
                </div>

                <div className="md:col-span-2">
                    <div className="flex items-center gap-4 mb-6 border-b border-border-subtle/10 pb-4">
                        <button
                            onClick={() => setActiveTab('mine')}
                            className={cn(
                                "flex items-center gap-2 pb-4 -mb-4 border-b-2 text-sm font-bold transition-all",
                                activeTab === 'mine'
                                    ? "text-foreground border-primary"
                                    : "text-muted-foreground border-transparent hover:text-foreground"
                            )}
                        >
                            <Clock className="w-4 h-4" />
                            我的记录
                        </button>
                        <button
                            onClick={() => setActiveTab('wall')}
                            className={cn(
                                "flex items-center gap-2 pb-4 -mb-4 border-b-2 text-sm font-bold transition-all",
                                activeTab === 'wall'
                                    ? "text-foreground border-primary"
                                    : "text-muted-foreground border-transparent hover:text-foreground"
                            )}
                        >
                            <Globe className="w-4 h-4" />
                            留言墙
                        </button>
                    </div>

                    <div className="min-h-[400px]">
                        {activeTab === 'mine' ? (
                            <MyMessagesList loading={isListLoading} messages={messages} user={user} onDelete={handleDelete} />
                        ) : (
                            <PublicWallList loading={isWallLoading} messages={wallMessages} />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

function MyMessagesList({ loading, messages, user, onDelete }: { loading: boolean, messages: UserMessage[], user: User | null, onDelete: (id: string) => void }) {
    if (!user) {
        return (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <UserIcon className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">登录后查看我的历史记录</p>
            </div>
        );
    }

    if (loading) {
        return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>;
    }

    if (messages.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 border border-dashed border-border-subtle/10 rounded-2xl bg-secondary/5 text-muted-foreground">
                <MessageSquare className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">暂无留言记录</p>
            </div>
        );
    }

    return (
        <div className="space-y-4">
            <AnimatePresence mode="popLayout">
                {messages.map((msg) => (
                    <motion.div
                        key={msg.id}
                        layout
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9 }}
                        className="bg-card border border-border-subtle/10 rounded-2xl p-5 hover:border-border-subtle/20 transition-all shadow-sm group"
                    >
                        <div className="flex justify-between items-start mb-3">
                            <div className="flex items-center gap-2">
                                <span className={cn(
                                    "w-2 h-2 rounded-full",
                                    msg.status === 'REPLIED' ? "bg-green-500 shadow-[0_0_8px_#22c55e]" :
                                        msg.status === 'CLOSED' ? "bg-muted-foreground" : "bg-amber-500 shadow-[0_0_8px_#f59e0b]"
                                )} />
                                <span className="text-xs font-bold text-muted-foreground">
                                    {new Date(msg.createdAt).toLocaleString()}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className={cn(
                                    "text-[10px] px-2 py-0.5 rounded border",
                                    msg.status === 'REPLIED' ? "bg-green-500/10 text-green-500 border-green-500/20" :
                                        msg.status === 'CLOSED' ? "bg-secondary text-muted-foreground border-border" :
                                            "bg-amber-500/10 text-amber-500 border-amber-500/20"
                                )}>
                                    {msg.status === 'REPLIED' ? '已回复' : msg.status === 'CLOSED' ? '已关闭' : '待处理'}
                                </span>
                                <button
                                    onClick={() => onDelete(msg.id)}
                                    className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
                                    title="删除留言"
                                >
                                    <Trash2 className="w-3.5 h-3.5" />
                                </button>
                            </div>
                        </div>

                        <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                            {msg.content}
                        </p>

                        {msg.reply && (
                            <div className="mt-4 pt-4 border-t border-border-subtle/10">
                                <div className="flex items-center gap-2 mb-2">
                                    <div className="w-5 h-5 rounded-full bg-primary flex items-center justify-center text-[10px] font-bold text-white">
                                        管
                                    </div>
                                    <span className="text-xs font-bold text-primary">管理员回复</span>
                                </div>
                                <p className="text-sm text-muted-foreground bg-secondary/50 p-3 rounded-lg border border-border-subtle/5">
                                    {msg.reply}
                                </p>
                            </div>
                        )}
                    </motion.div>
                ))}
            </AnimatePresence>
        </div>
    );
}

function PublicWallList({ loading, messages }: { loading: boolean, messages: UserMessage[] }) {
    if (loading) {
        return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>;
    }

    if (messages.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center py-20 border border-dashed border-border-subtle/10 rounded-2xl bg-secondary/5 text-muted-foreground">
                <Globe className="w-10 h-10 mb-3 opacity-20" />
                <p className="text-sm">暂无上墙留言</p>
            </div>
        );
    }

    return (
        <div className="columns-1 md:columns-2 gap-4 space-y-4">
            <AnimatePresence mode="popLayout">
                {messages.map((msg, idx) => (
                    <motion.div
                        key={msg.id}
                        layout
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: idx * 0.05 }}
                        className="break-inside-avoid bg-card border border-border-subtle/10 rounded-2xl p-5 mb-4 hover:border-border-subtle/20 transition-colors shadow-sm"
                    >
                        <div className="flex items-center gap-3 mb-3">
                            <div className="w-8 h-8 rounded-full bg-secondary overflow-hidden relative border border-border-subtle/10">
                                {msg.memberAvatarUrl ? (
                                    <QuantumImage src={msg.memberAvatarUrl} alt="avatar" fill className="object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center bg-primary/10 text-primary text-xs font-bold">
                                        {msg.memberNickname ? msg.memberNickname[0] : 'U'}
                                    </div>
                                )}
                            </div>
                            <div>
                                <div className="text-sm font-bold text-foreground">{msg.memberNickname || '匿名用户'}</div>
                                <div className="text-[10px] text-muted-foreground">{new Date(msg.createdAt).toLocaleDateString()}</div>
                            </div>
                        </div>

                        <p className="text-sm text-foreground/90 leading-relaxed mb-4">
                            {msg.content}
                        </p>

                        {msg.reply && (
                            <div className="bg-primary/5 p-3 rounded-xl border border-primary/10">
                                <div className="flex items-center gap-2 mb-1">
                                    <div className="w-4 h-4 rounded-full bg-primary flex items-center justify-center text-[8px] font-bold text-white">
                                        管
                                    </div>
                                    <span className="text-xs font-bold text-primary">回复</span>
                                </div>
                                <p className="text-xs text-muted-foreground">
                                    {msg.reply}
                                </p>
                            </div>
                        )}
                    </motion.div>
                ))}
            </AnimatePresence>
        </div>
    );
}
