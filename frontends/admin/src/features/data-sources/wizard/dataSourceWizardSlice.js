// File: ircs-frontend/src/features/data-sources/wizard/dataSourceWizardSlice.js
import { createSlice } from '@reduxjs/toolkit';
import { parseJsonToPaths } from './jsonPathParser';
import { performAutoMapping } from './autoMapping';

const initialMapping = {
    list_mapping: {
        items_path: '',
        primary_id_path: '',
        title_path: '',
        update_time_path: '',
        pagination: { total_pages_path: '', total_items_path: '' },
    },
    detail_mapping: {
        source_vid: '',
        title: '',
        aliasTitle: '',
        coverImageUrl: '',
        description: '',
        year: '',
        area: '',
        language: '',
        remarks: '',
        score: '',
        publishedAt: '',
        totalEpisodes: '',
        duration: '',
        actors: '',
        directors: '',
        genres: '',
        categoryName: '',
        typeId: '',
        playlist_from: '',
        playlist_url: '',
        doubanId: '',
        tmdbId: '',
        imdbId: '',
        rottenTomatoesId: ''
    }
};

const initialState = {
    currentStep: 1,
    sampleJsonResponse: null,
    jsonPaths: [],
    previewResult: null,
    formData: {
        id: null,
        name: '',
        baseUrl: 'http://api.ffzyapi.com',
        listPath: '/api.php/provide/vod/',
        listParams: '{\n  "ac": "list",\n  "pg": "{page}"\n}',
        detailPath: '/api.php/provide/vod/',
        detailParams: '{\n  "ac": "detail",\n  "ids": "{ids}"\n}',
        transportMode: 'AUTO',
        httpProtocol: 'AUTO',
        ipVersionPolicy: 'AUTO',
        dnsResolverType: 'SYSTEM',
        dnsResolverEndpoint: '',
        connectTimeoutMs: '10000',
        readTimeoutMs: '30000',
        userAgent: '',
        adultRestricted: false,
        sampleId: '',
        fieldMapping: initialMapping
    },
    saveError: null
};

const getPathStr = (rule) => {
    if (!rule) return '';
    if (typeof rule === 'string') return rule;
    return rule.path || '';
};

const dataSourceWizardSlice = createSlice({
    name: 'dataSourceWizard',
    initialState,
    reducers: {
        nextStep: (state) => {
            if (state.currentStep < 4) state.currentStep += 1;
        },
        prevStep: (state) => {
            if (state.currentStep > 1) state.currentStep -= 1;
        },
        resetWizard: () => initialState,
        updateFormData: (state, action) => {
            state.formData = { ...state.formData, ...action.payload };
        },
        updateFieldMapping: (state, action) => {
            const { mapType, key, value } = action.payload;
            if (mapType === 'list_mapping_pagination') {
                state.formData.fieldMapping.list_mapping.pagination[key] = value;
            } else {
                state.formData.fieldMapping[mapType][key] = value;
            }
        },
        setPreviewResult: (state, action) => {
            state.previewResult = action.payload;
        },
        setSampleJson: (state, action) => {
            const { data, type } = action.payload;
            state.sampleJsonResponse = data;
            const paths = parseJsonToPaths(data);
            state.jsonPaths = paths;

            if (type === 'LIST') {
                state.formData.fieldMapping.list_mapping = performAutoMapping(paths, 'LIST', state.formData.fieldMapping.list_mapping);
            } else if (type === 'DETAIL') {
                state.formData.fieldMapping.detail_mapping = performAutoMapping(paths, 'DETAIL', state.formData.fieldMapping.detail_mapping);
            }
        },
        initializeWizardForEdit: (state, action) => {
            const dataSource = action.payload;

            state.currentStep = 1;
            state.saveError = null;
            state.sampleJsonResponse = null;
            state.previewResult = null;
            state.jsonPaths = [];

            state.formData.id = dataSource.id;
            state.formData.name = dataSource.name || '';
            state.formData.baseUrl = dataSource.baseUrl || '';
            state.formData.listPath = dataSource.listPath || '';
            state.formData.listParams = dataSource.listParams || '';
            state.formData.detailPath = dataSource.detailPath || '';
            state.formData.detailParams = dataSource.detailParams || '';
            state.formData.transportMode = dataSource.transportMode || 'AUTO';
            state.formData.httpProtocol = dataSource.httpProtocol || 'AUTO';
            state.formData.ipVersionPolicy = dataSource.ipVersionPolicy || 'AUTO';
            state.formData.dnsResolverType = dataSource.dnsResolverType || 'SYSTEM';
            state.formData.dnsResolverEndpoint = dataSource.dnsResolverEndpoint || '';
            state.formData.connectTimeoutMs = dataSource.connectTimeoutMs?.toString() || '10000';
            state.formData.readTimeoutMs = dataSource.readTimeoutMs?.toString() || '30000';
            state.formData.userAgent = dataSource.userAgent || '';
            state.formData.adultRestricted = Boolean(dataSource.adultRestricted);
            state.formData.sampleId = '';

            try {
                const parsedMapping = JSON.parse(dataSource.fieldMapping);
                const detailRules = parsedMapping.detail_mapping || {};

                state.formData.fieldMapping = {
                    ...initialMapping,
                    list_mapping: {
                        ...initialMapping.list_mapping,
                        ...(parsedMapping.list_mapping || {}),
                        pagination: {
                            ...initialMapping.list_mapping.pagination,
                            ...((parsedMapping.list_mapping && parsedMapping.list_mapping.pagination) || {})
                        }
                    },
                    detail_mapping: {
                        source_vid: getPathStr(detailRules.source_vid),
                        title: getPathStr(detailRules.title),
                        aliasTitle: getPathStr(detailRules.aliasTitle || detailRules.subTitle), // Handle migration
                        coverImageUrl: getPathStr(detailRules.coverImageUrl),
                        description: getPathStr(detailRules.description),
                        year: getPathStr(detailRules.year),
                        area: getPathStr(detailRules.area),
                        language: getPathStr(detailRules.language),
                        remarks: getPathStr(detailRules.remarks),
                        score: getPathStr(detailRules.score),
                        publishedAt: getPathStr(detailRules.publishedAt),
                        totalEpisodes: getPathStr(detailRules.totalEpisodes),
                        duration: getPathStr(detailRules.duration),

                        actors: getPathStr(detailRules.actors),
                        directors: getPathStr(detailRules.directors),
                        genres: getPathStr(detailRules.genres),
                        categoryName: getPathStr(detailRules.categoryName),
                        typeId: getPathStr(detailRules.typeId),
                        playlist_from: getPathStr(detailRules.playlist_from),
                        playlist_url: getPathStr(detailRules.playlist_url),

                        doubanId: getPathStr(detailRules.doubanId),
                        tmdbId: getPathStr(detailRules.tmdbId),
                        imdbId: getPathStr(detailRules.imdbId),
                        rottenTomatoesId: getPathStr(detailRules.rottenTomatoesId),
                    }
                };
            } catch (e) {
                console.error("Failed to parse fieldMapping for edit:", e);
                state.formData.fieldMapping = initialMapping;
            }
        },
        setSaveError: (state, action) => {
            state.saveError = action.payload;
        }
    },
});

export const {
    nextStep, prevStep, resetWizard, updateFormData, updateFieldMapping,
    setPreviewResult, setSampleJson, initializeWizardForEdit, setSaveError
} = dataSourceWizardSlice.actions;

export default dataSourceWizardSlice.reducer;
