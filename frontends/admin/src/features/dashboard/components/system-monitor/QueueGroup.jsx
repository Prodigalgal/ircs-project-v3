import React, { memo, useMemo } from 'react';
import { Box, LinearProgress, Stack, Tooltip as MuiTooltip, Typography } from '@mui/material';
import SpeedIcon from '@mui/icons-material/Speed';
import { blockedReasonLabels, rateKindLabels } from './systemMonitorViewModel';
import { formatNumber, instantRate, stableRate } from './systemMonitorFormatters';

const EMPTY_QUEUES = [];

function queueActive(queue) {
    const rate = queue?.rate || {};
    return stableRate(rate) > 0
        || instantRate(rate) > 0
        || (rate.instantCount || 0) > 0
        || (rate.stableCount || 0) > 0;
}

function siblingQueue(queue, queueIndex) {
    if (!queue?.key || !queueIndex) return null;
    if (queue.key.endsWith('_PENDING')) return queueIndex.get(queue.key.replace(/_PENDING$/, '_INFLIGHT')) || null;
    if (queue.key.endsWith('_INFLIGHT')) return queueIndex.get(queue.key.replace(/_INFLIGHT$/, '_PENDING')) || null;
    return null;
}

const QueueBar = memo(function QueueBar({ queue, isDlqGroup, queueIndex, queueLaneState, laneSummary }) {
    let maxScale = 50;
    if (queue.messageCount > 50) maxScale = 100;
    if (queue.messageCount > 100) maxScale = 500;
    if (queue.messageCount > 500) maxScale = Math.ceil(queue.messageCount / 100) * 100;

    const rate = queue.rate || {};
    const hasRate = queue.rateKind && queue.rateKind !== 'NONE';
    const stableTpm = stableRate(rate);
    const instantTpm = instantRate(rate);
    const rateActive = queueActive(queue);
    const sibling = siblingQueue(queue, queueIndex);
    const siblingActive = queueActive(sibling)
        || ((sibling?.messageCount || 0) > 0 && (sibling?.consumerCount || 0) > 0);

    const inferredNoConsumers = !isDlqGroup
        && queue.messageCount > 0
        && queue.consumerCount === 0
        && !rateActive
        && !siblingActive;
    const blockedReason = queue.blockedReason || (inferredNoConsumers ? 'NO_ACTIVE_CONSUMER' : null);
    const noConsumers = ['NO_CONSUMER_IMPL', 'DISABLED_BY_CONFIG', 'NO_ACTIVE_CONSUMER', 'CONSUMER_STALE', 'UNKNOWN']
        .includes(blockedReason);
    const blockedReasonLabel = blockedReasonLabels[blockedReason] || blockedReasonLabels.UNKNOWN;
    const isProcessing = blockedReason === 'PROCESSING';
    const isStalled = blockedReason === 'CONSUMER_NO_PROGRESS' || blockedReason === 'CONSUMER_PROCESSING_STUCK';
    const lane = queueLaneState(queue);
    const laneText = laneSummary(lane);

    const isWarning = noConsumers || (isDlqGroup && queue.messageCount > 0);
    const isCritical = isStalled;

    return (
        <Box key={queue.key} sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mb: 0.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="caption" sx={{
                        color: (isWarning || isCritical) ? 'error.main' : 'text.secondary',
                        fontWeight: (isWarning || isCritical) ? 'bold' : 'normal',
                        fontSize: '0.8rem'
                    }}>
                        {queue.name}
                    </Typography>
                    {isCritical && (
                        <MuiTooltip title={blockedReason === 'CONSUMER_PROCESSING_STUCK'
                            ? `Worker 长时间停留在当前处理阶段：${laneText || blockedReasonLabel}`
                            : '队列疑似假死：有积压、有消费者，但后端未观察到领取或完成进展'}>
                            <Typography variant="caption" sx={{ bgcolor: 'error.main', color: '#fff', px: 0.5, borderRadius: 0.5, fontSize: '0.65rem' }}>
                                STALLED
                            </Typography>
                        </MuiTooltip>
                    )}
                    {isProcessing && (
                        <MuiTooltip title={`Worker 正在处理：${laneText || blockedReasonLabel}`}>
                            <Typography variant="caption" sx={{ bgcolor: 'success.main', color: '#111', px: 0.5, borderRadius: 0.5, fontSize: '0.65rem', fontWeight: 'bold' }}>
                                处理中
                            </Typography>
                        </MuiTooltip>
                    )}
                    {noConsumers && (
                        <MuiTooltip title={`队列有积压但当前未观察到活跃消费者：${blockedReasonLabel}`}>
                            <Typography variant="caption" sx={{ bgcolor: 'warning.main', color: '#111', px: 0.5, borderRadius: 0.5, fontSize: '0.65rem', fontWeight: 'bold' }}>
                                {blockedReasonLabel}
                            </Typography>
                        </MuiTooltip>
                    )}
                </Box>

                <Stack direction="row" spacing={1.5} sx={{
                    alignItems: "center"
                }}>
                    <MuiTooltip
                        title={hasRate
                            ? `${rate.label || rateKindLabels[queue.rateKind]}：稳定 ${formatNumber(stableTpm)}/分，瞬时 ${formatNumber(instantTpm)}/分，窗口 ${formatNumber(rate.instantCount || 0)}/${rate.instantWindowSeconds || 60}s${laneText ? `；${laneText}` : ''}`
                            : `NONE：该队列没有可确认的事件桶速率${blockedReason ? `；${blockedReasonLabel}` : ''}${laneText ? `；${laneText}` : ''}`}
                    >
                        <Box sx={{ display: 'flex', alignItems: 'center', color: isCritical ? 'error.main' : (hasRate ? 'success.main' : 'text.disabled'), opacity: rateActive || !hasRate ? 1 : 0.5 }}>
                            <SpeedIcon sx={{ fontSize: 14, mr: 0.5 }} />
                            <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
                                {hasRate ? `${rateKindLabels[queue.rateKind]} ${formatNumber(stableTpm)}/m` : 'NONE'}
                            </Typography>
                        </Box>
                    </MuiTooltip>

                    <Typography variant="caption" sx={{
                        color: "text.disabled"
                    }}>|</Typography>

                    <Typography variant="caption" color={(isWarning || isCritical) ? 'error.main' : 'text.primary'} sx={{
                        fontWeight: "bold"
                    }}>
                        {formatNumber(queue.messageCount)} msg
                        <span style={{ fontWeight: 'normal', color: '#888', marginLeft: '4px' }}>
                            / {queue.consumerCount} cons
                        </span>
                    </Typography>
                </Stack>
            </Box>
            <LinearProgress
                variant="determinate"
                value={Math.min(100, (queue.messageCount / maxScale) * 100)}
                sx={{
                    height: 6,
                    borderRadius: 3,
                    bgcolor: 'action.hover',
                    '& .MuiLinearProgress-bar': {
                        bgcolor: isCritical ? 'error.main' : (isWarning ? 'warning.main' : queue.color),
                        transition: 'transform 0.4s linear'
                    }
                }}
            />
        </Box>
    );
});

function QueueGroup({ group, queueLaneState, laneSummary }) {
    const queues = group.queues || EMPTY_QUEUES;
    const queueIndex = useMemo(() => new Map(queues.map(queue => [queue.key, queue])), [queues]);

    return (
        <Box key={group.title} sx={{ mb: 4 }}>
            <Typography
                variant="caption"
                color={group.dlq ? 'error' : 'primary'}
                sx={{ fontWeight: 'bold', display: 'block', mb: 1.5, borderBottom: '1px solid', borderColor: 'divider', pb: 0.5 }}
            >
                {group.title}
            </Typography>
            {queues.map(queue => (
                <QueueBar
                    key={queue.key}
                    queue={queue}
                    isDlqGroup={group.dlq}
                    queueIndex={queueIndex}
                    queueLaneState={queueLaneState}
                    laneSummary={laneSummary}
                />
            ))}
        </Box>
    );
}

export default memo(QueueGroup);
