// 通用分页响应
export interface PageResponse<T> {
    content: T[];
    page?: PageMetadata;
    totalPages?: number;
    totalElements?: number;
    size?: number;
    number?: number;
    last?: boolean;
    first?: boolean;
    empty?: boolean;
}

export interface PageMetadata {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    numberOfElements: number;
    empty: boolean;
}

export interface CategoryItem {
    name: string;
    slug: string;
}

export interface PortalMetadata {
    categories: CategoryItem[];
    genres: string[];
    areas: string[];
    languages: string[];
    years: string[];
}

export interface MagnetLink {
    id?: string;
    title: string;
    link: string;
    size?: string;
    date?: string;
    uploadDate?: string;
    tags?: string[];
}

export interface CloudLink {
    id?: string;
    title: string;
    link: string;
    code?: string;
    provider?: string;
}

export interface MovieCard {
    id: string;
    title: string;
    aliasTitle?: string;
    posterUrl: string;
    rating: number;
    releaseYear: string;
    categoryName: string;
    area: string;
    description?: string;
    genres?: string[];
    lastTrendAt?: string;

    remarks?: string;
    totalEpisodes?: string;
    duration?: string;

    // 新增字段
    season?: number;
    subtitle?: string;

    uniqueId?: string;
}

export interface CastMember {
    id: string;
    name: string;
    role: string;
}

export interface Episode {
    id: string;
    title: string;
    url: string;
}

export interface VideoSource {
    id: string;
    name: string;
    episodes: Episode[];
}

export interface MovieDetail {
    id: string;
    title: string;
    aliasTitle?: string;
    tagline?: string;
    description: string;
    rating: number;
    releaseYear: string;

    duration?: string;
    totalEpisodes?: string;
    remarks?: string;
    lastTrendAt?: string;

    // 新增字段
    season?: number;
    subtitle?: string;

    posterUrl: string;

    area: string;
    language: string;
    categoryName: string;

    doubanId?: string;
    tmdbId?: string;
    imdbId?: string;

    genres: string[];
    cast: CastMember[];

    sources: VideoSource[];
    magnets: MagnetLink[];
    cloudLinks: CloudLink[];
}

// 对应后端 PortalHomeVO
export interface HomeData {
    spotlight: MovieCard[];
    trending: MovieCard[];
    sections: CategorySection[];
}

export interface CategorySection {
    id: string;
    title: string;
    movies: MovieCard[];
}

// 用户相关
export interface User {
    id: string;
    email: string;
    nickname: string;
    avatarUrl: string;
    role: string;
    status: "PENDING_VERIFICATION" | "ACTIVE" | "BANNED";
    token?: string;

    experience: number;
    points: number;
    level: number;
    nextLevelXp: number;
    lastCheckInDate: string | null;
    checkInStreak: number;
    title: string;
}

export interface RegisterResponse {
    message: string;
    verifyRequired: boolean;
}

export interface InteractionRecord {
    id: string;
    unifiedVideoId: string;
    title: string;
    coverImageUrl: string;
    score: number;

    lastVideoId?: string;
    lastEpisodeId?: string;
    episodeName?: string;
    progressSeconds: number;
    durationSeconds: number;
    lastWatchedAt: string;

    isFavorite: boolean;
}

export type VideoFormat = 'hls' | 'mp4' | 'embed';

export interface ExploreOptions {
    page?: number;
    size?: number;
    keyword?: string;
    category?: string;
    genre?: string;
    area?: string;
    year?: string;
    language?: string;
    sort?: string;
}
