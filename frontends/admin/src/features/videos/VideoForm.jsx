import React, {useState, useMemo, memo, useEffect} from 'react';
import {
    Autocomplete, Box, Button, Chip, CircularProgress, Dialog, DialogActions,
    DialogContent, DialogTitle, Divider, Tab, Tabs, TextField, Typography,
    InputAdornment, IconButton, Tooltip, Paper, Grid, Alert, Stack, Menu, MenuItem, Skeleton
} from '@mui/material';
import ApiIcon from '@mui/icons-material/Api';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import PlaylistPlayIcon from '@mui/icons-material/PlaylistPlay';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import DeleteIcon from '@mui/icons-material/Delete';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import CloudSyncIcon from '@mui/icons-material/CloudSync';
import { debounce } from '@mui/material/utils';

import { LockToggle } from '../../components/common/LockableTextField';
import LockableTextField from '../../components/common/LockableTextField';
import LockControlPanel from '../../components/common/LockControlPanel';
import UnifiedVideoSelect from '../../components/common/UnifiedVideoSelect';
import { useVideoForm } from './hooks/useVideoForm';
import VideoBasicTab from './components/VideoBasicTab';
import { useLazyGetGenresPageQuery } from '../genres/genresApi';
import PlaylistManagerDialog from '../playlists/components/PlaylistManagerDialog';
import { useCreatePlaylistMutation, useUpdatePlaylistMutation, useDeletePlaylistMutation } from '../playlists/playlistsApi';
import { useReNormalizeVideoMutation, useReEnrichVideoMutation, useReFetchVideoMutation } from './videosApi';

// 封装组件
const AsyncAutocomplete = memo(function AsyncAutocomplete({
                                                              label, placeholder, multiple = false, value, onChange, fetchHook,
                                                              getOptionLabel = (opt) => opt.name,
                                                              isOptionEqualToValue = (opt, val) => opt.id === val.id,
                                                              renderValue, renderInputEndAdornment
                                                          }) {
    const [open, setOpen] = useState(false);
    const [options, setOptions] = useState([]);
    const [inputValue, setInputValue] = useState('');

    const [triggerFetch, { data, isFetching }] = fetchHook();

    const fetchDebounced = useMemo(() => debounce((input) => {
        triggerFetch({ name: input, keyword: input, page: 0, size: 20, sort: 'id,asc' });
    }, 400), [triggerFetch]);

    useEffect(() => { if (open) fetchDebounced(inputValue); }, [inputValue, open, fetchDebounced]);
    useEffect(() => { if (data?.content) setOptions(data.content); }, [data]);

    return (
        <Autocomplete
            multiple={multiple}
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            onInputChange={(e, val) => setInputValue(val)}
            isOptionEqualToValue={isOptionEqualToValue}
            getOptionLabel={getOptionLabel}
            options={options}
            loading={isFetching}
            value={value}
            onChange={onChange}
            filterOptions={(x) => x}
            renderValue={renderValue ? renderValue : undefined}
            renderInput={(params) => {
                const { InputProps, ...restParams } = params;
                return (
                    <TextField
                        {...restParams} label={label} placeholder={placeholder} variant="outlined"
                        slotProps={{
                            input: {
                                ...InputProps,
                                endAdornment: (
                                    <React.Fragment>
                                        {isFetching ? <CircularProgress color="inherit" size={20} /> : null}
                                        {InputProps.endAdornment}
                                        {renderInputEndAdornment}
                                    </React.Fragment>
                                )
                            }
                        }}
                    />
                );
            }}
        />
    );
});

const FreeTextAutocomplete = memo(function FreeTextAutocomplete({
                                                                 label, placeholder, value, onChange,
                                                                 renderValue, renderInputEndAdornment
                                                             }) {
    return (
        <Autocomplete
            multiple
            freeSolo
            options={[]}
            value={value}
            onChange={onChange}
            renderValue={renderValue ? renderValue : undefined}
            renderInput={(params) => {
                const { InputProps, ...restParams } = params;
                return (
                    <TextField
                        {...restParams}
                        label={label}
                        placeholder={placeholder}
                        variant="outlined"
                        slotProps={{
                            input: {
                                ...InputProps,
                                endAdornment: (
                                    <React.Fragment>
                                        {InputProps.endAdornment}
                                        {renderInputEndAdornment}
                                    </React.Fragment>
                                )
                            }
                        }}
                    />
                );
            }}
        />
    );
});

function TabPanel(props) {
    const { children, value, index, ...other } = props;
    return (
        <div role="tabpanel" hidden={value !== index} {...other}>
            {value === index && <Box sx={{ p: 2 }}>{children}</Box>}
        </div>
    );
}

function FormSkeleton() {
    return (
        <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 4 }}>
                <Skeleton variant="rectangular" height={350} sx={{ borderRadius: 1 }} />
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}>
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}><Skeleton height={56} /></Grid>
                    <Grid size={{ xs: 6 }}><Skeleton height={56} /></Grid>
                    <Grid size={{ xs: 6 }}><Skeleton height={56} /></Grid>
                    <Grid size={{ xs: 6 }}><Skeleton height={56} /></Grid>
                    <Grid size={{ xs: 6 }}><Skeleton height={56} /></Grid>
                    <Grid size={{ xs: 12 }}><Skeleton height={120} /></Grid>
                </Grid>
            </Grid>
        </Grid>
    );
}

function VideoForm({ open, onClose, onSubmit, video, isLoading }) {
    const {
        formData, setFormData, handleChange, handleLockChange, handleBatchLock, isLocked,
        handleSubmit, isSubmitting, tabIndex, setTabIndex, resetForm,
        currentUnifiedVideoValue,
        lockedCount, totalLockableCount
    } = useVideoForm(video, onSubmit);

    useEffect(() => {
        if (open) {
            resetForm();
        }
    }, [open, resetForm]);

    const [selectedPlaylist, setSelectedPlaylist] = useState(null);
    const [isPlaylistDialogOpen, setIsPlaylistDialogOpen] = useState(false);

    const [createPlaylist] = useCreatePlaylistMutation();
    const [updatePlaylist] = useUpdatePlaylistMutation();
    const [deletePlaylist] = useDeletePlaylistMutation();

    const [reNormalize] = useReNormalizeVideoMutation();
    const [reEnrich] = useReEnrichVideoMutation();
    const [reFetch] = useReFetchVideoMutation();
    const [anchorEl, setAnchorEl] = useState(null);

    const handleActionClick = (event) => setAnchorEl(event.currentTarget);
    const handleActionClose = () => setAnchorEl(null);

    const handleManualAction = async (actionType) => {
        handleActionClose();
        if (!formData.id) return;
        try {
            if (actionType === 'normalize') {
                await reNormalize(formData.id).unwrap();
                alert('归一化任务已提交');
            } else if (actionType === 'enrich') {
                await reEnrich(formData.id).unwrap();
                alert('元数据任务已提交');
            } else if (actionType === 'fetch') {
                if (window.confirm('警告：这将重新从源站获取数据并覆盖当前字段！确定继续吗？')) {
                    await reFetch(formData.id).unwrap();
                    alert('源站抓取任务已提交');
                }
            }
        } catch (e) {
            console.error(e);
            alert('操作失败');
        }
    };


    const playlists = formData.playlists || [];

    const handleEditPlaylist = (pl) => {
        setSelectedPlaylist(pl);
        setIsPlaylistDialogOpen(true);
    };

    const handleNewPlaylist = () => {
        setSelectedPlaylist({ name: '新播放列表', episodes: [], videoId: formData.id, videoTitle: formData.title });
        setIsPlaylistDialogOpen(true);
    };

    const handlePlaylistSubmit = async (plData) => {
        try {
            if (plData.id) {
                await updatePlaylist(plData).unwrap();
            } else {
                await createPlaylist({ ...plData, videoId: formData.id }).unwrap();
            }
            setIsPlaylistDialogOpen(false);
        } catch (e) {
            console.error(e);
            alert('保存失败: ' + (e.data?.message || e.message));
        }
    };

    const handleDeletePlaylist = async (id) => {
        if (window.confirm('确定删除此播放列表？')) {
            await deletePlaylist(id).unwrap();
        }
    };

    const handleCopyUrl = () => {
        if (formData.detailApiUrl) {
            navigator.clipboard.writeText(formData.detailApiUrl);
        }
    };

    const handleOpenUrl = () => {
        if (formData.detailApiUrl) {
            window.open(formData.detailApiUrl, '_blank', 'noopener,noreferrer');
        }
    };

    if (!open) return null;

    const renderTagsWithLock = () => (value, getTagProps) =>
        value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
                <Chip
                    key={key}
                    variant="outlined"
                    label={typeof option === 'string' ? option : option.name || option.sourceValue}
                    {...tagProps}
                />
            );
        });

    let prettyMetadata = '';
    try {
        if (formData.rawMetadata) {
            prettyMetadata = JSON.stringify(JSON.parse(formData.rawMetadata), null, 2);
        }
    } catch {
        prettyMetadata = formData.rawMetadata;
    }

    const modalTitle = isLoading
        ? (video?.title ? `加载中: ${video.title}...` : '加载中...')
        : (video ? `编辑视频: ${formData.title}` : '新建视频');

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {modalTitle}
                    {!isLoading && formData.normalizationStatus === 'FAILED' && (
                        <Chip icon={<WarningAmberIcon />} label="归一化失败" color="error" size="small" sx={{ ml: 1 }} />
                    )}
                </Box>
                {!isLoading && formData.id && (
                    <Box>
                        <IconButton onClick={handleActionClick}>
                            <MoreVertIcon />
                        </IconButton>
                        <Menu
                            anchorEl={anchorEl}
                            open={Boolean(anchorEl)}
                            onClose={handleActionClose}
                        >
                            <MenuItem onClick={() => handleManualAction('normalize')}>
                                <CleaningServicesIcon fontSize="small" sx={{ mr: 1 }} /> 重新归一化 (Re-Normalize)
                            </MenuItem>
                            <MenuItem onClick={() => handleManualAction('enrich')}>
                                <AutoFixHighIcon fontSize="small" sx={{ mr: 1 }} /> 重新搜刮元数据 (Re-Enrich)
                            </MenuItem>
                            <Divider />
                            <MenuItem onClick={() => handleManualAction('fetch')} sx={{ color: 'warning.main' }}>
                                <CloudSyncIcon fontSize="small" sx={{ mr: 1 }} /> 从源站重新抓取 (Re-Fetch)
                            </MenuItem>
                        </Menu>
                    </Box>
                )}
            </DialogTitle>

            <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
                <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)}>
                    <Tab label="基本信息" disabled={isLoading} />
                    <Tab label="关联信息" disabled={isLoading} />
                    <Tab label="ID 与元数据" disabled={isLoading} />
                    <Tab label="播放列表" disabled={isLoading} />
                    {!isLoading && formData.rawMetadata && <Tab label="原始数据 (调试)" />}
                </Tabs>
            </Box>

            <DialogContent sx={{ p: 2 }}>
                {isLoading ? (
                    <FormSkeleton />
                ) : (
                    <>
                        <LockControlPanel
                            lockedCount={lockedCount}
                            totalCount={totalLockableCount}
                            onLockAll={() => handleBatchLock(true)}
                            onUnlockAll={() => handleBatchLock(false)}
                        />

                        <TabPanel value={tabIndex} index={0}>
                            <VideoBasicTab
                                formData={formData}
                                handleChange={handleChange}
                                isLocked={isLocked}
                                handleLockChange={handleLockChange}
                            />
                        </TabPanel>

                        <TabPanel value={tabIndex} index={1}>
                            <Grid container spacing={2}>
                                <Grid size={{ xs: 12 }}>
                                    <UnifiedVideoSelect
                                        value={currentUnifiedVideoValue}
                                        onChange={(val) => setFormData(prev => ({
                                            ...prev,
                                            unifiedVideoId: val ? val.id : null,
                                            unifiedVideoTitle: val ? val.title : ''
                                        }))}
                                    />
                                </Grid>

                                <Grid size={{ xs: 12 }}>
                                    <AsyncAutocomplete
                                        label="标准题材"
                                        placeholder="输入搜索题材"
                                        multiple={true}
                            fetchHook={useLazyGetGenresPageQuery}
                                        value={formData.genres}
                                        getOptionLabel={(opt) => opt.name || opt.code || ''}
                                        onChange={(e, val) => setFormData(prev => ({ ...prev, genres: val }))}
                                        renderValue={renderTagsWithLock('genreCodes')}
                                        renderInputEndAdornment={
                                            <LockToggle locked={isLocked('genreCodes')} name="genreCodes" onLockChange={handleLockChange} edge="end" />
                                        }
                                    />
                                </Grid>

                                <Grid size={{ xs: 12 }}>
                                    <FreeTextAutocomplete
                                        label="演员" placeholder="输入搜索演员" multiple={true}
                                        value={formData.actors}
                                        onChange={(e, val) => setFormData(prev => ({ ...prev, actors: val }))}
                                        renderValue={renderTagsWithLock('actors')}
                                        renderInputEndAdornment={
                                            <LockToggle locked={isLocked('actors')} name="actors" onLockChange={handleLockChange} edge="end" />
                                        }
                                    />
                                </Grid>
                                <Grid size={{ xs: 12 }}>
                                    <FreeTextAutocomplete
                                        label="导演" placeholder="输入搜索导演" multiple={true}
                                        value={formData.directors}
                                        onChange={(e, val) => setFormData(prev => ({ ...prev, directors: val }))}
                                        renderValue={renderTagsWithLock('directors')}
                                        renderInputEndAdornment={
                                            <LockToggle locked={isLocked('directors')} name="directors" onLockChange={handleLockChange} edge="end" />
                                        }
                                    />
                                </Grid>
                            </Grid>
                        </TabPanel>

                        <TabPanel value={tabIndex} index={2}>
                            <Grid container spacing={2}>
                                <Grid size={{ xs: 12 }}>
                                    <Typography variant="overline" sx={{
                                        color: "text.secondary"
                                    }}>外部权威 ID</Typography>
                                </Grid>
                                {['doubanId', 'tmdbId', 'imdbId', 'rottenTomatoesId'].map(field => (
                                    <Grid size={{ xs: 6, sm: 3 }} key={field}>
                                        <LockableTextField
                                            name={field} label={field.replace('Id', ' ID').toUpperCase()}
                                            value={formData[field]} onChange={handleChange} fullWidth
                                            locked={isLocked(field)} onLockChange={handleLockChange}
                                        />
                                    </Grid>
                                ))}
                                <Grid size={{ xs: 12 }}><Divider sx={{ my: 1 }} /></Grid>
                                <Grid size={{ xs: 12 }}>
                                    <Typography variant="overline" sx={{
                                        color: "text.secondary"
                                    }}>数据源信息</Typography>
                                </Grid>
                                <Grid size={{ xs: 12, sm: 6 }}>
                                    <TextField
                                        label="数据源名称"
                                        value={formData.dataSourceName}
                                        fullWidth
                                        variant="filled"
                                        slotProps={{ input: { readOnly: true } }}
                                    />
                                </Grid>
                                <Grid size={{ xs: 12, sm: 6 }}>
                                    <TextField
                                        label="源站 VID"
                                        value={formData.sourceVid}
                                        fullWidth
                                        variant="filled"
                                        slotProps={{ input: { readOnly: true } }}
                                    />
                                </Grid>
                                <Grid size={{ xs: 12 }}>
                                    <TextField
                                        label="源站 API 详情链接"
                                        value={formData.detailApiUrl}
                                        fullWidth
                                        variant="outlined"
                                        size="small"
                                        slotProps={{
                                            input: {
                                                readOnly: true,
                                                startAdornment: (
                                                    <InputAdornment position="start">
                                                        <ApiIcon fontSize="small" color="action" />
                                                    </InputAdornment>
                                                ),
                                                endAdornment: formData.detailApiUrl && (
                                                    <InputAdornment position="end">
                                                        <Tooltip title="复制链接">
                                                            <IconButton size="small" onClick={handleCopyUrl}>
                                                                <ContentCopyIcon fontSize="small" />
                                                            </IconButton>
                                                        </Tooltip>
                                                        <Tooltip title="在新标签页打开">
                                                            <IconButton size="small" onClick={handleOpenUrl} color="primary">
                                                                <OpenInNewIcon fontSize="small" />
                                                            </IconButton>
                                                        </Tooltip>
                                                    </InputAdornment>
                                                )
                                            }
                                        }}
                                        helperText="点击右侧按钮可直接访问源站 API 查看原始数据"
                                    />
                                </Grid>
                            </Grid>
                        </TabPanel>

                        <TabPanel value={tabIndex} index={3}>
                            <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <Typography variant="subtitle2" sx={{
                                    color: "text.secondary"
                                }}>
                                    当前共有 {playlists.length} 个播放源
                                </Typography>
                                <Button startIcon={<PlaylistAddIcon />} size="small" variant="outlined" onClick={handleNewPlaylist} disabled={!formData.id}>
                                    添加播放列表
                                </Button>
                            </Box>

                            {!formData.id && (
                                <Alert severity="warning" sx={{ mb: 2 }}>请先保存新建视频，再添加播放列表。</Alert>
                            )}

                            <Grid container spacing={2}>
                                {playlists.map((pl) => (
                                    <Grid size={{ xs: 12 }} key={pl.id}>
                                        <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', bgcolor: 'background.default' }}>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                                <Box sx={{ bgcolor: 'action.hover', p: 1, borderRadius: 1 }}>
                                                    <PlaylistPlayIcon color="primary" />
                                                </Box>
                                                <Box>
                                                    <Typography variant="subtitle1" sx={{
                                                        fontWeight: "bold"
                                                    }}>{pl.name}</Typography>
                                                    <Typography variant="caption" sx={{
                                                        color: "text.secondary"
                                                    }}>
                                                        共 {pl.episodes ? pl.episodes.length : 0} 集
                                                    </Typography>
                                                </Box>
                                            </Box>
                                            <Stack direction="row" spacing={1}>
                                                <Button size="small" onClick={() => handleEditPlaylist(pl)}>
                                                    管理剧集
                                                </Button>
                                                <IconButton size="small" color="error" onClick={() => handleDeletePlaylist(pl.id)}>
                                                    <DeleteIcon fontSize="small" />
                                                </IconButton>
                                            </Stack>
                                        </Paper>
                                    </Grid>
                                ))}
                                {playlists.length === 0 && formData.id && (
                                    <Grid size={{ xs: 12 }}>
                                        <Typography
                                            align="center"
                                            sx={{
                                                color: "text.secondary",
                                                py: 4
                                            }}>
                                            暂无播放列表
                                        </Typography>
                                    </Grid>
                                )}
                            </Grid>
                        </TabPanel>

                        {formData.rawMetadata && (
                            <TabPanel value={tabIndex} index={4}>
                                <Paper variant="outlined" sx={{ bgcolor: '#1e1e1e', p: 2, maxHeight: 400, overflow: 'auto' }}>
                                    <pre style={{ margin: 0, fontSize: '0.85rem', color: '#ffcc80', fontFamily: 'Consolas, monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                        {prettyMetadata}
                                    </pre>
                                </Paper>
                                <Typography
                                    variant="caption"
                                    sx={{
                                        color: "text.secondary",
                                        mt: 1,
                                        display: 'block'
                                    }}>
                                    *此数据是爬虫抓取时暂存的原始JSON片段，归一化服务会尝试将其解析为标准字段。如果归一化失败，请检查此JSON结构是否与数据源映射规则匹配。
                                </Typography>
                            </TabPanel>
                        )}
                    </>
                )}
            </DialogContent>

            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained" disabled={isSubmitting || isLoading}>
                    {isSubmitting ? <CircularProgress size={24} /> : '保存更改'}
                </Button>
            </DialogActions>

            <PlaylistManagerDialog
                open={isPlaylistDialogOpen}
                onClose={() => setIsPlaylistDialogOpen(false)}
                onSubmit={handlePlaylistSubmit}
                playlist={selectedPlaylist}
                videoId={formData.id}
                videoTitle={formData.title}
            />
        </Dialog>
    );
}

export default VideoForm;
