import { PageResponse, InteractionRecord } from "@/lib/types";

const API_BASE = "/api/portal/interaction";
const FEEDBACK_BASE = "/api/portal/feedback";
const MEDIA_REQUEST_BASE = "/api/portal/media-requests";

export interface UserMessage {
    id: string;
    content: string;
    reply: string;
    status: 'PENDING' | 'REPLIED' | 'CLOSED';
    createdAt: string;
    // For public wall
    memberNickname?: string;
    memberAvatarUrl?: string;
}

export interface MediaRequest {
    id: string;
    title: string;
    releaseYear?: number;
    extraInfo?: string;
    status: 'PENDING' | 'SCHEDULING' | 'BATCHED' | 'SCHEDULED' | 'SKIPPED_EXISTS' | 'CANCELLED' | 'COMPLETED' | 'FAILED';
    requestCount: number;
    createdAt: string;
    updatedAt: string;
    lastRequestedAt: string;
    scheduledAt?: string;
    completedAt?: string;
    lastErrorMessage?: string;
    existingVideoId?: string;
    existingVideoSource?: string;
    spentPoints: number;
}

export const interactionApi = {
    async toggleFavorite(unifiedVideoId: string): Promise<boolean> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/favorites/${unifiedVideoId}`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to toggle favorite");
        const data = await res.json();
        return data.favorited;
    },

    async checkFavorite(unifiedVideoId: string): Promise<boolean> {
        const token = localStorage.getItem("huawai_token");
        if (!token) return false;

        const res = await fetch(`${API_BASE}/favorites/${unifiedVideoId}/status`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) return false;
        const data = await res.json();
        return data.favorited;
    },

    async getFavorites(page = 0, size = 20): Promise<PageResponse<InteractionRecord>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/favorites?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch favorites");
        return res.json();
    },

    async reportProgress(
        unifiedVideoId: string,
        videoId: string,
        episodeId: string | undefined,
        episodeName: string,
        progress: number,
        duration: number,
        useKeepAlive = false
    ): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        if (!token) return;

        await fetch(`${API_BASE}/history`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({
                unifiedVideoId,
                videoId,
                episodeId,
                episodeName,
                progress,
                duration
            }),
            keepalive: useKeepAlive
        });
    },

    async getHistory(page = 0, size = 20): Promise<PageResponse<InteractionRecord>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${API_BASE}/history?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch history");
        return res.json();
    },

    async clearHistory(): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        await fetch(`${API_BASE}/history`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
    },

    async deleteRecord(historyId: string): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        await fetch(`${API_BASE}/history/${historyId}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
    },

    // --- Feedback / Messages ---

    async submitMessage(content: string): Promise<UserMessage> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({ content })
        });
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || "Failed to submit message");
        }
        return res.json();
    },

    async getMyMessages(page = 0, size = 10): Promise<PageResponse<UserMessage>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch messages");
        return res.json();
    },

    async getPublicWall(page = 0, size = 20): Promise<PageResponse<UserMessage>> {
        const res = await fetch(`${FEEDBACK_BASE}/wall?page=${page}&size=${size}`, {
            method: "GET"
        });
        if (!res.ok) throw new Error("Failed to fetch message wall");
        return res.json();
    },

    async deleteMessage(messageId: string): Promise<void> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${FEEDBACK_BASE}/${messageId}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to delete message");
    },

    async submitMediaRequest(title: string, releaseYear?: number, extraInfo?: string): Promise<MediaRequest> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${MEDIA_REQUEST_BASE}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
            },
            body: JSON.stringify({ title, releaseYear, extraInfo })
        });
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || "Failed to submit media request");
        }
        return res.json();
    },

    async getMyMediaRequests(page = 0, size = 20): Promise<PageResponse<MediaRequest>> {
        const token = localStorage.getItem("huawai_token");
        const res = await fetch(`${MEDIA_REQUEST_BASE}?page=${page}&size=${size}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (!res.ok) throw new Error("Failed to fetch media requests");
        return res.json();
    }
};
