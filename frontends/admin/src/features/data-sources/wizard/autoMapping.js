const LIST_RULES = {
    items_path: {
        aliases: ['list', 'vod_list', 'vods', 'videos', 'items', 'results', 'records', 'rows', 'data'],
        anti: ['page', 'pagecount', 'total', 'count'],
        container: true,
    },
    primary_id_path: {
        aliases: ['vod_id', 'source_vid', 'sourceVid', 'video_id', 'movie_id', 'data_id', 'vid', 'id'],
        anti: ['type_id', 'category_id', 'cat_id', 'class_id', 'douban_id', 'tmdb_id', 'imdb_id', 'page_id'],
    },
    title_path: {
        aliases: ['vod_name', 'video_name', 'movie_name', 'vod_title', 'title', 'name', 'subject'],
        anti: ['type_name', 'category_name', 'class_name', 'actor_name', 'director_name', 'source_name'],
    },
    update_time_path: {
        aliases: ['vod_time', 'update_time', 'updated_at', 'last_modified', 'modified_at', 'vod_addtime', 'created_at'],
        anti: ['play_time', 'runtime', 'duration'],
    },
};

const PAGINATION_RULES = {
    total_pages_path: {
        aliases: ['pagecount', 'page_count', 'total_pages', 'pages', 'last_page', 'page_total'],
        anti: ['total_items', 'total_count', 'vod_total'],
    },
    total_items_path: {
        aliases: ['total', 'total_items', 'total_count', 'recordcount', 'record_count', 'count'],
        anti: ['pagecount', 'page_count', 'total_pages', 'last_page', 'vod_total'],
    },
};

const DETAIL_RULES = {
    source_vid: {
        aliases: ['vod_id', 'source_vid', 'sourceVid', 'video_id', 'movie_id', 'data_id', 'vid', 'id'],
        anti: ['type_id', 'category_id', 'cat_id', 'class_id', 'douban_id', 'tmdb_id', 'imdb_id'],
    },
    title: {
        aliases: ['vod_name', 'video_name', 'movie_name', 'vod_title', 'title', 'name', 'subject'],
        anti: ['type_name', 'category_name', 'class_name', 'actor_name', 'director_name', 'source_name'],
    },
    aliasTitle: {
        aliases: ['vod_sub', 'sub_title', 'subtitle', 'alias_title', 'alias', 'original_name', 'name_en', 'aka'],
        anti: ['vod_name', 'type_name', 'category_name'],
    },
    coverImageUrl: {
        aliases: ['vod_pic', 'cover_image_url', 'coverImageUrl', 'cover_url', 'poster_url', 'pic', 'cover', 'poster', 'image_url', 'image', 'img', 'thumb', 'thumbnail'],
        anti: ['avatar', 'logo', 'icon'],
    },
    description: {
        aliases: ['vod_content', 'vod_blurb', 'vod_desc', 'description', 'desc', 'content', 'synopsis', 'plot', 'summary', '简介'],
        anti: ['play_url', 'playlist_url'],
    },
    year: {
        aliases: ['vod_year', 'release_year', 'year', 'date_year', '年份'],
        anti: ['updated_at', 'publish_time', 'duration'],
    },
    area: {
        aliases: ['vod_area', 'area', 'region', 'country', 'countries', '地区'],
        anti: ['language', 'lang'],
    },
    language: {
        aliases: ['vod_lang', 'vod_language', 'language', 'lang', 'audio_lang', '语言'],
        anti: ['area', 'region', 'country'],
    },
    remarks: {
        aliases: ['vod_remarks', 'remarks', 'remark', 'status_text', 'state', 'note', 'episode_status'],
        anti: ['description', 'content'],
    },
    score: {
        aliases: ['vod_score', 'score', 'rating', 'rate', 'douban_score', 'imdb_score'],
        anti: ['score_count', 'votes'],
    },
    publishedAt: {
        aliases: ['vod_pubdate', 'pubdate', 'publish_time', 'published_at', 'release_date', 'released_at', 'created_at', 'date'],
        anti: ['updated_at', 'vod_time'],
    },
    totalEpisodes: {
        aliases: ['vod_total', 'total_episodes', 'episodes_total', 'episode_count', 'episode_total', 'total', '集数'],
        anti: ['total_items', 'total_count', 'total_pages', 'pagecount'],
    },
    duration: {
        aliases: ['vod_duration', 'duration', 'runtime', 'runtime_minutes', 'time_length', 'length'],
        anti: ['update_time', 'published_at'],
    },
    actors: {
        aliases: ['vod_actor', 'actor_names', 'actors', 'actor', 'cast', 'casts', 'stars', 'starring', '主演'],
        anti: ['director', 'directors'],
    },
    directors: {
        aliases: ['vod_director', 'director_names', 'directors', 'director', '导演'],
        anti: ['actor', 'actors'],
    },
    genres: {
        aliases: ['vod_class', 'genre_names', 'genres', 'genre', 'class', 'tags', 'tag'],
        anti: ['type_name', 'category_name', 'vod_name'],
    },
    categoryName: {
        aliases: ['type_name', 'category_name', 'cat_name', 'class_name', 'raw_type_name', 'typeName'],
        anti: ['vod_name', 'video_name', 'movie_name', 'actor_name', 'director_name'],
    },
    typeId: {
        aliases: ['type_id', 'category_id', 'cat_id', 'class_id', 'raw_type_id', 'typeId', 'tid'],
        anti: ['vod_id', 'video_id', 'movie_id', 'douban_id', 'tmdb_id', 'imdb_id'],
    },
    playlist_from: {
        aliases: ['vod_play_from', 'play_from', 'playlist_from', 'source_from', 'from'],
        anti: ['play_url', 'playlist_url'],
    },
    playlist_url: {
        aliases: ['vod_play_url', 'play_url', 'playlist_url', 'episode_url', 'urls', 'url', 'm3u8'],
        anti: ['cover_url', 'poster_url', 'base_url'],
    },
    doubanId: {
        aliases: ['vod_douban_id', 'douban_id', 'doubanId', 'douban'],
        anti: ['score', 'rating'],
    },
    tmdbId: {
        aliases: ['vod_tmdb_id', 'tmdb_id', 'tmdbId', 'tmdb'],
        anti: [],
    },
    imdbId: {
        aliases: ['vod_imdb_id', 'imdb_id', 'imdbId', 'imdb'],
        anti: [],
    },
    rottenTomatoesId: {
        aliases: ['rotten_tomatoes_id', 'rottenTomatoesId', 'rt_id', 'tomato_id', 'rotten_tomatoes'],
        anti: ['score', 'rating'],
    },
};

function isBlank(value) {
    return value === undefined || value === null || value === '';
}

function normalizeKey(value) {
    return String(value || '')
        .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
        .toLowerCase()
        .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '_')
        .replace(/^_+|_+$/g, '');
}

function compact(value) {
    return normalizeKey(value).replace(/_/g, '');
}

function leaf(path) {
    const parts = String(path || '').split('.');
    return (parts[parts.length - 1] || '').replace(/\[.*?]/g, '');
}

function descendantCount(paths, path) {
    if (!path || path === '$') {
        return 0;
    }
    const arrayPrefix = `${path}[`;
    const objectPrefix = `${path}.`;
    return paths.filter(candidate =>
        candidate !== path && (candidate.startsWith(arrayPrefix) || candidate.startsWith(objectPrefix))
    ).length;
}

function aliasScore(pathLeaf, fullPath, aliases) {
    const leafKey = normalizeKey(pathLeaf);
    const leafCompact = compact(pathLeaf);
    const pathCompact = compact(fullPath);
    let best = Number.NEGATIVE_INFINITY;

    aliases.forEach((alias, index) => {
        const aliasKey = normalizeKey(alias);
        const aliasCompact = compact(alias);
        const specificity = Math.min(aliasCompact.length, 32);
        let score = Number.NEGATIVE_INFINITY;

        if (leafKey === aliasKey || leafCompact === aliasCompact) {
            score = 1000 - index * 8 + specificity;
        } else if (leafKey.endsWith(`_${aliasKey}`) || leafCompact.endsWith(aliasCompact)) {
            score = 650 - index * 8 + specificity;
        } else if (leafKey.includes(aliasKey) || leafCompact.includes(aliasCompact)) {
            score = 380 - index * 8 + specificity;
        } else if (pathCompact.includes(aliasCompact)) {
            score = 160 - index * 8 + specificity;
        }

        best = Math.max(best, score);
    });

    return best;
}

function antiPenalty(pathLeaf, rule) {
    if (!rule.anti?.length) {
        return 0;
    }
    const leafKey = normalizeKey(pathLeaf);
    const leafCompact = compact(pathLeaf);
    let penalty = 0;

    rule.anti.forEach(alias => {
        const aliasKey = normalizeKey(alias);
        const aliasCompact = compact(alias);
        if (leafKey === aliasKey || leafCompact === aliasCompact) {
            penalty += 900;
        } else if (leafKey.includes(aliasKey) || leafCompact.includes(aliasCompact)) {
            penalty += 450;
        }
    });

    return penalty;
}

function scorePath(path, rule, paths) {
    if (!path || path === '$') {
        return Number.NEGATIVE_INFINITY;
    }

    const pathLeaf = leaf(path);
    if (!pathLeaf) {
        return Number.NEGATIVE_INFINITY;
    }

    let score = aliasScore(pathLeaf, path, rule.aliases) - antiPenalty(pathLeaf, rule);
    const descendants = descendantCount(paths, path);

    if (rule.container) {
        if (descendants === 0) {
            return Number.NEGATIVE_INFINITY;
        }
        score += Math.min(descendants * 4, 120);
    } else if (descendants > 0 && !path.includes('[0].')) {
        score -= Math.min(descendants * 8, 160);
    }

    if (path.includes('[0].')) {
        score += 24;
    }

    const depth = path.split('.').length;
    score -= Math.max(0, depth - 4) * 2;

    return score;
}

function choosePath(paths, rule) {
    const ranked = paths
        .map(path => ({ path, score: scorePath(path, rule, paths) }))
        .filter(candidate => candidate.score > 0)
        .sort((left, right) => right.score - left.score || left.path.length - right.path.length);

    return ranked[0]?.path || '';
}

function mapIfBlank(target, field, paths, rule) {
    if (isBlank(target[field])) {
        target[field] = choosePath(paths, rule);
    }
}

export function performAutoMapping(jsonPaths, type, currentMapping) {
    const paths = Array.isArray(jsonPaths) ? jsonPaths : [];

    if (type === 'LIST') {
        const next = {
            ...currentMapping,
            pagination: {
                ...(currentMapping.pagination || {}),
            },
        };

        Object.entries(LIST_RULES).forEach(([field, rule]) => mapIfBlank(next, field, paths, rule));
        Object.entries(PAGINATION_RULES).forEach(([field, rule]) => {
            if (isBlank(next.pagination[field])) {
                next.pagination[field] = choosePath(paths, rule);
            }
        });

        return next;
    }

    if (type === 'DETAIL') {
        const next = { ...currentMapping };
        Object.entries(DETAIL_RULES).forEach(([field, rule]) => mapIfBlank(next, field, paths, rule));
        return next;
    }

    return currentMapping;
}
