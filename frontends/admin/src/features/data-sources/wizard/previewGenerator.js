// File: ircs-frontend/src/features/data-sources/wizard/previewGenerator.js
import { JSONPath } from 'jsonpath-plus';

const getValue = (json, path) => {
    if (!path || !json) return null;
    try {
        const result = JSONPath({ path, json, wrap: false });
        return result === undefined ? null : result;
    } catch (e) {
        console.warn(`Error resolving JSONPath "${path}":`, e.message);
        return null;
    }
};

const transformSplitByComma = (value) => {
    if (typeof value !== 'string' || !value) return null;
    return value.split(/[,，/|]/).map(s => s.trim()).filter(Boolean);
};

const transformPlaylist = (fromStr, urlStr) => {
    if (!fromStr || !urlStr) return [];

    const sources = fromStr.split('$$$');
    const urlGroups = urlStr.split('$$$');
    const playlists = [];

    for (let i = 0; i < Math.min(sources.length, urlGroups.length); i++) {
        const name = sources[i].trim();
        if (!name) continue;

        const episodes = urlGroups[i].split('#').map(epStr => {
            const parts = epStr.split('$');
            if (parts.length >= 2) {
                return { name: parts[0].trim(), url: parts[1].trim() };
            }
            return null;
        }).filter(Boolean);

        if (episodes.length > 0) {
            playlists.push({ name, episodes });
        }
    }
    return playlists;
};

export const generatePreview = (fieldMapping, json) => {
    if (!fieldMapping || !json) {
        return { error: "Missing mapping rules or sample JSON for preview." };
    }

    const { detail_mapping } = fieldMapping;
    const result = {};

    result.source_vid = getValue(json, detail_mapping.source_vid);
    result.title = getValue(json, detail_mapping.title);
    result.aliasTitle = getValue(json, detail_mapping.aliasTitle);
    result.cover_image_url = getValue(json, detail_mapping.coverImageUrl);
    result.description = getValue(json, detail_mapping.description);
    result.year = getValue(json, detail_mapping.year);
    result.area = getValue(json, detail_mapping.area);
    result.language = getValue(json, detail_mapping.language);
    result.remarks = getValue(json, detail_mapping.remarks);
    result.score = getValue(json, detail_mapping.score);
    result.publishedAt = getValue(json, detail_mapping.publishedAt);

    result.totalEpisodes = getValue(json, detail_mapping.totalEpisodes);
    result.duration = getValue(json, detail_mapping.duration);

    result.douban_id = getValue(json, detail_mapping.doubanId);
    result.tmdb_id = getValue(json, detail_mapping.tmdbId);
    result.imdb_id = getValue(json, detail_mapping.imdbId);
    result.rotten_tomatoes_id = getValue(json, detail_mapping.rottenTomatoesId);

    result.actors = transformSplitByComma(getValue(json, detail_mapping.actors));
    result.directors = transformSplitByComma(getValue(json, detail_mapping.directors));
    result.genres = transformSplitByComma(getValue(json, detail_mapping.genres));

    result.category = {
        id: getValue(json, detail_mapping.typeId),
        name: getValue(json, detail_mapping.categoryName)
    };
    result.playlists = transformPlaylist(
        getValue(json, detail_mapping.playlist_from),
        getValue(json, detail_mapping.playlist_url)
    );

    return result;
};
