import React, { useMemo } from 'react';
import { Card, CardMedia, Chip, Grid, TextField, Box, Stack } from '@mui/material';
import LockableTextField from '../../../components/common/LockableTextField';
import AreaSelect from '../../../components/common/AreaSelect';
import LanguageSelect from '../../../components/common/LanguageSelect';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import StorageIcon from '@mui/icons-material/Storage';
import CloudDoneIcon from '@mui/icons-material/CloudDone';
import LinkIcon from '@mui/icons-material/Link';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import DeleteIcon from '@mui/icons-material/Delete';
import { Autocomplete, CircularProgress } from '@mui/material';
import { ENRICH_STATUS, NORMALIZATION_STATUS, AGGREGATION_STATUS, getStatusConfig } from '../../../utils/constants';
import { useGetAllDataSourcesQuery } from '../../data-sources/dataSourcesApi';
import { toDateInputValue } from '../../../utils/dateInput';

const FALLBACK_IMAGE = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='300' viewBox='0 0 200 300'%3E%3Crect width='200' height='300' fill='%23333'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' font-family='sans-serif' font-size='20' fill='%23666'%3ENo Image%3C/text%3E%3C/svg%3E";

function ImageStatusChip({ status, storageType }) {
    if (!status || status === 'UNPROCESSED') {
        return <Chip label="外部链接 (未处理)" color="default" size="small" variant="outlined" icon={<LinkIcon />} />;
    }
    if (status === 'FETCHING') {
        return <Chip label="下载中..." color="info" size="small" variant="outlined" icon={<CloudDownloadIcon />} />;
    }
    if (status === 'LOCAL_STORED') {
        if (storageType === 'LOCAL') {
            return <Chip label="本地已存储" color="success" size="small" variant="outlined" icon={<StorageIcon />} />;
        }
        return <Chip label="本地缓存" color="warning" size="small" variant="outlined" icon={<StorageIcon />} />;
    }
    if (status === 'UPLOADING') {
        return <Chip label="上传 R2 中..." color="info" size="small" variant="outlined" icon={<CloudUploadIcon />} />;
    }
    if (status === 'REMOTE_STORED') {
        return <Chip label="R2 托管" color="success" size="small" variant="filled" icon={<CloudDoneIcon />} />;
    }
    if (status === 'FAILED' || status === 'DEAD') {
        return <Chip label="处理失败" color="error" size="small" variant="outlined" icon={<ErrorOutlineIcon />} />;
    }
    if (status === 'PENDING_DELETE') {
        return <Chip label="待删除" color="warning" size="small" variant="outlined" icon={<DeleteIcon />} />;
    }
    return null;
}

const DataSourceSelect = ({ value, onChange }) => {
    const { data: dataSources = [], isLoading } = useGetAllDataSourcesQuery();

    return (
        <Autocomplete
            options={dataSources}
            getOptionLabel={(option) => option.name}
            isOptionEqualToValue={(opt, val) => opt.id === val.id}
            value={value}
            onChange={onChange}
            loading={isLoading}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label="所属数据源"
                    fullWidth
                    required
                    slotProps={{
                        ...params.slotProps,

                        input: {
                            ...params.slotProps.input,
                            endAdornment: (
                                <React.Fragment>
                                    {isLoading ? <CircularProgress color="inherit" size={20} /> : null}
                                    {params.slotProps.input.endAdornment}
                                </React.Fragment>
                            ),
                        }
                    }}
                />
            )}
        />
    );
};


function VideoBasicTab({ formData, handleChange, isLocked, handleLockChange }) {

    const previewUrl = useMemo(() => {
        if (formData.coverImageUrl && formData.coverImageUrl !== formData.coverImageOriginalUrl) {
            return formData.coverImageUrl;
        }
        if (formData.resolvedCoverUrl) {
            return formData.resolvedCoverUrl;
        }
        return formData.coverImageOriginalUrl;
    }, [formData.coverImageUrl, formData.coverImageOriginalUrl, formData.resolvedCoverUrl]);

    const languageArray = useMemo(() => {
        if (!formData.language) return [];
        return formData.language.split(/[,，]/)
            .map(s => s.trim())
            .filter(Boolean);
    }, [formData.language]);

    const handleLanguageChange = (event) => {
        const newArray = event.target.value;
        const newString = newArray.join(',');
        handleChange({ target: { name: 'language', value: newString } });
    };

    const handleDataSourceChange = (event, newValue) => {
        handleChange({
            target: {
                name: 'dataSourceId',
                value: newValue ? newValue.id : null
            }
        });
        handleChange({
            target: {
                name: 'dataSourceName',
                value: newValue ? newValue.name : ''
            }
        });

        handleChange({
            target: { name: 'sourceCategoryName', value: '' }
        });
        handleChange({
            target: { name: 'sourceCategoryCode', value: '' }
        });
        handleChange({
            target: { name: 'categoryName', value: '' }
        });
        handleChange({
            target: { name: 'categoryCode', value: '' }
        });
    };

    const currentDataSource = useMemo(() => {
        if (!formData.dataSourceId) return null;
        return { id: formData.dataSourceId, name: formData.dataSourceName || 'Loading...' };
    }, [formData.dataSourceId, formData.dataSourceName]);

    const normConfig = getStatusConfig(NORMALIZATION_STATUS, formData.normalizationStatus);
    const enrichConfig = getStatusConfig(ENRICH_STATUS, formData.enrichmentStatus);
    const aggConfig = getStatusConfig(AGGREGATION_STATUS, formData.aggregationStatus);

    return (
        <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 4 }}>
                <Card variant="outlined" sx={{
                    height: '100%', minHeight: 350, display: 'flex', flexDirection: 'column',
                    alignItems: 'center', bgcolor: '#000', position: 'relative', p: 1
                }}>
                    <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%' }}>
                        <CardMedia
                            component="img"
                            image={previewUrl || FALLBACK_IMAGE}
                            alt={formData.title}
                            loading="lazy"
                            decoding="async"
                            sx={{ objectFit: 'contain', maxHeight: 400, maxWidth: '100%', borderRadius: 1 }}
                            onError={(e) => { if (e.target.src !== FALLBACK_IMAGE) e.target.src = FALLBACK_IMAGE; }}
                            referrerPolicy="no-referrer"
                        />
                    </Box>
                    <Box sx={{ mt: 2, mb: 1, width: '100%' }}>
                        <LockableTextField
                            name="coverImageUrl"
                            label="封面 URL (源)"
                            value={formData.coverImageUrl}
                            onChange={handleChange}
                            fullWidth
                            size="small"
                            variant="filled"
                            locked={isLocked('coverImageUrl')}
                            onLockChange={handleLockChange}
                            slotProps={{ input: { style: { fontSize: '0.75rem' } } }}
                        />
                        <Box sx={{ mt: 1, textAlign: 'center' }}>
                            <ImageStatusChip
                                status={formData.coverImageStatus}
                                storageType={formData.coverImageStorageType}
                            />
                        </Box>

                        <Stack
                            direction="row"
                            spacing={1}
                            sx={{
                                justifyContent: "center",
                                mt: 2
                            }}>
                            <Chip label={`清洗: ${normConfig.label}`} color={normConfig.color} size="small" variant="outlined" />
                            <Chip label={`元数据: ${enrichConfig.label}`} color={enrichConfig.color} size="small" variant="outlined" />
                            <Chip label={`聚合: ${aggConfig.label}`} color={aggConfig.color} size="small" variant="outlined" />
                        </Stack>
                    </Box>
                </Card>
            </Grid>

            <Grid size={{ xs: 12, md: 8 }}>
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                        <LockableTextField
                            name="title" label="主标题 (Main Title)" value={formData.title} onChange={handleChange} fullWidth required
                            locked={isLocked('title')} onLockChange={handleLockChange}
                            placeholder="清洗后的纯净标题 (不含季数/副标)"
                        />
                    </Grid>

                    <Grid size={{ xs: 4 }}>
                        <LockableTextField
                            name="season" label="季/部 (Season)" type="number" value={formData.season} onChange={handleChange} fullWidth
                            locked={isLocked('season')} onLockChange={handleLockChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 8 }}>
                        <LockableTextField
                            name="subtitle" label="副标题 (Subtitle)" value={formData.subtitle} onChange={handleChange} fullWidth
                            locked={isLocked('subtitle')} onLockChange={handleLockChange}
                            placeholder="从标题中提取的章节名 (如: 决战)"
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                        <LockableTextField
                            name="aliasTitle" label="别名 (Alias/SubTitle)" value={formData.aliasTitle} onChange={handleChange} fullWidth
                            locked={isLocked('aliasTitle')} onLockChange={handleLockChange}
                            placeholder="源站提供的副标题 (如: 4K修正版)"
                        />
                    </Grid>

                    <Grid size={{ xs: 6 }}>
                        <DataSourceSelect
                            value={currentDataSource}
                            onChange={handleDataSourceChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="sourceCategoryName"
                            label="源站分类名称"
                            value={formData.sourceCategoryName || ''}
                            onChange={handleChange}
                            fullWidth
                            locked={isLocked('category')}
                            onLockChange={handleLockChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="sourceCategoryCode"
                            label="源站分类 Code"
                            value={formData.sourceCategoryCode || ''}
                            onChange={handleChange}
                            fullWidth
                            locked={isLocked('category')}
                            onLockChange={handleLockChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 6 }}>
                        <AreaSelect
                            name="areas"
                            label="地区"
                            value={formData.areas || []}
                            onChange={handleChange}
                            fullWidth
                            locked={isLocked('area')}
                            onLockChange={handleLockChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                        <LanguageSelect
                            name="language"
                            label="语言 (Raw)"
                            value={languageArray}
                            onChange={handleLanguageChange}
                            fullWidth
                            multiple
                            freeSolo
                            locked={isLocked('language')}
                            onLockChange={handleLockChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="year" label="年份" value={formData.year} onChange={handleChange} fullWidth
                            locked={isLocked('year')} onLockChange={handleLockChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="score" label="评分" type="number" value={formData.score} onChange={handleChange} fullWidth
                            locked={isLocked('score')} onLockChange={handleLockChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="totalEpisodes" label="总集数" value={formData.totalEpisodes} onChange={handleChange} fullWidth
                            locked={isLocked('totalEpisodes')} onLockChange={handleLockChange}
                        />
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                        <LockableTextField
                            name="duration" label="时长" value={formData.duration} onChange={handleChange} fullWidth
                            locked={isLocked('duration')} onLockChange={handleLockChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                        <LockableTextField
                            name="publishedAt"
                            label="发布日期"
                            type="date"
                            value={toDateInputValue(formData.publishedAt)}
                            onChange={handleChange}
                            fullWidth
                            locked={isLocked('publishedAt')}
                            onLockChange={handleLockChange}
                            slotProps={{
                                inputLabel: {
                                    shrink: true
                                }
                            }}
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                        <LockableTextField
                            name="remarks" label="状态/备注" value={formData.remarks} onChange={handleChange} fullWidth
                            locked={isLocked('remarks')} onLockChange={handleLockChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}>
                        <LockableTextField
                            name="description" label="简介" value={formData.description} onChange={handleChange}
                            fullWidth multiline rows={4}
                            locked={isLocked('description')} onLockChange={handleLockChange}
                        />
                    </Grid>
                </Grid>
            </Grid>
        </Grid>
    );
}

export default VideoBasicTab;
