// File: src/features/ops/maintenance/MaintenancePage.jsx
import React, {useMemo, useState} from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    Container,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Fade,
    LinearProgress,
    Paper,
    Tab,
    Tabs,
    Typography
} from '@mui/material';
import Grid from '@mui/material/Grid';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import MergeTypeIcon from '@mui/icons-material/MergeType';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import VisibilityIcon from '@mui/icons-material/Visibility';
import WhatshotIcon from '@mui/icons-material/Whatshot';
import DnsIcon from '@mui/icons-material/Dns';
import BuildIcon from '@mui/icons-material/Build';
import SyncIcon from '@mui/icons-material/Sync';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';

import {useTaskStream} from './hooks/useTaskStream';
import {
    useGetActiveMaintenanceSessionQuery,
    useInitAdultAssessmentBackfillTaskMutation,
    useInitAggregationResetTaskMutation,
    useInitSanitizeTaskMutation,
    useInitUnifiedRecalculateTaskMutation,
    useReindexRawVideosMutation,
    useReindexUnifiedVideosMutation,
    useTriggerTrendSyncMutation
} from './maintenanceApi';
import ConsoleViewer from './components/ConsoleViewer';

// Mapping Backend Task Names to Frontend IDs
const TASK_NAME_MAP = {
    'Sanitize': 'sanitize',
    'Aggregation Reset': 'agg_reset',
    'Unified Recalculate': 'unified_recalc', // Added
    'Aggregation Adult Assessment Backfill': 'adult_assessment_backfill',
    'Search Reindex (RawVideo)': 'reindex_raw',      // Added Mapping
    'Search Reindex (UnifiedVideo)': 'reindex_unified', // Added Mapping
    'aggregation-adult-assessment-backfill': 'adult_assessment_backfill'
};

function TaskCard({title, description, icon, btnLabel, btnColor, onClick, disabled, isRunning}) {
    return (
        <Paper variant="outlined" sx={{p: 3, height: '100%', display: 'flex', flexDirection: 'column'}}>
            <Box sx={{display: 'flex', alignItems: 'center', mb: 2, color: 'text.secondary'}}>
                {icon}
                <Typography
                    variant="subtitle1"
                    sx={{
                        fontWeight: "bold",
                        ml: 1
                    }}>{title}</Typography>
                {isRunning && (
                    <Chip label="运行中" color="success" size="small" sx={{ml: 'auto'}} variant="outlined"/>
                )}
            </Box>
            <Typography
                variant="body2"
                sx={{
                    color: "text.secondary",
                    mb: 3,
                    flex: 1
                }}>
                {description}
            </Typography>
            <Button
                variant={isRunning ? "outlined" : "contained"}
                color={isRunning ? "success" : btnColor}
                onClick={onClick}
                disabled={disabled && !isRunning} // Allow click if running (to resume view)
                startIcon={isRunning ? <VisibilityIcon/> : <PlayArrowIcon/>}
            >
                {isRunning ? "查看实时监控" : btnLabel}
            </Button>
        </Paper>
    );
}

function MaintenancePage() {
    const {status, logs, progress, startStream, disconnect} = useTaskStream();
    const [dialogOpen, setDialogOpen] = useState(false);
    const [tabIndex, setTabIndex] = useState(0);

    // Polling Active Session
    const {data: activeSession} = useGetActiveMaintenanceSessionQuery();

    const [initSanitize] = useInitSanitizeTaskMutation();
    const [initAggReset] = useInitAggregationResetTaskMutation();
    const [initUnifiedRecalc] = useInitUnifiedRecalculateTaskMutation(); // Hook
    const [initAdultAssessmentBackfill] = useInitAdultAssessmentBackfillTaskMutation();
    const [triggerTrendSync, {isLoading: isSyncing}] = useTriggerTrendSyncMutation();

    const [reindexRaw, {isLoading: isReindexingRaw}] = useReindexRawVideosMutation();
    const [reindexUnified, {isLoading: isReindexingUnified}] = useReindexUnifiedVideosMutation();

    const globalRunningId = useMemo(() => {
        if (!activeSession) return null;
        return activeSession.isRunning ? TASK_NAME_MAP[activeSession.taskName] : null;
    }, [activeSession]);

    // Handle session launch or resume
    const launchSession = (sessionId) => {
        setDialogOpen(true);
        startStream(`/api/v1/ops/maintenance/stream/${sessionId}`);
    };

    const handleSanitizeClick = async () => {
        if (globalRunningId === 'sanitize') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (!window.confirm("全量清洗将重新处理所有视频的标题、年份等字段。确定开始吗？")) return;
        try {
            const {sessionId} = await initSanitize().unwrap();
            launchSession(sessionId);
        } catch (e) {
            alert("启动失败: " + (e.data?.message || e.message));
        }
    };

    const handleAggResetClick = async () => {
        if (globalRunningId === 'agg_reset') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (!window.confirm("⚠️ 高风险警告 ⚠️\n\n将删除所有聚合视频并重新生成。确定继续吗？")) return;
        try {
            const {sessionId} = await initAggReset().unwrap();
            launchSession(sessionId);
        } catch (e) {
            alert("启动失败: " + (e.data?.message || e.message));
        }
    };

    const handleUnifiedRecalcClick = async () => {
        if (globalRunningId === 'unified_recalc') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (!window.confirm("确定对所有聚合视频执行【属性重算】吗？\n将根据最新的源数据刷新副标题、状态等字段。已锁定的字段不受影响。")) return;
        try {
            const {sessionId} = await initUnifiedRecalc().unwrap();
            launchSession(sessionId);
        } catch (e) {
            alert("启动失败: " + (e.data?.message || e.message));
        }
    };

    const handleAdultAssessmentBackfillClick = async () => {
        if (globalRunningId === 'adult_assessment_backfill') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (!window.confirm("确定批量回填成人限制评估吗？\n任务只会为聚合视频补充 adultAssessment/adultRestricted 标记，并同步搜索索引，不会删除内容。")) return;
        try {
            const {sessionId} = await initAdultAssessmentBackfill().unwrap();
            launchSession(sessionId);
        } catch (e) {
            alert("启动失败: " + (e.data?.message || e.message));
        }
    };

    const handleTrendSyncClick = async () => {
        if (window.confirm("确定要手动触发热门榜单同步吗？(后台运行)")) {
            try {
                await triggerTrendSync().unwrap();
                alert("已提交后台运行。");
            } catch (e) {
                alert("启动失败: " + (e.data?.message || e.message));
            }
        }
    };

    const handleReindexRawClick = async () => {
        if (globalRunningId === 'reindex_raw') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (window.confirm("确定全量重建 RawVideo 索引？(Direct-Batch 模式)")) {
            try {
                const {sessionId} = await reindexRaw().unwrap();
                launchSession(sessionId);
            } catch (e) {
                alert("启动失败: " + (e.data?.message || e.message));
            }
        }
    };

    const handleReindexUnifiedClick = async () => {
        if (globalRunningId === 'reindex_unified') {
            launchSession(activeSession.sessionId);
            return;
        }
        if (globalRunningId) return;

        if (window.confirm("确定全量重建 UnifiedVideo 索引？(Direct-Batch 模式)")) {
            try {
                const {sessionId} = await reindexUnified().unwrap();
                launchSession(sessionId);
            } catch (e) {
                alert("启动失败: " + (e.data?.message || e.message));
            }
        }
    };

    const handleDialogClose = () => {
        // Just close the window and disconnect SSE, task continues on backend
        disconnect();
        setDialogOpen(false);
    };

    const tasks = [
        {
            id: 'sanitize',
            category: 'CLEAN',
            title: "全量视频清洗 (Re-sanitize)",
            description: "重新应用最新的元数据清洗规则（如标题去噪、年份提取等）。",
            icon: <CleaningServicesIcon fontSize="large" color="info"/>,
            btnLabel: "开始全量清洗",
            btnColor: "info",
            onClick: handleSanitizeClick,
            disabled: !!globalRunningId
        },
        {
            id: 'agg_reset',
            category: 'CORE',
            title: "重置并重新聚合 (Re-Aggregate)",
            description: "清空聚合库，基于当前源数据重新构建聚合关系图。",
            icon: <MergeTypeIcon fontSize="large" color="warning"/>,
            btnLabel: "执行重置聚合",
            btnColor: "warning",
            onClick: handleAggResetClick,
            disabled: !!globalRunningId
        },
        {
            id: 'unified_recalc',
            category: 'CORE',
            title: "聚合属性重算 (Recalculate)",
            description: "批量刷新聚合视频属性（副标题、状态等），从源视频择优回填。",
            icon: <AutoFixHighIcon fontSize="large" color="secondary"/>,
            btnLabel: "开始属性重算",
            btnColor: "secondary",
            onClick: handleUnifiedRecalcClick,
            disabled: !!globalRunningId
        },
        {
            id: 'adult_assessment_backfill',
            category: 'CORE',
            title: "成人限制评估回填",
            description: "按最新规则批量补充 adultAssessment/adultRestricted 标记，并同步 UnifiedVideo 搜索索引。",
            icon: <AutoFixHighIcon fontSize="large" color="secondary"/>,
            btnLabel: "开始评估回填",
            btnColor: "secondary",
            onClick: handleAdultAssessmentBackfillClick,
            disabled: !!globalRunningId
        },
        {
            id: 'trend_sync',
            category: 'SYNC',
            title: "热门榜单同步 (Trend Sync)",
            description: "从 TMDB/豆瓣 获取最新热门列表并抓取缺失资源 (后台运行)。",
            icon: <WhatshotIcon fontSize="large" color="error"/>,
            btnLabel: "同步热门榜单",
            btnColor: "error",
            onClick: handleTrendSyncClick,
            disabled: isSyncing
        },
        {
            id: 'reindex_raw',
            category: 'SEARCH',
            title: "源视频索引全量重建",
            description: "Direct-Batch 模式：快速重建 RawVideo 索引 (后台运行)。",
            icon: <ManageSearchIcon fontSize="large" color="info"/>,
            btnLabel: "重建 Raw 索引",
            btnColor: "info",
            onClick: handleReindexRawClick,
            disabled: isReindexingRaw || !!globalRunningId
        },
        {
            id: 'reindex_unified',
            category: 'SEARCH',
            title: "聚合视频索引全量重建",
            description: "Direct-Batch 模式：快速重建 UnifiedVideo 索引 (后台运行)。",
            icon: <ManageSearchIcon fontSize="large" color="info"/>,
            btnLabel: "重建 Unified 索引",
            btnColor: "info",
            onClick: handleReindexUnifiedClick,
            disabled: isReindexingUnified || !!globalRunningId
        }
    ];

    const filteredTasks = tabIndex === 0
        ? tasks
        : tasks.filter(t => {
            if (tabIndex === 1) return t.category === 'CLEAN';
            if (tabIndex === 2) return t.category === 'CORE';
            if (tabIndex === 3) return t.category === 'SYNC';
            if (tabIndex === 4) return t.category === 'SEARCH';
            return true;
        });

    return (
        <Container maxWidth="lg" sx={{pt: 4, pb: 4, height: '100%', display: 'flex', flexDirection: 'column'}}>
            <Box sx={{mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <Box>
                    <Typography variant="h4" gutterBottom sx={{
                        fontWeight: "bold"
                    }}>
                        系统维护
                    </Typography>
                    <Typography variant="body1" sx={{
                        color: "text.secondary"
                    }}>
                        执行系统级的数据治理、重置与同步任务。
                    </Typography>
                </Box>
                {globalRunningId && (
                    <Fade in={true}>
                        <Alert severity="info" variant="outlined" sx={{alignItems: 'center'}}>
                            有后台任务正在运行中，点击对应卡片可查看进度。
                        </Alert>
                    </Fade>
                )}
            </Box>

            <Box sx={{borderBottom: 1, borderColor: 'divider', mb: 3}}>
                <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)} aria-label="maintenance tabs"
                      variant="scrollable" scrollButtons="auto">
                    <Tab label="全部任务" icon={<DnsIcon/>} iconPosition="start"/>
                    <Tab label="数据清洗" icon={<BuildIcon/>} iconPosition="start"/>
                    <Tab label="核心重置" icon={<MergeTypeIcon/>} iconPosition="start"/>
                    <Tab label="发现与同步" icon={<SyncIcon/>} iconPosition="start"/>
                    <Tab label="搜索索引" icon={<ManageSearchIcon/>} iconPosition="start"/>
                </Tabs>
            </Box>

            <Box sx={{flex: 1, overflowY: 'auto', p: 1}}>
                <Grid container spacing={3}>
                    {filteredTasks.map((task) => (
                        <Grid size={{xs: 12, md: 6}} key={task.id}>
                            <Fade in={true} timeout={500}>
                                <div style={{height: '100%'}}>
                                    <TaskCard
                                        title={task.title}
                                        description={task.description}
                                        icon={task.icon}
                                        btnLabel={task.btnLabel}
                                        btnColor={task.btnColor}
                                        onClick={task.onClick}
                                        disabled={task.disabled}
                                        isRunning={globalRunningId === task.id}
                                    />
                                </div>
                            </Fade>
                        </Grid>
                    ))}
                </Grid>
            </Box>

            {/* 运行监控弹窗 */}
            <Dialog open={dialogOpen} onClose={handleDialogClose} maxWidth="md" fullWidth>
                <DialogTitle>
                    任务执行监控: {activeSession?.taskName || '...'}
                </DialogTitle>
                <DialogContent dividers>
                    <Box sx={{mb: 2}}>
                        <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 0.5}}>
                            <Typography variant="body2" sx={{
                                color: "text.secondary"
                            }}>
                                {status === 'RUNNING' ? '正在处理...' : (status === 'COMPLETED' ? '已完成' : '状态未知')}
                            </Typography>
                            <Typography variant="body2" sx={{
                                fontWeight: "bold"
                            }}>
                                {progress.current} / {progress.total > 0 ? progress.total : '-'}
                            </Typography>
                        </Box>
                        <LinearProgress
                            variant={progress.total > 0 ? "determinate" : "indeterminate"}
                            value={progress.percent}
                            sx={{height: 10, borderRadius: 5}}
                            color={status === 'ERROR' ? 'error' : 'primary'}
                        />
                    </Box>

                    <ConsoleViewer logs={logs} height={400}/>

                    {status === 'COMPLETED' && (
                        <Alert severity="success" sx={{mt: 2}}>任务执行成功。</Alert>
                    )}
                    {status === 'ERROR' && (
                        <Alert severity="error" sx={{mt: 2}}>任务异常中断或会话已过期。</Alert>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleDialogClose} color="primary"
                            variant={status === 'COMPLETED' ? "contained" : "outlined"}>
                        {status === 'RUNNING' ? "关闭窗口 (后台继续)" : "关闭"}
                    </Button>
                </DialogActions>
            </Dialog>
        </Container>
    );
}

export default MaintenancePage;
