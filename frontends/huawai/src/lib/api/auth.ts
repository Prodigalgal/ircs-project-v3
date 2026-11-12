import { RegisterResponse, User } from "@/lib/types";

const API_BASE = "/api/portal/auth";
const PROFILE_BASE = "/api/portal/profile";

export interface PoWChallenge {
    id: string;
    nonce: string;
    difficulty: number;
    algorithm?: string;
    scope?: string;
    expiresAt?: string;
    captchaRequired?: boolean;
    captchaQuestion?: string | null;
}

export interface OAuthProvider {
    code: string;
    label: string;
    loginUrl: string;
}

export interface OAuthProviderSettings {
    enabled: boolean;
    providers: OAuthProvider[];
}

export type PoWScope =
    | "portal.login"
    | "portal.register"
    | "portal.activate"
    | "portal.resend-code"
    | "portal.forgot-password"
    | "portal.reset-password";

export type PoWHeaders = Record<string, string>;

export interface CheckInResult {
    earnedPoints: number;
    currentPoints: number;
    checkInStreak: number;
    experience: number;
    level: number;
}

export interface MemberTokenVO {
    token: string;
    id: string;
    email: string;
    nickname: string;
    avatarUrl: string;
    role: string;
    status: User['status'];

    experience: number;
    points: number;
    level: number;
    nextLevelXp: number;
    checkInStreak: number;
    title: string;
    lastCheckInDate: string | null;
}

function extractErrorMessage(errorData: unknown): string | null {
    if (!errorData) {
        return null;
    }
    if (typeof errorData === "string") {
        return errorData;
    }
    if (typeof errorData !== "object") {
        return null;
    }

    const data = errorData as Record<string, unknown>;
    const candidates = [
        data.message,
        data.detail,
        data.title,
        data.error,
        typeof data.error === "object" && data.error !== null
            ? (data.error as Record<string, unknown>).message
            : undefined,
    ];

    for (const candidate of candidates) {
        if (typeof candidate === "string" && candidate.trim()) {
            return candidate;
        }
    }

    if (typeof data.code === "string" && data.code.trim()) {
        return `请求失败：${data.code}`;
    }

    return null;
}

function jsonHeaders(powHeaders?: PoWHeaders): Record<string, string> {
    return {
        "Content-Type": "application/json",
        ...(powHeaders ?? {}),
    };
}

function bufferToHex(buffer: ArrayBuffer): string {
    return Array.from(new Uint8Array(buffer))
        .map((value) => value.toString(16).padStart(2, "0"))
        .join("");
}

async function sha256Hex(value: string): Promise<string> {
    const encoder = new TextEncoder();
    const hashBuffer = await crypto.subtle.digest("SHA-256", encoder.encode(value));
    return bufferToHex(hashBuffer);
}

export async function solvePoWChallenge(
    challenge: PoWChallenge,
    captchaAnswer?: string
): Promise<PoWHeaders> {
    if (!challenge.id || !challenge.nonce) {
        throw new Error("安全挑战无效，请刷新后重试");
    }

    const scope = challenge.scope ?? "portal.login";
    const targetPrefix = "0".repeat(Math.max(0, challenge.difficulty));
    const maxAttempts = 10_000_000;

    for (let counter = 0; counter < maxAttempts; counter += 1) {
        const digest = await sha256Hex(`${challenge.id}:${scope}:${challenge.nonce}:${counter}`);
        if (digest.startsWith(targetPrefix)) {
            const headers: PoWHeaders = {
                "X-IRCS-POW-Challenge": challenge.id,
                "X-IRCS-POW-Counter": counter.toString(),
                "X-IRCS-POW-Digest": digest,
            };
            if (challenge.captchaRequired) {
                headers["X-IRCS-POW-Captcha-Answer"] = captchaAnswer?.trim() ?? "";
            }
            return headers;
        }

        if (counter > 0 && counter % 1000 === 0) {
            await new Promise((resolve) => setTimeout(resolve, 0));
        }
    }

    throw new Error("安全验证计算超时，请重试");
}

async function handleResponse<T>(res: Response): Promise<T> {
    if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        const message = extractErrorMessage(errorData);

        if (message) {
            throw new Error(message);
        }

        switch (res.status) {
            case 401: throw new Error("认证失败或登录过期");
            case 403: throw new Error("您没有权限执行此操作");
            case 404: throw new Error("请求的资源不存在");
            case 429: throw new Error("请求过于频繁，请稍后再试");
            case 500: throw new Error("服务器内部错误");
            case 502: throw new Error("网关错误，服务可能暂时不可用");
            case 504: throw new Error("请求超时");
            default: throw new Error(`请求失败 (${res.status})`);
        }
    }

    if (res.status === 204) {
        return {} as T;
    }
    return res.json();
}

export const authApi = {
    async getPoWChallenge(scope: PoWScope = "portal.login"): Promise<PoWChallenge> {
        const res = await fetch(`${API_BASE}/pow?scope=${encodeURIComponent(scope)}`, { method: "GET" });
        return handleResponse<PoWChallenge>(res);
    },

    async getOAuthProviders(): Promise<OAuthProviderSettings> {
        const res = await fetch(`${API_BASE}/oauth/providers`, { method: "GET" });
        return handleResponse<OAuthProviderSettings>(res);
    },

    async login(email: string, password: string, powHeaders?: PoWHeaders): Promise<MemberTokenVO> {
        const res = await fetch(`${API_BASE}/login`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ email, password }),
        });
        return handleResponse<MemberTokenVO>(res);
    },

    async register(email: string, password: string, nickname: string, powHeaders?: PoWHeaders): Promise<RegisterResponse> {
        const res = await fetch(`${API_BASE}/register`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ email, password, nickname }),
        });
        return handleResponse<RegisterResponse>(res);
    },

    async activate(email: string, code: string, powHeaders?: PoWHeaders): Promise<MemberTokenVO> {
        const res = await fetch(`${API_BASE}/activate`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ email, code }),
        });
        return handleResponse<MemberTokenVO>(res);
    },

    async resendCode(email: string, powHeaders?: PoWHeaders): Promise<void> {
        const res = await fetch(`${API_BASE}/resend-code`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ email }),
        });
        return handleResponse<void>(res);
    },

    async forgotPassword(email: string, nickname: string, powHeaders?: PoWHeaders): Promise<void> {
        const res = await fetch(`${API_BASE}/forgot-password`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ email, nickname }),
        });
        return handleResponse<void>(res);
    },

    async resetPassword(token: string, newPassword: string, powHeaders?: PoWHeaders): Promise<void> {
        const res = await fetch(`${API_BASE}/reset-password`, {
            method: "POST",
            headers: jsonHeaders(powHeaders),
            body: JSON.stringify({ token, newPassword }),
        });
        return handleResponse<void>(res);
    },

    async getMe(token: string): Promise<User> {
        const res = await fetch(`${PROFILE_BASE}`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (res.status === 401 || res.status === 403 || res.status === 404) {
            throw new Error("Session expired");
        }

        return handleResponse<User>(res);
    },

    async updateProfile(token: string, nickname: string): Promise<User> {
        const res = await fetch(`${PROFILE_BASE}`, {
            method: "PUT",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ nickname })
        });
        return handleResponse<User>(res);
    },

    async changePassword(token: string, oldPass: string, newPass: string): Promise<void> {
        const res = await fetch(`${PROFILE_BASE}/password`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ oldPassword: oldPass, newPassword: newPass })
        });
        return handleResponse<void>(res);
    },

    async uploadAvatar(token: string, file: File): Promise<{ url: string }> {
        const formData = new FormData();
        formData.append("file", file);

        const res = await fetch(`${PROFILE_BASE}/avatar`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` },
            body: formData
        });
        return handleResponse<{ url: string }>(res);
    },

    async checkIn(token: string): Promise<CheckInResult> {
        const res = await fetch(`${PROFILE_BASE}/check-in`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
        });
        return handleResponse<CheckInResult>(res);
    }
};
