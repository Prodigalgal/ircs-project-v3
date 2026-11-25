import React, { useEffect, useRef, useState } from 'react';
import {
    Box, Checkbox, IconButton, LinearProgress, Stack, Tooltip, Typography
} from '@mui/material';
import Grid from '@mui/material/Grid';
import VerticalAlignBottomIcon from '@mui/icons-material/VerticalAlignBottom';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import ScraperVideoCard from './ScraperVideoCard';

function ScraperRunningView({ logs, cards, isFinished, onClearLogs }) {
    const [autoScroll, setAutoScroll] = useState(true);
    const logEndRef = useRef(null);

    useEffect(() => {
        if (autoScroll) {
            logEndRef.current?.scrollIntoView({ behavior: "smooth" });
        }
    }, [logs, autoScroll]);

    return (
        <Grid container sx={{ height: '100%' }}>
            {/* 左侧日志栏 */}
            <Grid size={{ xs: 12, md: 4 }} sx={{ borderRight: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', height: '100%' }}>
                <Box
                    sx={(theme) => ({
                        px: 1.5,
                        py: 1,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        bgcolor: theme.palette.surface.consoleHeader,
                        borderBottom: '1px solid',
                        borderColor: theme.palette.surface.consoleBorder,
                    })}
                >
                    <Typography variant="caption" sx={{ color: '#94A3B8', fontWeight: 700 }}>运行日志</Typography>
                    <Stack direction="row" spacing={0.5}>
                        <Tooltip title="自动滚动">
                            <Checkbox
                                icon={<VerticalAlignBottomIcon fontSize="small" sx={{ color: '#94A3B8' }} />}
                                checkedIcon={<VerticalAlignBottomIcon fontSize="small" color="primary" />}
                                checked={autoScroll}
                                onChange={(e) => setAutoScroll(e.target.checked)}
                                size="small"
                            />
                        </Tooltip>
                        <Tooltip title="清空日志">
                            <IconButton size="small" onClick={onClearLogs} sx={{ color: '#94A3B8' }}>
                                <DeleteSweepIcon fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    </Stack>
                </Box>

                <Box
                    sx={(theme) => ({
                        flex: 1,
                        bgcolor: theme.palette.surface.consoleBg,
                        color: theme.palette.surface.consoleText,
                        fontFamily: 'Consolas, monospace',
                        p: 1.5,
                        overflowY: 'auto',
                        fontSize: '0.75rem',
                        lineHeight: 1.6,
                    })}
                >
                    {logs.map((log, i) => (
                        <div key={i} style={{ color: log.type === 'error' ? '#F87171' : '#34D399', marginBottom: 4 }}>
                            <span style={{ color: '#64748B', marginRight: 6 }}>[{new Date(log.time).toLocaleTimeString()}]</span>
                            {log.msg}
                        </div>
                    ))}
                    <div ref={logEndRef} />
                </Box>
                {!isFinished && <LinearProgress sx={{ height: 2 }} />}
            </Grid>

            {/* 右侧结果栏 */}
            <Grid size={{ xs: 12, md: 8 }} sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <Box sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
                    <Typography variant="subtitle2">
                        采集结果 ({cards.length})
                        <Typography component="span" variant="caption" sx={{ ml: 2, color: 'text.secondary' }}>
                            {isFinished ? '任务已结束' : '实时接收中...'}
                        </Typography>
                    </Typography>
                </Box>

                <Box sx={{ flex: 1, overflowY: 'auto', p: 2, bgcolor: 'background.default' }}>
                    <Box sx={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
                        gap: 2
                    }}>
                        {cards.map((card, idx) => (
                            <ScraperVideoCard key={idx} data={card} />
                        ))}
                    </Box>
                    {cards.length === 0 && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 10, opacity: 0.5 }}>
                            <Typography variant="body2">暂无结果，请等待搜索...</Typography>
                        </Box>
                    )}
                </Box>
            </Grid>
        </Grid>
    );
}

export default ScraperRunningView;