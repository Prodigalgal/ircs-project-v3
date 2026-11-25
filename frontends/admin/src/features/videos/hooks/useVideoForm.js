import { useState, useEffect, useCallback, useMemo } from 'react';
import { useLazyGetCategoriesQuery } from '../../categories/categoriesApi';
import { useGetUnifiedVideoByIdQuery } from '../../unified-videos/unifiedVideosApi';
import { useEntityLocking } from '../../../shared/hooks/useEntityLocking';

export const VIDEO_LOCKABLE_FIELDS = [
    'title', 'aliasTitle', 'season', 'subtitle',
    'description', 'year', 'area', 'language',
    'remarks', 'score', 'publishedAt', 'category',
    'totalEpisodes', 'duration',
    'actors', 'directors', 'genreCodes',
    'doubanId', 'tmdbId', 'imdbId', 'rottenTomatoesId',
    'coverImageUrl'
];

const emptyVideo = {
    title: '', aliasTitle: '', season: '', subtitle: '',
    coverImageUrl: '', description: '', year: '',
    language: '', remarks: '', score: '', publishedAt: '',
    totalEpisodes: '', duration: '',
    sourceCategoryName: '', dataSourceId: null, dataSourceName: '',
    categoryId: '', categoryName: '', categoryCode: '', sourceCategoryCode: '',
    doubanId: '', tmdbId: '', imdbId: '', rottenTomatoesId: '',
    actors: [], directors: [], areas: [], genres: [],
    actorNames: [], directorNames: [], areaCodes: [], languageCodes: [], genreCodes: [],
    sourceVid: '', id: '',
    coverImageStatus: null, coverImageStorageType: null,
    detailApiUrl: '',
    normalizationStatus: '',
    enrichmentStatus: '',
    aggregationStatus: '', // New
    rawMetadata: '',
    unifiedVideoId: null, unifiedVideoTitle: null,
    playlists: [],
    coverImageOriginalUrl: '',
    resolvedCoverUrl: '',
    coverImageStoragePath: ''
};

export function useVideoForm(initialVideo, onSubmit) {
    const [formData, setFormData] = useState(emptyVideo);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [tabIndex, setTabIndex] = useState(0);

    const {
        lockedFields, isLocked, toggleLock,
        lockAll, unlockAll, setLocks, lockedCount, totalLockableCount
    } = useEntityLocking(VIDEO_LOCKABLE_FIELDS);

    const resetForm = useCallback(() => {
        if (initialVideo) {
            const areaCodes = initialVideo.areaCodes || [];
            const genreCodes = initialVideo.genreCodes || [];
            const languageCodes = initialVideo.languageCodes || [];
            const languageStr = initialVideo.rawLanguageStr
                || (Array.isArray(initialVideo.languages) ? initialVideo.languages.join(', ') : '');

            const inputUrl = initialVideo.coverImageOriginalUrl || initialVideo.coverImageUrl || '';

            setFormData({
                id: initialVideo.id,
                title: initialVideo.title || '',
                aliasTitle: initialVideo.aliasTitle || initialVideo.subTitle || '',
                season: initialVideo.season || '',
                subtitle: initialVideo.subtitle || '',

                coverImageUrl: inputUrl,
                resolvedCoverUrl: initialVideo.coverImageUrl || '',
                coverImageOriginalUrl: initialVideo.coverImageOriginalUrl || '',
                coverImageStoragePath: initialVideo.coverImageStoragePath || '',

                coverImageStatus: initialVideo.coverImageStatus,
                coverImageStorageType: initialVideo.coverImageStorageType,
                description: initialVideo.description || '',
                year: initialVideo.year || '',

                areas: areaCodes.map(code => ({ id: code, code, name: code })),

                language: languageStr,
                remarks: initialVideo.remarks || '',
                score: initialVideo.score || '',
                publishedAt: initialVideo.publishedAt ? initialVideo.publishedAt.split('T')[0] : '',

                totalEpisodes: initialVideo.totalEpisodes || '',
                duration: initialVideo.duration || '',

                sourceCategoryName: initialVideo.sourceCategoryName,
                sourceCategoryCode: initialVideo.sourceCategoryCode || '',
                dataSourceId: initialVideo.dataSourceId,
                dataSourceName: initialVideo.dataSourceName,

                categoryId: initialVideo.categoryId || '',
                categoryName: initialVideo.categoryName || '',
                categoryCode: initialVideo.categoryCode || '',

                doubanId: initialVideo.doubanId || '',
                tmdbId: initialVideo.tmdbId || '',
                imdbId: initialVideo.imdbId || '',
                rottenTomatoesId: initialVideo.rottenTomatoesId || '',
                sourceVid: initialVideo.sourceVid || '',
                detailApiUrl: initialVideo.detailApiUrl || '',
                actors: initialVideo.actorNames || [],
                directors: initialVideo.directorNames || [],
                genres: genreCodes.map(code => ({ id: code, code, name: code })),
                actorNames: initialVideo.actorNames || [],
                directorNames: initialVideo.directorNames || [],
                areaCodes: areaCodes,
                languageCodes: languageCodes,
                genreCodes: genreCodes,
                normalizationStatus: initialVideo.normalizationStatus || 'PENDING',
                enrichmentStatus: initialVideo.enrichmentStatus || 'PENDING',
                aggregationStatus: initialVideo.aggregationStatus || 'PENDING', // New
                rawMetadata: initialVideo.rawMetadata || '',
                unifiedVideoId: initialVideo.unifiedVideoId || null,
                playlists: initialVideo.playlists || []
            });
            setLocks(initialVideo.lockedFields || []);
        } else {
            setFormData(emptyVideo);
            setLocks([]);
        }
        setTabIndex(0);
    }, [initialVideo, setLocks]);

    useEffect(() => {
        resetForm();
    }, [resetForm]);

    const handleChange = useCallback((e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    }, []);

    const handleSubmit = useCallback(async () => {
        setIsSubmitting(true);
        const submissionData = {
            ...formData,
            season: formData.season === '' ? null : parseInt(formData.season, 10),
            rawLanguageStr: formData.language,
            actorNames: formData.actors.map(v => typeof v === 'string' ? v : v.name).filter(Boolean),
            directorNames: formData.directors.map(v => typeof v === 'string' ? v : v.name).filter(Boolean),
            areaCodes: (formData.areas || []).map(a => a.code || a.id).filter(Boolean),
            languageCodes: formData.languageCodes,
            genreCodes: (formData.genres || []).map(g => g.code || g.name || g).filter(Boolean),
            categoryCode: formData.categoryCode,
            sourceCategoryCode: formData.sourceCategoryCode,
            sourceCategoryName: formData.sourceCategoryName,
            dataSourceId: formData.dataSourceId,

            unifiedVideoId: formData.unifiedVideoId,
            lockedFields: lockedFields,
            playlists: undefined,
            actors: undefined, directors: undefined, areas: undefined, genres: undefined,
            sourceVid: undefined, dataSourceName: undefined, detailApiUrl: undefined,
            categoryName: undefined, // Remove read-only names
            coverImageStatus: undefined, coverImageStorageType: undefined,
            normalizationStatus: undefined, enrichmentStatus: undefined, aggregationStatus: undefined, // Read-only statuses
            rawMetadata: undefined, unifiedVideoTitle: undefined,
            coverImageOriginalUrl: undefined, resolvedCoverUrl: undefined, coverImageStoragePath: undefined
        };
        try {
            await onSubmit(submissionData);
        } catch (e) {
            console.error(e);
        } finally {
            setIsSubmitting(false);
        }
    }, [formData, lockedFields, onSubmit]);

    const currentUnifiedVideoValue = useMemo(() =>
            formData.unifiedVideoId
                ? { id: formData.unifiedVideoId, title: formData.unifiedVideoTitle || '(Loading...)' }
                : null
        , [formData.unifiedVideoId, formData.unifiedVideoTitle]);

    const { data: unifiedVideoDetail } = useGetUnifiedVideoByIdQuery(
        formData.unifiedVideoId,
        { skip: !formData.unifiedVideoId }
    );

    useEffect(() => {
        if (unifiedVideoDetail && formData.unifiedVideoId === unifiedVideoDetail.id) {
            setFormData(prev => ({...prev, unifiedVideoTitle: unifiedVideoDetail.title}));
        }
    }, [unifiedVideoDetail, formData.unifiedVideoId]);

    const handleBatchLockWrapper = (shouldLock) => {
        if (shouldLock) lockAll(); else unlockAll();
    };

    return {
        formData,
        setFormData,
        handleChange,
        handleLockChange: toggleLock,
        handleBatchLock: handleBatchLockWrapper,
        isLocked,
        handleSubmit,
        isSubmitting,
        tabIndex,
        setTabIndex,
        resetForm,
        currentUnifiedVideoValue,
        categoryFetch: useLazyGetCategoriesQuery,
        lockedCount,
        totalLockableCount
    };
}
