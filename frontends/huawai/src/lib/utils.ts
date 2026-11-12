import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}

export function formatDuration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${h}小时 ${m}分`;
}

export function getErrorMessage(error: unknown): string {
    if (!error) return "发生未知错误";

    if (error instanceof Error) {
        if (error.message === "Failed to fetch" || error.message.includes("NetworkError")) {
            return "网络连接失败，请检查您的网络设置";
        }
        if (error.message === "Session expired") {
            return "登录已过期，请重新登录";
        }
        return error.message;
    }

    if (typeof error === 'string') return error;
    if (typeof error === 'object' && 'message' in error) {
        return String((error as { message: unknown }).message);
    }

    return "系统繁忙，请稍后重试";
}

export function buildForwardedHeaders(reqHeaders: Headers): Record<string, string> {
    const cfIp = reqHeaders.get("cf-connecting-ip");
    const realIp = reqHeaders.get("x-real-ip");
    const forwardedFor = reqHeaders.get("x-forwarded-for");

    const userAgent = reqHeaders.get("user-agent");
    const headers: Record<string, string> = {};

    let clientIp = "";

    if (cfIp) {
        clientIp = cfIp;
    } else if (realIp) {
        clientIp = realIp;
    } else if (forwardedFor) {
        clientIp = forwardedFor.split(',')[0].trim();
    }

    if (clientIp) {
        headers["X-Forwarded-For"] = clientIp;
        headers["X-Real-IP"] = clientIp;
        if (cfIp) headers["CF-Connecting-IP"] = cfIp;
    }

    if (userAgent) {
        headers["User-Agent"] = userAgent;
    }

    headers["X-Source-Component"] = "Huawai-SSR";

    return headers;
}