"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useToast } from "@/lib/context/ToastContext";
import { authApi, type PoWHeaders } from "@/lib/api/auth";
import { User } from '@/lib/types';

const USER_CACHE_KEY = "huawai_user_cache_v1";
const USER_CACHE_TTL_MS = 5 * 60 * 1000;

interface CachedUser {
    user: User;
    cachedAt: number;
}

interface AuthContextType {
    user: User | null;
    isLoading: boolean;
    isStealthMode: boolean;
    login: (email: string, password: string, powHeaders?: PoWHeaders) => Promise<void>;
    signup: (name: string, email: string, password: string, powHeaders?: PoWHeaders) => Promise<void>;
    updateUser: (user: User) => void;
    logout: () => void;
    toggleStealthMode: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function readCachedUser(token: string): User | null {
    const value = localStorage.getItem(USER_CACHE_KEY);
    if (!value) {
        return null;
    }
    try {
        const cached = JSON.parse(value) as CachedUser;
        if (Date.now() - cached.cachedAt > USER_CACHE_TTL_MS) {
            return null;
        }
        if (cached.user.token !== token) {
            return null;
        }
        return cached.user;
    } catch {
        localStorage.removeItem(USER_CACHE_KEY);
        return null;
    }
}

function writeCachedUser(userData: User) {
    localStorage.setItem(USER_CACHE_KEY, JSON.stringify({
        user: userData,
        cachedAt: Date.now()
    } satisfies CachedUser));
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isStealthMode, setIsStealthMode] = useState(false);

    const router = useRouter();
    const { addToast } = useToast();

    // 初始化：检查 Token 和 本地偏好设置
    useEffect(() => {
        const initAuth = async () => {
            // 1. 恢复隐身模式设置
            const storedStealth = localStorage.getItem("huawai_stealth_mode");
            if (storedStealth === "true") {
                setIsStealthMode(true);
            }

            // 2. 恢复用户 Session
            const token = localStorage.getItem("huawai_token");
            if (!token) {
                setIsLoading(false);
                return;
            }

            const cachedUser = readCachedUser(token);
            if (cachedUser) {
                setUser(cachedUser);
                setIsLoading(false);
            }

            try {
                const userData = await authApi.getMe(token);
                const userWithToken = { ...userData, token };
                setUser(userWithToken);
                writeCachedUser(userWithToken);
            } catch (e) {
                console.error("Auth check failed", e);
                localStorage.removeItem("huawai_token");
                localStorage.removeItem(USER_CACHE_KEY);
                setUser(null);
            } finally {
                if (!cachedUser) {
                    setIsLoading(false);
                }
            }
        };

        initAuth();
    }, []);

    const login = async (email: string, password: string, powHeaders?: PoWHeaders) => {
        const userData = await authApi.login(email, password, powHeaders);
        updateUser(userData);

        if (userData.status === "PENDING_VERIFICATION") {
            sessionStorage.setItem("pending_activation_email", email);
            router.push("/activate");
            return;
        }

        router.push("/");
    };

    const signup = async (name: string, email: string, password: string, powHeaders?: PoWHeaders) => {
        const response = await authApi.register(email, password, name, powHeaders);

        if (response.verifyRequired) {
            sessionStorage.setItem("pending_activation_email", email);
            router.push("/activate");
            return;
        }

        router.push("/login");
    };

    const updateUser = (userData: User) => {
        setUser(userData);
        if (userData.token) {
            localStorage.setItem("huawai_token", userData.token);
            writeCachedUser(userData);
        }
    };

    const logout = () => {
        setUser(null);
        localStorage.removeItem("huawai_token");
        localStorage.removeItem(USER_CACHE_KEY);
        addToast("已安全登出", "info");
        router.push("/login");
    };

    const toggleStealthMode = () => {
        const newValue = !isStealthMode;

        setIsStealthMode(newValue);
        localStorage.setItem("huawai_stealth_mode", String(newValue));

        if (newValue) {
            addToast("已开启隐身模式，暂停记录历史", "info");
        } else {
            addToast("已关闭隐身模式，恢复记录历史", "success");
        }
    };

    return (
        <AuthContext.Provider value={{
            user,
            isLoading,
            isStealthMode,
            login,
            signup,
            updateUser,
            logout,
            toggleStealthMode
        }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}
