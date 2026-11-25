import React from 'react';
import { Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import ListHeader from '../../components/common/ListHeader';
import UnifiedVideoSelect from '../../components/common/UnifiedVideoSelect';
import MagnetResourcePanel from './MagnetResourcePanel';
import {
    useScrapeUnifiedVideoMagnetsMutation
} from '../unified-videos/unifiedVideosApi';

function formatMagnetResult(result) {
    const status = result?.status || 'UNKNOWN';
    if (status === 'PENDING') {
        return '磁力补链任务已投递，后台 worker 会异步爬取并写入结果。';
    }
    if (status === 'SKIPPED') {
        return `磁力搜刮已跳过：${result?.skippedReason || '该聚合视频没有可用外部 ID 或标题。'}`;
    }
    return `磁力搜刮完成：状态 ${status}，新增/刷新 ${result?.acceptedCount ?? 0} 条资源。`;
}

function MagnetResourceList() {
    const [selectedVideo, setSelectedVideo] = React.useState(null);
    const [scrapeMagnets, { isLoading: isScraping }] = useScrapeUnifiedVideoMagnetsMutation();

    const isReady = !selectedVideo?.metadataStatus || selectedVideo.metadataStatus === 'SYNCED';

    const handleScrape = React.useCallback(async () => {
        if (!selectedVideo?.id) return;
        if (!isReady) {
            alert('该聚合视频尚未完成富化，暂不能补充磁力链接。');
            return;
        }
        if (!window.confirm('确定要对该聚合视频投递磁力补链任务吗？后台 worker 会异步爬取并写入结果。')) {
            return;
        }
        try {
            const result = await scrapeMagnets(selectedVideo.id).unwrap();
            alert(formatMagnetResult(result));
        } catch (error) {
            alert(`磁力搜刮失败: ${error.data?.message || error.message}`);
        }
    }, [isReady, scrapeMagnets, selectedVideo]);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <ListHeader
                title="磁链资源"
                subtitle="按聚合视频集中管理磁链资源、审核状态与补链任务。"
                extraActions={(
                    <Button
                        size="small"
                        variant="contained"
                        startIcon={<TravelExploreIcon />}
                        disabled={!selectedVideo?.id || !isReady || isScraping}
                        onClick={handleScrape}
                    >
                        投递补链任务
                    </Button>
                )}
            />

            <Paper variant="outlined" sx={{ p: 1.5, mb: 1.5 }}>
                <Stack spacing={1.25}>
                    <UnifiedVideoSelect
                        label="选择聚合视频"
                        value={selectedVideo}
                        onChange={setSelectedVideo}
                    />
                    {selectedVideo && (
                        <Stack
                            direction="row"
                            spacing={1}
                            sx={{
                                alignItems: "center",
                                flexWrap: "wrap"
                            }}>
                            <Typography variant="body2" sx={{
                                fontWeight: 600
                            }}>
                                {selectedVideo.title}
                            </Typography>
                            <Chip label={selectedVideo.year || '-'} size="small" variant="outlined" />
                            <Chip
                                label={isReady ? '已富化，可补链' : '待富化'}
                                color={isReady ? 'success' : 'warning'}
                                size="small"
                                variant="outlined"
                            />
                        </Stack>
                    )}
                </Stack>
            </Paper>

            <Box sx={{ flex: 1, overflow: 'auto' }}>
                <MagnetResourcePanel
                    unifiedVideoId={selectedVideo?.id}
                    metadataStatus={selectedVideo?.metadataStatus}
                    onScrape={handleScrape}
                    isScraping={isScraping}
                    title="资源列表"
                />
            </Box>
        </Box>
    );
}

export default MagnetResourceList;
