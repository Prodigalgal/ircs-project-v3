import { ExploreOptions, HomeData, MovieCard, MovieDetail, PageResponse, PortalMetadata } from "@/lib/types";

const API_PROXY_BASE = "/api/portal";

const getBaseUrl = (): string => {
    if (typeof window === 'undefined' && process.env.API_INTERNAL_URL) {
        return process.env.API_INTERNAL_URL;
    }

    if (typeof window !== 'undefined') {
        return API_PROXY_BASE;
    }

    if (process.env.BACKEND_URL) {
        return `${process.env.BACKEND_URL}/api/portal`;
    }

    return 'http://localhost:8080/api/portal';
};

const getAuthHeaders = (extraHeaders?: Record<string, string>) => {
    const headers: Record<string, string> = {
        "Content-Type": "application/json",
        ...extraHeaders
    };
    if (typeof window !== 'undefined') {
        const token = localStorage.getItem("huawai_token");
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }
    }
    return headers;
};

async function fetchWithRetry(url: string, options: RequestInit, retries = 2, backoff = 500): Promise<Response> {
    try {
        const res = await fetch(url, options);
        if (!res.ok) {
            if (res.status >= 500 && retries > 0) {
                console.warn(`[API] Fetch failed (${res.status}), retrying in ${backoff}ms...`);
                await new Promise(r => setTimeout(r, backoff));
                return fetchWithRetry(url, options, retries - 1, backoff * 2);
            }
        }
        return res;
    } catch (err) {
        if (retries > 0) {
            console.warn(`[API] Network error, retrying in ${backoff}ms...`, err);
            await new Promise(r => setTimeout(r, backoff));
            return fetchWithRetry(url, options, retries - 1, backoff * 2);
        }
        throw err;
    }
}

export const contentApi = {
    async getHomeData(headers?: Record<string, string>): Promise<HomeData> {
        const baseUrl = getBaseUrl();
        const url = `${baseUrl}/home`;

        const res = await fetchWithRetry(url, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 60 }
        });

        if (!res.ok) throw new Error(`Failed to fetch home data: ${res.status}`);
        return res.json();
    },

    async getMetadata(headers?: Record<string, string>): Promise<PortalMetadata> {
        const baseUrl = getBaseUrl();

        const res = await fetchWithRetry(`${baseUrl}/metadata`, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 3600 }
        });
        if (!res.ok) throw new Error("Failed to fetch metadata");
        return res.json();
    },

    async explore(options: ExploreOptions, headers?: Record<string, string>): Promise<PageResponse<MovieCard>> {
        const params = new URLSearchParams({
            page: (options.page || 0).toString(),
            size: (options.size || 20).toString(),
        });

        if (options.category && options.category !== 'all') params.append("type", options.category);
        if (options.sort) params.append("sort", options.sort);
        if (options.keyword) params.append("keyword", options.keyword);
        if (options.genre && options.genre !== '全部') params.append("genre", options.genre);
        if (options.area && options.area !== '全部') params.append("area", options.area);
        if (options.year && options.year !== '全部') params.append("year", options.year);
        if (options.language && options.language !== '全部') params.append("language", options.language);

        const baseUrl = getBaseUrl();
        const requestOptions: RequestInit = {
            headers: getAuthHeaders(headers),
        };

        if (typeof window === 'undefined') {
            requestOptions.next = { revalidate: 60 };
        } else {
            requestOptions.cache = 'no-store';
        }

        const res = await fetchWithRetry(`${baseUrl}/explore?${params.toString()}`, requestOptions);

        if (!res.ok) throw new Error("Failed to fetch explore data");
        return res.json();
    },

    async getMovieDetail(id: string, headers?: Record<string, string>): Promise<MovieDetail | null> {
        const baseUrl = getBaseUrl();
        const url = `${baseUrl}/movies/${id}`;

        const res = await fetchWithRetry(url, {
            headers: getAuthHeaders(headers),
            next: { revalidate: 900 }
        });

        if (!res.ok) {
            if (res.status === 404) return null;
            throw new Error(`Failed to fetch movie detail: ${res.status}`);
        }
        return res.json();
    }
};
