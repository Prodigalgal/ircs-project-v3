import { Episode, VideoFormat } from "@/lib/types";

export const VideoDomain = {
    /**
     * 检测视频 URL 的格式类型
     */
    detectFormat(url: string): VideoFormat {
        if (!url) return 'embed';
        const lower = url.toLowerCase();

        if (lower.includes('.m3u8')) {
            return 'hls';
        }
        if (lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.ogg')) {
            return 'mp4';
        }

        return 'embed';
    },

    /**
     * 将集数列表按照播放方式分组 (直连 vs 外链/Embed)
     * 用于 UI 层的分栏展示
     */
    groupEpisodes(episodes: Episode[]) {
        const native: Episode[] = [];
        const external: Episode[] = [];

        episodes.forEach(ep => {
            const fmt = this.detectFormat(ep.url);
            if (fmt === 'embed') {
                external.push(ep);
            } else {
                native.push(ep);
            }
        });

        return { native, external };
    }
};