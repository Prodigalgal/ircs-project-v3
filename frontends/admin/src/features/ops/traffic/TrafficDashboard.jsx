// File: src/features/ops/traffic/TrafficDashboard.jsx
import React from 'react';
import {
    Box,
    Card,
    CardContent,
    Chip,
    Container,
    Grid,
    IconButton,
    LinearProgress,
    Stack,
    Tooltip,
    Typography
} from '@mui/material';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import DnsIcon from '@mui/icons-material/Dns';
import VpnKeyIcon from '@mui/icons-material/VpnKey';

import {useGetTrafficStatusQuery, useResetTrafficSlotMutation} from './trafficApi';
import ListHeader from '../../../components/common/ListHeader';

function TrafficSlotCard({slot, onReset}) {
    const isBlocked = slot.isBlocked;
    const congestionRate = slot.congestionRate || 0;
    const congestionPercent = Math.min(100, congestionRate * 100);
    const isTokenBucket = slot.limiterType === 'TOKEN_BUCKET';
    const remainingText = slot.capacity
        ? `${slot.remainingPermits ?? 0} / ${slot.capacity}`
        : `${slot.remainingPermits ?? 0}`;
    const nextAvailableSeconds = ((slot.nextAvailableInMs || 0) / 1000).toFixed(1);
    const waitingTasks = slot.waitingTasks ?? 0;
    const detailItems = [
        slot.business && `业务: ${slot.business}`,
        slot.target && `目标: ${slot.target}`,
        slot.egressIdentity && `出口: ${slot.egressIdentity}`,
        slot.lastResult && `最近: ${slot.lastResult}`,
        slot.lastObservedAt && `时间: ${new Date(slot.lastObservedAt).toLocaleString()}`
    ].filter(Boolean);

    let color = 'success';
    if (isBlocked) color = 'error';
    else if (congestionRate > 0.8) color = 'warning';

    const handleReset = () => {
        if (window.confirm(`⚠️ 强制重置限流锁：[${slot.label}] \n\n这将清除 Redis 中的计数器，可能导致瞬间并发激增。仅在锁死（死锁）时使用。\n\n确定继续吗？`)) {
            onReset(slot.key);
        }
    };

    return (
        <Card variant="outlined" sx={{height: '100%', borderColor: isBlocked ? 'error.main' : 'divider'}}>
            <CardContent>
                <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2}}>
                    <Box sx={{maxWidth: '85%'}}>
                        <Typography variant="subtitle2" noWrap title={slot.label} sx={{
                            fontWeight: "bold"
                        }}>
                            {slot.label}
                        </Typography>
                        <Typography
                            variant="caption"
                            noWrap
                            title={slot.key}
                            sx={{
                                color: "text.secondary",
                                fontFamily: 'monospace',
                                display: 'block',
                                mt: 0.5
                            }}>
                            {slot.key}
                        </Typography>
                        {detailItems.length > 0 && (
                            <Typography
                                variant="caption"
                                sx={{
                                    color: "text.secondary",
                                    display: 'block',
                                    mt: 0.5
                                }}>
                                {detailItems.join(' · ')}
                            </Typography>
                        )}
                    </Box>
                    {isBlocked &&
                        <Chip label="BLOCKED" color="error" size="small" sx={{fontWeight: 'bold', height: 20}}/>}
                </Box>

                <Stack spacing={1}>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <Typography variant="caption" sx={{
                            color: "text.secondary"
                        }}>拥堵指数</Typography>
                        <Typography variant="caption" color={`${color}.main`} sx={{
                            fontWeight: "bold"
                        }}>
                            {(congestionRate * 100).toFixed(1)}%
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={congestionPercent}
                        color={color}
                        sx={{height: 8, borderRadius: 4, bgcolor: 'action.hover'}}
                    />

                    <Box sx={{display: 'flex', justifyContent: 'space-between', mt: 1}}>
                        <Box>
                            <Typography
                                variant="caption"
                                sx={{
                                    display: "block",
                                    color: "text.secondary"
                                }}>预计等待</Typography>
                            <Typography variant="body2"
                                        sx={{
                                            fontWeight: "500"
                                        }}>{nextAvailableSeconds} s</Typography>
                        </Box>
                        <Box sx={{textAlign: 'right'}}>
                            <Typography
                                variant="caption"
                                sx={{
                                    display: "block",
                                    color: "text.secondary"
                                }}>
                                {isTokenBucket ? '令牌余量' : '积压任务 (估)'}
                            </Typography>
                            <Typography variant="body2" sx={{
                                fontWeight: "500"
                            }}>
                                {isTokenBucket ? remainingText : waitingTasks}
                            </Typography>
                        </Box>
                    </Box>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', mt: 1}}>
                        <Box>
                            <Typography
                                variant="caption"
                                sx={{
                                    display: "block",
                                    color: "text.secondary"
                                }}>最近请求</Typography>
                            <Typography variant="body2" sx={{
                                fontWeight: "500"
                            }}>{slot.totalRequests ?? 0}</Typography>
                        </Box>
                        <Box sx={{textAlign: 'right'}}>
                            <Typography
                                variant="caption"
                                sx={{
                                    display: "block",
                                    color: "text.secondary"
                                }}>允许/等待/拒绝/异常</Typography>
                            <Typography variant="body2" sx={{
                                fontWeight: "500"
                            }}>
                                {(slot.allowedCount ?? 0)} / {(slot.waitingCount ?? 0)} / {(slot.rejectedCount ?? 0)} / {(slot.errorCount ?? 0)}
                            </Typography>
                        </Box>
                    </Box>
                </Stack>

                <Box sx={{mt: 2, display: 'flex', justifyContent: 'flex-end'}}>
                    <Tooltip title="强制清除 Redis 锁 (Reset)">
                        <IconButton size="small" onClick={handleReset} color="warning">
                            <RestartAltIcon fontSize="small"/>
                        </IconButton>
                    </Tooltip>
                </Box>
            </CardContent>
        </Card>
    );
}

function TrafficDashboard() {
    const {data, isLoading, refetch} = useGetTrafficStatusQuery();
    const [resetSlot] = useResetTrafficSlotMutation();

    const handleReset = async (key) => {
        try {
            await resetSlot(key).unwrap();
        } catch (e) {
            alert('重置失败: ' + (e.data?.message || e.message));
        }
    };

    return (
        <Box sx={{height: '100%', overflow: 'auto', display: 'flex', flexDirection: 'column'}}>
            <Container maxWidth="xl" sx={{pt: 3, pb: 6}}>
                <ListHeader
                    title="流量治理监控台 (Traffic Control)"
                    subtitle="实时监控分布式限流器状态。红色代表触发熔断拒绝，橙色代表高负载。"
                    onRefresh={refetch}
                    loading={isLoading}
                />

                {!data && isLoading ? (
                    <LinearProgress/>
                ) : (
                    <Stack spacing={4}>
                        {/* Global Gatekeepers */}
                        <Box>
                            <Stack
                                direction="row"
                                spacing={1}
                                sx={{
                                    alignItems: "center",
                                    mb: 2
                                }}>
                                <DnsIcon color="primary"/>
                                <Typography variant="h6">全局限流器 (Global Gatekeepers)</Typography>
                                <Chip label={data?.globalLimiters?.length || 0} size="small" variant="outlined"/>
                            </Stack>
                            <Grid container spacing={2}>
                                {data?.globalLimiters?.map((slot) => (
                                    <Grid item size={{xs:12 ,sm:6 ,md:4 ,lg:3}} key={slot.key}>
                                        <TrafficSlotCard slot={slot} onReset={handleReset}/>
                                    </Grid>
                                ))}
                            </Grid>
                        </Box>

                        {/* Credential Buckets */}
                        <Box>
                            <Stack
                                direction="row"
                                spacing={1}
                                sx={{
                                    alignItems: "center",
                                    mb: 2
                                }}>
                                <VpnKeyIcon color="secondary"/>
                                <Typography variant="h6">凭证级限流 (Credential Buckets)</Typography>
                                <Chip label={data?.credentialLimiters?.length || 0} size="small" variant="outlined"/>
                            </Stack>
                            <Grid container spacing={2}>
                                {data?.credentialLimiters?.map((slot) => (
                                    <Grid item size={{xs:12 ,sm:6 ,md:4 ,lg:3}} key={slot.key}>
                                        <TrafficSlotCard slot={slot} onReset={handleReset}/>
                                    </Grid>
                                ))}
                            </Grid>
                        </Box>
                    </Stack>
                )}
            </Container>
        </Box>
    );
}

export default TrafficDashboard;
