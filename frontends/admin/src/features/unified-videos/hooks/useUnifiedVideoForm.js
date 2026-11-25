import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { debounce } from '@mui/material/utils';
import { useGetGenresQuery } from '../../genres/genresApi';
import { useLazyGetVideosQuery } from '../../videos/videosApi';
import { useEntityLocking } from '../../../shared/hooks/useEntityLocking';

export const UNIFIED_LOCKABLE_FIELDS = [
    'title', 'aliasTitle', 'season', 'subtitle',
    'description', 'year', 'area', 'language',
    'score', 'publishedAt',
    'totalEpisodes', 'duration', 'remarks',
    'doubanId', 'tmdbId', 'imdbId', 'rottenTomatoesId',
    'genres', 'category', 'actors', 'directors'
];

const emptyUnifiedVideo = {
    title: '', aliasTitle: '', season: '', subtitle: '',
    coverImageUrl: '', description: '', year: '',
    areas: [],
    languages: [],
    areaCodes: [],
    languageCodes: [],
    score: '', publishedAt: '',
    totalEpisodes: '', duration: '', remarks: '',
    doubanId: '', tmdbId: '', imdbId: '', rottenTomatoesId: '',
    sourceVideos: [],
    genres: [],
    genreCodes: [],
    actors: [],
    directors: [],
    actorNames: [],
    directorNames: [],
    categoryCode: '',
    coverImageStatus: null, coverImageStorageType: null,
    lastTrendAt: null,
    metadataStatus: 'SYNCED' // New
};

export function useUnifiedVideoForm(initialData, onSubmit) {
    const [formData, setFormData] = useState(emptyUnifiedVideo);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const initialSourceVideoIdsRef = useRef(new Set());

    const [inputValue, setInputValue] = useState('');
    const [options, setOptions] = useState([]);

    const {
        lockedFields, isLocked, toggleLock,
        lockAll, unlockAll, setLocks, lockedCount, totalLockableCount
    } = useEntityLocking(UNIFIED_LOCKABLE_FIELDS);

    const [triggerSearch, { data: searchResults, isFetching: isSearching }] = useLazyGetVideosQuery();

    const { data: standardGenres = [] } = useGetGenresQuery();

    const fetchVideos = useMemo(
        () => debounce((input) => {
            triggerSearch({ title: input, page: 0, size: 20 });
        }, 400),
        [triggerSearch]
    );

    useEffect(() => {
        if (inputValue === '') {
            setOptions([]);
            return;
        }
        fetchVideos(inputValue);
    }, [inputValue, fetchVideos]);

    useEffect(() => {
        if (searchResults && searchResults.content) {
            setOptions(searchResults.content);
        }
    }, [searchResults]);

    useEffect(() => {
        if (initialData) {
            const languages = (initialData.languages && Array.isArray(initialData.languages))
                ? initialData.languages
                : [];

            const sources = initialData.sourceVideos || [];

            initialSourceVideoIdsRef.current = new Set(sources.map(v => v.id));

            setFormData({
                id: initialData.id,
                title: initialData.title || '',
                aliasTitle: initialData.aliasTitle || initialData.subTitle || '',
                season: initialData.season || '',
                subtitle: initialData.subtitle || '',

                coverImageUrl: initialData.coverImageUrl || '',
                coverImageStatus: initialData.coverImageStatus,
                coverImageStorageType: initialData.coverImageStorageType,
                description: initialData.description || '',
                year: initialData.year || '',
                areas: initialData.areas || [],
                languages: languages,
                areaCodes: initialData.areaCodes || (initialData.areas || []).map(a => a.code).filter(Boolean),
                languageCodes: initialData.languageCodes || [],
                score: initialData.score || '',
                publishedAt: initialData.publishedAt || '',

                totalEpisodes: initialData.totalEpisodes || '',
                duration: initialData.duration || '',
                remarks: initialData.remarks || '',

                doubanId: initialData.doubanId || '',
                tmdbId: initialData.tmdbId || '',
                imdbId: initialData.imdbId || '',
                rottenTomatoesId: initialData.rottenTomatoesId || '',
                sourceVideos: sources,
                genres: initialData.genres || [],
                genreCodes: initialData.genreCodes || [],
                actors: initialData.actors || [],
                directors: initialData.directors || [],
                actorNames: initialData.actorNames || initialData.actors || [],
                directorNames: initialData.directorNames || initialData.directors || [],
                categoryId: initialData.categoryId || '',
                categoryName: initialData.categoryName || '',
                categoryCode: initialData.categoryCode || '',
                lastTrendAt: initialData.lastTrendAt || null,
                metadataStatus: initialData.metadataStatus || 'SYNCED' // New
            });
            setLocks(initialData.lockedFields || []);
        } else {
            setFormData(emptyUnifiedVideo);
            initialSourceVideoIdsRef.current = new Set();
            setLocks([]);
        }
    }, [initialData, setLocks]);

    const handleChange = useCallback((e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    }, []);

    const handleBatchLock = useCallback((shouldLockAll) => {
        if(shouldLockAll) lockAll(); else unlockAll();
    }, [lockAll, unlockAll]);

    const handleAddSourceVideo = useCallback((event, newValue) => {
        if (newValue) {
            setFormData(prev => {
                if (prev.sourceVideos.some(v => v.id === newValue.id)) return prev;
                return {
                    ...prev,
                    sourceVideos: [...prev.sourceVideos, {
                        id: newValue.id,
                        title: newValue.title,
                        sourceVid: newValue.sourceVid
                    }]
                };
            });
            setInputValue('');
        }
    }, []);

    const handleRemoveSourceVideo = useCallback((id) => {
        setFormData(prev => ({
            ...prev,
            sourceVideos: prev.sourceVideos.filter(v => v.id !== id)
        }));
    }, []);

    const handleSubmit = useCallback(async () => {
        setIsSubmitting(true);
        try {
            const payload = {
                ...formData,
                season: formData.season === '' ? null : parseInt(formData.season, 10), // Parse integer
                lockedFields,
                areaCodes: formData.areas.map(a => a.code).filter(Boolean),
                languageCodes: formData.languageCodes,
                genreCodes: formData.genreCodes,
                actorNames: formData.actors.map(v => typeof v === 'string' ? v : v.name).filter(Boolean),
                directorNames: formData.directors.map(v => typeof v === 'string' ? v : v.name).filter(Boolean),
                categoryCode: formData.categoryCode,
                areas: undefined,
                languages: undefined,
                genres: undefined,
                actors: undefined,
                directors: undefined,
                sourceVideos: undefined,
                coverImageStatus: undefined,
                coverImageStorageType: undefined,
                categoryName: undefined,
                metadataStatus: undefined // Read-only
            };

            const currentIds = new Set(formData.sourceVideos.map(v => v.id));
            const initialIds = initialSourceVideoIdsRef.current;

            const bindVideoIds = [];
            const unbindVideoIds = [];

            for (const id of currentIds) {
                if (!initialIds.has(id)) {
                    bindVideoIds.push(id);
                }
            }

            for (const id of initialIds) {
                if (!currentIds.has(id)) {
                    unbindVideoIds.push(id);
                }
            }

            payload.bindVideoIds = bindVideoIds;

            if (formData.id) {
                payload.unbindVideoIds = unbindVideoIds;
            }

            await onSubmit(payload);
        } finally {
            setIsSubmitting(false);
        }
    }, [formData, lockedFields, onSubmit]);

    return {
        formData,
        setFormData,
        handleChange,
        handleLockChange: toggleLock,
        handleBatchLock,
        isLocked,
        handleSubmit,
        isSubmitting,
        inputValue,
        setInputValue,
        options,
        isSearching,
        handleAddSourceVideo,
        handleRemoveSourceVideo,
        lockedCount,
        totalLockableCount,
        standardGenres
    };
}
