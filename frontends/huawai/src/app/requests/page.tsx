"use client";

import { useEffect, useMemo, useState } from "react";
import { Calendar, Clapperboard, Clock, Loader2, Send, Sparkles } from "lucide-react";
import { GlowButton } from "@/components/ui/GlowButton";
import { interactionApi, MediaRequest } from "@/lib/api/interaction";
import { useAuth } from "@/lib/context/AuthContext";
import { useToast } from "@/lib/context/ToastContext";
import { cn, getErrorMessage } from "@/lib/utils";
import { useRouter } from "next/navigation";

const STATUS_LABELS: Record<MediaRequest["status"], string> = {
    PENDING: "等待收集",
    SCHEDULING: "正在归集",
    BATCHED: "等待执行",
    SCHEDULED: "已进入采集",
    SKIPPED_EXISTS: "站内已存在",
    CANCELLED: "已取消",
    COMPLETED: "已完成",
    FAILED: "处理失败"
};

export default function MediaRequestPage() {
    const { user } = useAuth();
    const router = useRouter();
    const { addToast } = useToast();
    const [title, setTitle] = useState("");
    const [releaseYear, setReleaseYear] = useState("");
    const [extraInfo, setExtraInfo] = useState("");
    const [requests, setRequests] = useState<MediaRequest[]>([]);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const currentYear = useMemo(() => new Date().getFullYear() + 2, []);

    useEffect(() => {
        if (!user) return;
        setLoading(true);
        interactionApi.getMyMediaRequests(0, 30)
            .then(res => setRequests(res.content))
            .catch(() => addToast("求片记录加载失败", "error"))
            .finally(() => setLoading(false));
    }, [user, addToast]);

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!user) {
            addToast("请先登录", "error");
            router.push("/login");
            return;
        }
        if (!title.trim()) return;
        const year = releaseYear.trim() ? Number(releaseYear.trim()) : undefined;
        if (year && (year < 1888 || year > currentYear)) {
            addToast("年份范围无效", "error");
            return;
        }
        setSubmitting(true);
        try {
            const created = await interactionApi.submitMediaRequest(title, year, extraInfo);
            setRequests(prev => [created, ...prev.filter(item => item.id !== created.id)]);
            setTitle("");
            setReleaseYear("");
            setExtraInfo("");
            addToast(`求片已提交，消耗 ${created.spentPoints} 积分`, "success");
        } catch (error) {
            addToast(getErrorMessage(error), "error");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="min-h-screen bg-background pt-32 pb-20 px-6 relative">
            <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

            <div className="max-w-5xl mx-auto relative z-10 grid grid-cols-1 lg:grid-cols-[360px_1fr] gap-8">
                <section className="space-y-6">
                    <div>
                        <h1 className="text-3xl font-bold text-foreground mb-2 flex items-center gap-3">
                            <Clapperboard className="w-8 h-8 text-primary" />
                            求片
                        </h1>
                        <p className="text-muted-foreground text-sm">
                            提交片名和年份，系统会批量去重后自动向资源站发起采集。
                        </p>
                    </div>

                    <form onSubmit={handleSubmit} className="space-y-4">
                        <input
                            value={title}
                            onChange={(event) => setTitle(event.target.value)}
                            placeholder={user ? "影片标题，例如 黑客帝国" : "请先登录后求片"}
                            maxLength={120}
                            disabled={!user}
                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-2xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all text-sm"
                            required
                        />
                        <input
                            value={releaseYear}
                            onChange={(event) => setReleaseYear(event.target.value.replace(/\D/g, "").slice(0, 4))}
                            placeholder="年份，可选"
                            inputMode="numeric"
                            disabled={!user}
                            className="w-full bg-secondary/50 border border-border-subtle/10 rounded-2xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all text-sm"
                        />
                        <div className="relative">
                            <textarea
                                value={extraInfo}
                                onChange={(event) => setExtraInfo(event.target.value)}
                                placeholder="补充信息，可填写地区、主演、别名或资源线索"
                                maxLength={500}
                                disabled={!user}
                                className="w-full h-32 bg-secondary/50 border border-border-subtle/10 rounded-2xl p-4 text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 focus:bg-background transition-all resize-none text-sm"
                            />
                            <div className="absolute bottom-3 right-3 text-xs text-muted-foreground/60">
                                {extraInfo.length}/500
                            </div>
                        </div>
                        <GlowButton
                            className="w-full"
                            icon={submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                            disabled={submitting || !title.trim() || !user}
                        >
                            {submitting ? "提交中..." : (user ? "提交求片" : "登录后求片")}
                        </GlowButton>
                    </form>

                    <div className="p-4 bg-primary/10 border border-primary/20 rounded-xl">
                        <h4 className="text-primary font-bold text-sm mb-2 flex items-center gap-2">
                            <Sparkles className="w-4 h-4" /> 规则
                        </h4>
                        <ul className="text-xs text-primary/80 space-y-1 list-disc pl-4">
                            <li>求片会消耗积分，积分可通过每日签到获得</li>
                            <li>相同片名和年份会自动合并请求次数</li>
                            <li>采集结果进入现有视频入库流程</li>
                        </ul>
                    </div>
                </section>

                <section>
                    <div className="flex items-center justify-between mb-6 border-b border-border-subtle/10 pb-4">
                        <div className="flex items-center gap-2 text-sm font-bold text-foreground">
                            <Clock className="w-4 h-4" />
                            我的求片
                        </div>
                        {user && (
                            <button
                                onClick={() => {
                                    setLoading(true);
                                    interactionApi.getMyMediaRequests(0, 30)
                                        .then(res => setRequests(res.content))
                                        .finally(() => setLoading(false));
                                }}
                                className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                            >
                                刷新
                            </button>
                        )}
                    </div>
                    <RequestList loading={loading} requests={requests} userVisible={Boolean(user)} />
                </section>
            </div>
        </div>
    );
}

function RequestList({ loading, requests, userVisible }: { loading: boolean; requests: MediaRequest[]; userVisible: boolean }) {
    if (!userVisible) {
        return <EmptyState text="登录后查看求片记录" />;
    }
    if (loading) {
        return <div className="flex justify-center py-12"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>;
    }
    if (requests.length === 0) {
        return <EmptyState text="暂无求片记录" />;
    }
    return (
        <div className="space-y-4">
            {requests.map(request => (
                <article
                    key={request.id}
                    className="bg-card border border-border-subtle/10 rounded-2xl p-5 hover:border-border-subtle/20 transition-all shadow-sm"
                >
                    <div className="flex items-start justify-between gap-4">
                        <div>
                            <h2 className="text-base font-bold text-foreground flex items-center gap-2">
                                {request.title}
                                {request.releaseYear && <span className="text-xs text-muted-foreground font-mono">{request.releaseYear}</span>}
                            </h2>
                            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                                <span className="inline-flex items-center gap-1">
                                    <Calendar className="w-3 h-3" />
                                    {new Date(request.lastRequestedAt).toLocaleString()}
                                </span>
                                <span>合并请求 {request.requestCount} 次</span>
                                {request.spentPoints > 0 && <span>本次消耗 {request.spentPoints} 积分</span>}
                            </div>
                        </div>
                        <span className={cn(
                            "shrink-0 rounded-full px-3 py-1 text-xs font-bold border",
                            request.status === "FAILED"
                                ? "text-red-400 border-red-400/20 bg-red-400/10"
                                : request.status === "SCHEDULED" || request.status === "COMPLETED" || request.status === "SKIPPED_EXISTS"
                                    ? "text-green-400 border-green-400/20 bg-green-400/10"
                                    : "text-amber-400 border-amber-400/20 bg-amber-400/10"
                        )}>
                            {STATUS_LABELS[request.status] || request.status}
                        </span>
                    </div>
                    {request.extraInfo && (
                        <p className="mt-3 text-sm text-muted-foreground leading-relaxed">{request.extraInfo}</p>
                    )}
                    {request.lastErrorMessage && (
                        <p className="mt-3 text-xs text-red-400">{request.lastErrorMessage}</p>
                    )}
                </article>
            ))}
        </div>
    );
}

function EmptyState({ text }: { text: string }) {
    return (
        <div className="flex flex-col items-center justify-center py-20 border border-dashed border-border-subtle/10 rounded-2xl bg-secondary/5 text-muted-foreground">
            <Clapperboard className="w-10 h-10 mb-3 opacity-20" />
            <p className="text-sm">{text}</p>
        </div>
    );
}
