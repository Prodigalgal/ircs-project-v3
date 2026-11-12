import React, { memo } from 'react';
import { Chip, Paper, Stack, Typography } from '@mui/material';
import { formatDateTime, formatNumber } from './systemMonitorFormatters';

function WorkerLaneCard({ title, lane }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, minHeight: 116 }}>
            <Stack
                direction="row"
                sx={{
                    justifyContent: "space-between",
                    alignItems: "center",
                    mb: 1
                }}>
                <Typography variant="subtitle2">{title}</Typography>
                <Chip
                    size="small"
                    color={lane?.running ? 'success' : 'default'}
                    variant={lane?.running ? 'filled' : 'outlined'}
                    label={lane?.running ? '处理中' : (lane?.lastRunState || '未知')}
                />
            </Stack>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    display: 'block'
                }}>
                Stage: {lane?.currentStage || lane?.lastRunState || '-'}
            </Typography>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    display: 'block'
                }}>
                Entity: {lane?.currentEntityId || lane?.currentRawVideoId || lane?.currentTaskId || '-'}
            </Typography>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    display: 'block'
                }}>
                Batch: {lane?.currentBatchSize || 0}
            </Typography>
            <Typography
                variant="caption"
                sx={{
                    color: "text.secondary",
                    display: 'block'
                }}>
                Running: {formatNumber(lane?.runningForSeconds || 0)}s · Progress: {formatDateTime(lane?.lastProgressAt)}
            </Typography>
            {lane?.lastError && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "error.main",
                        display: 'block'
                    }}>
                    {lane.lastError}
                </Typography>
            )}
        </Paper>
    );
}

export default memo(WorkerLaneCard);
