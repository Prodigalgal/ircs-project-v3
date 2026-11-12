"use client";

import { useToast } from "@/lib/context/ToastContext";
import { CheckCircle2, AlertCircle, Info, Loader2, X } from "lucide-react";
import { cn } from "@/lib/utils";

export function Toaster() {
    const { toasts, removeToast } = useToast();

    return (
        <div className="fixed top-24 right-6 z-[100] flex flex-col gap-3 pointer-events-none">
            {toasts.map((toast) => (
                <div
                    key={toast.id}
                    className={cn(
                        "pointer-events-auto w-80 p-4 rounded-xl border backdrop-blur-xl shadow-2xl flex items-start gap-3 relative overflow-hidden",
                        "animate-in fade-in slide-in-from-right-4 zoom-in-95 duration-200",
                        toast.type === "success" && "bg-green-500/10 border-green-500/20 text-green-700 dark:text-green-200",
                        toast.type === "error" && "bg-red-500/10 border-red-500/20 text-red-700 dark:text-red-200",
                        toast.type === "info" && "bg-indigo-500/10 border-indigo-500/20 text-indigo-700 dark:text-indigo-200",
                        toast.type === "loading" && "bg-card/90 border-border-subtle/10 text-foreground"
                    )}
                >
                    <div className="flex-shrink-0 mt-0.5">
                        {toast.type === "success" && <CheckCircle2 className="w-5 h-5 text-green-500" />}
                        {toast.type === "error" && <AlertCircle className="w-5 h-5 text-red-500" />}
                        {toast.type === "info" && <Info className="w-5 h-5 text-indigo-500" />}
                        {toast.type === "loading" && <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />}
                    </div>

                    <div className="flex-1 mr-2">
                        <p className="text-sm font-medium leading-tight">{toast.message}</p>
                    </div>

                    <button
                        onClick={() => removeToast(toast.id)}
                        className="text-foreground/40 hover:text-foreground transition-colors absolute top-2 right-2"
                    >
                        <X className="w-4 h-4" />
                    </button>

                    {toast.type !== "loading" && toast.duration && toast.duration > 0 && (
                        <div
                            className={cn(
                                "absolute bottom-0 left-0 h-0.5 toast-progress",
                                toast.type === "success" && "bg-green-500",
                                toast.type === "error" && "bg-red-500",
                                toast.type === "info" && "bg-indigo-500"
                            )}
                            style={{ animationDuration: `${toast.duration}ms` }}
                        />
                    )}
                </div>
            ))}
        </div>
    );
}
