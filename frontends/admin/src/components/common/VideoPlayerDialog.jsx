// File: ircs-frontend/src/components/common/VideoPlayerDialog.jsx
import React, { lazy, Suspense, useState, useEffect, useCallback } from 'react';
import {
    Dialog, DialogContent, DialogTitle,
    Box, Typography, IconButton, Tabs, Tab,
    Alert, Chip, Skeleton, Stack, ToggleButton, ToggleButtonGroup, Tooltip
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import Grid from '@mui/material/Grid';
import OndemandVideoIcon from '@mui/icons-material/OndemandVideo';
import WebIcon from '@mui/icons-material/Web';
import MovieIcon from '@mui/icons-material/Movie';

import { useLazyGetVideoByIdQuery } from '../../features/videos/videosApi';

const LazyVideoPlayer = lazy(() => import('./LazyVideoPlayer.jsx'));

const detectViewMode = (url) => {
    if (!url) return 'PLAYER';
    const lower = url.toLowerCase();
    if (lower.includes('.m3u8') || lower.includes('.mp4') || lower.includes('.webm') || lower.includes('.ogg')) {
        return 'PLAYER';
    }
    return 'IFRAME';
};

const PlaylistSidebar = ({ playlists, currentUrl, onSelectEpisode, loading, tabIndex, onTabChange }) => {
    if (loading) {
        return (
            <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
                <Skeleton variant="rectangular" height={48} sx={{ mb: 2, bgcolor: 'rgba(255,255,255,0.1)' }} />
                <Stack spacing={1}>
                    {[...Array(6)].map((_, i) => (
                        <Skeleton key={i} variant="rounded" height={32} sx={{ bgcolor: 'rgba(255,255,255,0.05)' }} />
                    ))}
                </Stack>
            </Box>
        );
    }

    const currentPlaylist = playlists[tabIndex];

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {playlists.length > 0 && (
                <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                    <Tabs
                        value={tabIndex}
                        onChange={(e, v) => onTabChange(v)}
                        variant="scrollable"
                        scrollButtons="auto"
                        sx={{ minHeight: 48 }}
                    >
                        {playlists.map((pl, idx) => (
                            <Tab key={pl.id || idx} label={pl.name} />
                        ))}
                    </Tabs>
                </Box>
            )}

            <Box sx={{ flex: 1, overflowY: 'auto', p: 1 }}>
                {!currentPlaylist || !currentPlaylist.episodes || currentPlaylist.episodes.length === 0 ? (
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', opacity: 0.5 }}>
                        <OndemandVideoIcon sx={{ fontSize: 40, mb: 1 }} />
                        <Typography variant="caption">暂无播放源</Typography>
                    </Box>
                ) : (
                    <Grid container spacing={1}>
                        {currentPlaylist.episodes.map((ep, idx) => {
                            const isSelected = ep.url === currentUrl;
                            return (
                                <Grid size={{ xs: 4, sm: 3, md: 4, lg: 3 }} key={idx}>
                                    <Chip
                                        label={ep.name}
                                        onClick={() => onSelectEpisode(ep.url)}
                                        color={isSelected ? 'primary' : 'default'}
                                        variant={isSelected ? 'filled' : 'outlined'}
                                        clickable
                                        sx={{
                                            width: '100%',
                                            justifyContent: 'center',
                                            '& .MuiChip-label': {
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                fontSize: '0.75rem'
                                            }
                                        }}
                                    />
                                </Grid>
                            );
                        })}
                    </Grid>
                )}
            </Box>
        </Box>
    );
};

function VideoPlayerDialog({ open, onClose, videoId, videoTitle, type = 'SOURCE' }) {
    const [activeUrl, setActiveUrl] = useState('');
    const [playlists, setPlaylists] = useState([]);
    const [tabIndex, setTabIndex] = useState(0);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [viewMode, setViewMode] = useState('PLAYER');

    const [triggerGetVideo] = useLazyGetVideoByIdQuery();

    useEffect(() => {
        if (open && videoId) {
            setLoading(true);
            setErrorMsg('');
            setActiveUrl('');
            setPlaylists([]);
            setTabIndex(0);

            const fetchData = async () => {
                try {
                    let fetchedPlaylists = [];
                    if (type === 'SOURCE') {
                        const videoData = await triggerGetVideo(videoId).unwrap();
                        if (videoData.playlists && videoData.playlists.length > 0) {
                            fetchedPlaylists = videoData.playlists;
                        }
                    } else {
                        setErrorMsg("暂不支持聚合视频直接预览。");
                        setLoading(false);
                        return;
                    }

                    if (fetchedPlaylists.length > 0) {
                        setPlaylists(fetchedPlaylists);
                        if (fetchedPlaylists[0]?.episodes?.length > 0) {
                            const firstUrl = fetchedPlaylists[0].episodes[0].url;
                            setActiveUrl(firstUrl);
                            setViewMode(detectViewMode(firstUrl));
                        }
                    }
                } catch {
                    setErrorMsg("数据加载失败");
                } finally {
                    setLoading(false);
                }
            };
            fetchData();
        }
    }, [open, videoId, type, triggerGetVideo]);


    const handleEpisodeEnded = useCallback(() => {
        if (viewMode !== 'PLAYER') return;

        const currentPlaylist = playlists[tabIndex];
        if (!currentPlaylist || !currentPlaylist.episodes) return;

        const currentIndex = currentPlaylist.episodes.findIndex(ep => ep.url === activeUrl);

        if (currentIndex !== -1 && currentIndex < currentPlaylist.episodes.length - 1) {
            const nextEpisode = currentPlaylist.episodes[currentIndex + 1];
            setActiveUrl(nextEpisode.url);
            setViewMode(detectViewMode(nextEpisode.url));
        }
    }, [activeUrl, playlists, tabIndex, viewMode]);

    const handleEpisodeSelect = (url) => {
        setActiveUrl(url);
        setViewMode(detectViewMode(url));
    };

    const handleModeChange = (event, newMode) => {
        if (newMode !== null) setViewMode(newMode);
    };

    if (!open) return null;

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth="lg"
            fullWidth
            slotProps={{
                paper: {
                    sx: {
                        height: '80vh',
                        maxHeight: 800,
                        display: 'flex',
                        flexDirection: 'column',
                        bgcolor: '#000',
                        backgroundImage: 'none'
                    }
                }
            }}
        >
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', bgcolor: '#1e1e1e', py: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Typography variant="subtitle1" component="div" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <PlayCircleOutlineIcon color="primary" />
                        {videoTitle}
                    </Typography>

                    {activeUrl && (
                        <ToggleButtonGroup
                            value={viewMode}
                            exclusive
                            onChange={handleModeChange}
                            size="small"
                            sx={{ height: 28 }}
                        >
                            <ToggleButton value="PLAYER">
                                <Tooltip title="流媒体模式">
                                    <MovieIcon fontSize="small" />
                                </Tooltip>
                            </ToggleButton>
                            <ToggleButton value="IFRAME">
                                <Tooltip title="网页模式">
                                    <WebIcon fontSize="small" />
                                </Tooltip>
                            </ToggleButton>
                        </ToggleButtonGroup>
                    )}
                </Box>
                <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, overflow: 'hidden', display: 'flex', flex: 1 }}>
                <Grid container sx={{ height: '100%', width: '100%' }}>
                    <Grid size={{ xs: 12, md: 9 }} sx={{
                        bgcolor: '#000',
                        display: 'block',
                        position: 'relative',
                        height: '100%'
                    }}>
                        {loading ? (
                            <Skeleton variant="rectangular" width="100%" height="100%" sx={{ position: 'absolute' }} />
                        ) : errorMsg ? (
                            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                                <Alert severity="warning" variant="outlined">{errorMsg}</Alert>
                            </Box>
                        ) : activeUrl ? (
                            <Box sx={{ width: '100%', height: '100%' }}>
                                {viewMode === 'PLAYER' ? (
                                    <Suspense fallback={<Skeleton variant="rectangular" width="100%" height="100%" />}>
                                        <LazyVideoPlayer
                                            activeUrl={activeUrl}
                                            videoTitle={videoTitle}
                                            onEnded={handleEpisodeEnded}
                                        />
                                    </Suspense>
                                ) : (
                                    <iframe
                                        src={activeUrl}
                                        title="Video Frame"
                                        width="100%"
                                        height="100%"
                                        allowFullScreen
                                        style={{ backgroundColor: '#000', border: 0 }}
                                    />
                                )}
                            </Box>
                        ) : (
                            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                                <Typography sx={{
                                    color: "text.secondary"
                                }}>请选择播放源</Typography>
                            </Box>
                        )}
                    </Grid>

                    <Grid size={{ xs: 12, md: 3 }} sx={{
                        bgcolor: '#121212',
                        borderLeft: '1px solid rgba(255,255,255,0.1)',
                        display: 'flex',
                        flexDirection: 'column'
                    }}>
                        <PlaylistSidebar
                            playlists={playlists}
                            currentUrl={activeUrl}
                            onSelectEpisode={handleEpisodeSelect}
                            loading={loading}
                            tabIndex={tabIndex}
                            onTabChange={setTabIndex}
                        />
                    </Grid>
                </Grid>
            </DialogContent>
        </Dialog>
    );
}

export default VideoPlayerDialog;
