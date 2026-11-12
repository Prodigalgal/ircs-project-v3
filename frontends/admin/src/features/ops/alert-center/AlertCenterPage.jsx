import React, { useMemo, useState } from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    Divider,
    FormControl,
    FormControlLabel,
    Grid,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Switch,
    Tab,
    Tabs,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import HealthAndSafetyIcon from '@mui/icons-material/HealthAndSafety';
import HistoryIcon from '@mui/icons-material/History';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import RuleIcon from '@mui/icons-material/Rule';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

import ListHeader from '../../../components/common/ListHeader';
import SmartTable from '../../../components/common/SmartTable';
import { getTotalElements } from '../../../utils/paginationUtils';
import {
    useGetHealingActionsQuery,
    useGetNotificationDeliveriesQuery,
    useGetNotificationDeliverySummaryQuery,
    useGetOpsAlertEventsQuery,
    useGetOpsAlertIncidentsQuery,
    useGetServiceRestartCapabilitiesForAlertCenterQuery,
    useRunLowRiskHealingMutation,
    useRunServiceRestartHealingMutation,
} from './alertCenterApi';

const SEVERITY_COLORS = {
    INFO: 'info',
    WARNING: 'warning',
    ERROR: 'error',
    CRITICAL: 'error',
};

const INCIDENT_STATUS_COLORS = {
    OPEN: 'warning',
    RECOVERED: 'success',
};

const ACTION_STATUS_COLORS = {
    DRY_RUN: 'info',
    SKIPPED: 'warning',
    EXECUTED: 'success',
    FAILED: 'error',
};

const LOW_RISK_PLAYBOOKS = [
    { value: 'RUNTIME_EXPIRED_INFLIGHT_REQUEUE', label: '运行队列过期领取恢复' },
    { value: 'RUNTIME_DLQ_REQUEUE_ONE', label: '运行队列 DLQ 单条重放' },
    { value: 'RABBIT_DLQ_RETRY_ONE', label: 'Rabbit DLQ 单条重试' },
    { value: 'DASHBOARD_REFRESH', label: '仪表盘快照刷新' },
];

function TabPanel({ value, index, children }) {
    return (
        <Box
            role="tabpanel"
            hidden={value !== index}
            sx={{ flex: 1, minHeight: 0, display: value === index ? 'flex' : 'none', flexDirection: 'column' }}
        >
            {value === index && children}
        </Box>
    );
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function shortText(value, fallback = '-') {
    if (value === null || value === undefined || value === '') return fallback;
    return String(value);
}

function StatusChip({ label, colorMap }) {
    return (
        <Chip
            size="small"
            label={shortText(label)}
            color={colorMap?.[label] || 'default'}
            variant={label === 'CRITICAL' ? 'filled' : 'outlined'}
        />
    );
}

function SummaryCard({ icon, label, value, detail, color = 'primary' }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
            <Stack spacing={1}>
                <Stack
                    direction="row"
                    spacing={1}
                    sx={{
                        alignItems: "center",
                        color: `${color}.main`
                    }}>
                    {icon}
                    <Typography variant="caption">{label}</Typography>
                </Stack>
                <Typography variant="h5" sx={{
                    fontWeight: "bold"
                }}>{value ?? 0}</Typography>
                <Typography variant="caption" sx={{
                    color: "text.secondary"
                }}>{detail}</Typography>
            </Stack>
        </Paper>
    );
}

function ResponsePanel({ title, response }) {
    if (!response) {
        return null;
    }
    return (
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1}>
                <Typography variant="subtitle2">{title}</Typography>
                <Stack direction="row" spacing={1} useFlexGap sx={{
                    flexWrap: "wrap"
                }}>
                    {'status' in response && <StatusChip label={response.status} colorMap={ACTION_STATUS_COLORS} />}
                    {'dryRun' in response && (
                        <Chip size="small" label={response.dryRun ? 'Dry-run' : 'Execute'} color={response.dryRun ? 'info' : 'warning'} />
                    )}
                    {'affected' in response && <Chip size="small" label={`影响 ${response.affected}`} variant="outlined" />}
                    {'accepted' in response && <Chip size="small" label={response.accepted ? '已接受' : '未接受'} color={response.accepted ? 'success' : 'warning'} />}
                    {'recoveryVerified' in response && (
                        <Chip size="small" label={response.recoveryVerified ? '恢复已验证' : '恢复待验证'} variant="outlined" />
                    )}
                </Stack>
                <Typography variant="body2" sx={{
                    color: "text.secondary"
                }}>{response.reason || '-'}</Typography>
            </Stack>
        </Paper>
    );
}

function LowRiskPlaybookPanel() {
    const [form, setForm] = useState({
        playbook: 'RUNTIME_EXPIRED_INFLIGHT_REQUEUE',
        dryRun: true,
        taskType: '',
        queueName: '',
        maxReplayAttempts: 3,
        days: 7,
    });
    const [lastResponse, setLastResponse] = useState(null);
    const [runLowRiskHealing, { isLoading, error }] = useRunLowRiskHealingMutation();

    const update = (field) => (event) => {
        const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
        setForm(prev => ({ ...prev, [field]: value }));
    };

    const run = async () => {
        const payload = {
            playbook: form.playbook,
            dryRun: form.dryRun,
            taskType: form.taskType || undefined,
            queueName: form.queueName || undefined,
            maxReplayAttempts: Number(form.maxReplayAttempts) || undefined,
            days: Number(form.days) || undefined,
        };
        const response = await runLowRiskHealing(payload).unwrap();
        setLastResponse(response);
    };

    return (
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <AutoFixHighIcon color="primary" />
                    <Typography variant="subtitle1">低风险 playbook</Typography>
                </Stack>
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                        <FormControl size="small" fullWidth>
                            <InputLabel>Playbook</InputLabel>
                            <Select label="Playbook" value={form.playbook} onChange={update('playbook')}>
                                {LOW_RISK_PLAYBOOKS.map(item => (
                                    <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <TextField size="small" fullWidth label="Task Type" value={form.taskType} onChange={update('taskType')} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <TextField size="small" fullWidth label="Queue Name" value={form.queueName} onChange={update('queueName')} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <TextField
                            size="small"
                            fullWidth
                            type="number"
                            label="Replay Attempts"
                            value={form.maxReplayAttempts}
                            onChange={update('maxReplayAttempts')}
                        />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <TextField size="small" fullWidth type="number" label="Dashboard Days" value={form.days} onChange={update('days')} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <FormControlLabel control={<Switch checked={form.dryRun} onChange={update('dryRun')} />} label="Dry-run" />
                    </Grid>
                    <Grid size={{ xs: 12, md: 3 }}>
                        <Tooltip title="执行态仍会被后端配置门禁和白名单保护">
                            <span>
                                <Button
                                    fullWidth
                                    variant="contained"
                                    startIcon={<PlayArrowIcon />}
                                    disabled={isLoading}
                                    onClick={run}
                                >
                                    运行
                                </Button>
                            </span>
                        </Tooltip>
                    </Grid>
                </Grid>
                {error && <Alert severity="error" variant="outlined">低风险 playbook 调用失败，请检查 ops-service。</Alert>}
                <ResponsePanel title="最近一次执行结果" response={lastResponse} />
            </Stack>
        </Paper>
    );
}

function ServiceRestartPlaybookPanel() {
    const { data: capabilities } = useGetServiceRestartCapabilitiesForAlertCenterQuery();
    const allowedServices = capabilities?.allowedServices || [];
    const [form, setForm] = useState({
        service: '',
        reason: 'Manual self-healing from alert center',
        dryRun: true,
    });
    const [lastResponse, setLastResponse] = useState(null);
    const [runServiceRestartHealing, { isLoading, error }] = useRunServiceRestartHealingMutation();

    const update = (field) => (event) => {
        const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
        setForm(prev => ({ ...prev, [field]: value }));
    };

    const run = async () => {
        const response = await runServiceRestartHealing({
            service: form.service,
            reason: form.reason || undefined,
            dryRun: form.dryRun,
        }).unwrap();
        setLastResponse(response);
    };

    const canRun = Boolean(form.service) && !isLoading;

    return (
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
                <Stack direction="row" spacing={1} sx={{
                    alignItems: "center"
                }}>
                    <RestartAltIcon color="warning" />
                    <Typography variant="subtitle1">服务级自愈</Typography>
                </Stack>
                {capabilities && !capabilities.enabled && (
                    <Alert severity="info" variant="outlined">
                        服务重启能力当前未启用：{capabilities.reason || '后端开关或 Kubernetes 凭据不可用'}
                    </Alert>
                )}
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, md: 4 }}>
                        <FormControl size="small" fullWidth>
                            <InputLabel>Service</InputLabel>
                            <Select label="Service" value={form.service} onChange={update('service')}>
                                {allowedServices.map(service => (
                                    <MenuItem key={service} value={service}>{service}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid size={{ xs: 12, md: 5 }}>
                        <TextField size="small" fullWidth label="Reason" value={form.reason} onChange={update('reason')} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 1.5 }}>
                        <FormControlLabel control={<Switch checked={form.dryRun} onChange={update('dryRun')} />} label="Dry-run" />
                    </Grid>
                    <Grid size={{ xs: 12, md: 1.5 }}>
                        <Tooltip title="真实重启还会经过后端白名单、冷却窗口和最大尝试次数保护">
                            <span>
                                <Button
                                    fullWidth
                                    variant="outlined"
                                    color="warning"
                                    startIcon={<RestartAltIcon />}
                                    disabled={!canRun}
                                    onClick={run}
                                >
                                    触发
                                </Button>
                            </span>
                        </Tooltip>
                    </Grid>
                </Grid>
                {error && <Alert severity="error" variant="outlined">服务级自愈调用失败，请检查 ops-service。</Alert>}
                <ResponsePanel title="最近一次重启保护结果" response={lastResponse} />
            </Stack>
        </Paper>
    );
}

function AlertCenterPage() {
    const [tab, setTab] = useState(0);
    const [incidentPage, setIncidentPage] = useState(0);
    const [incidentPageSize, setIncidentPageSize] = useState(20);
    const [eventPage, setEventPage] = useState(0);
    const [eventPageSize, setEventPageSize] = useState(20);
    const [actionPage, setActionPage] = useState(0);
    const [actionPageSize, setActionPageSize] = useState(20);
    const [deliveryPage, setDeliveryPage] = useState(0);
    const [deliveryPageSize, setDeliveryPageSize] = useState(20);

    const incidentsQuery = useGetOpsAlertIncidentsQuery(
        { page: incidentPage, size: incidentPageSize },
        { skip: tab !== 0 }
    );
    const eventsQuery = useGetOpsAlertEventsQuery(
        { page: eventPage, size: eventPageSize },
        { skip: tab !== 1 }
    );
    const actionsQuery = useGetHealingActionsQuery(
        { page: actionPage, size: actionPageSize },
        { skip: tab !== 2 }
    );
    const deliveriesQuery = useGetNotificationDeliveriesQuery(
        { page: deliveryPage, size: deliveryPageSize },
        { skip: tab !== 3 }
    );
    const deliverySummaryQuery = useGetNotificationDeliverySummaryQuery();

    const openIncidents = useMemo(
        () => (incidentsQuery.data?.content || []).filter(item => item.status === 'OPEN').length,
        [incidentsQuery.data]
    );

    const incidentColumns = useMemo(() => [
        {
            field: 'severity',
            headerName: '级别',
            width: 90,
            renderCell: ({ value }) => <StatusChip label={value} colorMap={SEVERITY_COLORS} />,
        },
        {
            field: 'status',
            headerName: '状态',
            width: 90,
            renderCell: ({ value }) => <StatusChip label={value} colorMap={INCIDENT_STATUS_COLORS} />,
        },
        {
            field: 'title',
            headerName: '事件标题',
            width: 260,
            renderCell: ({ row }) => (
                <Box>
                    <Typography variant="body2" noWrap>{row.title}</Typography>
                    <Typography variant="caption" noWrap sx={{
                        color: "text.secondary"
                    }}>{row.fingerprint}</Typography>
                </Box>
            ),
        },
        { field: 'resourceName', headerName: '资源', width: 180, valueGetter: (v, row) => shortText(v || row.resourceType) },
        { field: 'occurrenceCount', headerName: '次数', width: 80 },
        { field: 'lastReason', headerName: '最后原因', width: 280, valueGetter: shortText },
        { field: 'lastSeenAt', headerName: '最近出现', width: 180, valueGetter: formatDate },
    ], []);

    const eventColumns = useMemo(() => [
        { field: 'createdAt', headerName: '时间', width: 180, valueGetter: formatDate },
        {
            field: 'severity',
            headerName: '级别',
            width: 90,
            renderCell: ({ value }) => <StatusChip label={value} colorMap={SEVERITY_COLORS} />,
        },
        { field: 'source', headerName: '来源', width: 160, valueGetter: shortText },
        { field: 'eventType', headerName: '类型', width: 180, valueGetter: shortText },
        { field: 'resourceName', headerName: '资源', width: 180, valueGetter: shortText },
        { field: 'summary', headerName: '摘要', width: 360, valueGetter: shortText },
    ], []);

    const actionColumns = useMemo(() => [
        { field: 'createdAt', headerName: '创建时间', width: 180, valueGetter: formatDate },
        {
            field: 'status',
            headerName: '状态',
            width: 100,
            renderCell: ({ value }) => <StatusChip label={value} colorMap={ACTION_STATUS_COLORS} />,
        },
        {
            field: 'dryRun',
            headerName: '模式',
            width: 90,
            renderCell: ({ value }) => <Chip size="small" label={value ? 'Dry-run' : 'Execute'} variant="outlined" />,
        },
        { field: 'policyKey', headerName: '策略', width: 180, valueGetter: shortText },
        { field: 'playbookKey', headerName: 'Playbook', width: 220, valueGetter: shortText },
        { field: 'resultPayload', headerName: '结果证据', width: 360, valueGetter: shortText },
    ], []);

    const deliveryColumns = useMemo(() => [
        { field: 'createdAt', headerName: '时间', width: 180, valueGetter: formatDate },
        {
            field: 'status',
            headerName: '状态',
            width: 90,
            renderCell: ({ value }) => <StatusChip label={value} colorMap={{ SENT: 'success', FAILED: 'error', SKIPPED: 'warning' }} />,
        },
        { field: 'recipient', headerName: '收件人', width: 210, valueGetter: shortText },
        { field: 'subject', headerName: '主题', width: 260, valueGetter: shortText },
        { field: 'templateCode', headerName: '模板', width: 150, valueGetter: shortText },
        { field: 'failureMessage', headerName: '失败原因', width: 280, valueGetter: shortText },
    ], []);

    const currentTabFetching = tab === 0
        ? incidentsQuery.isFetching
        : tab === 1
            ? eventsQuery.isFetching
            : tab === 2
                ? actionsQuery.isFetching
                : tab === 3
                    ? deliveriesQuery.isFetching
                    : false;
    const loading = currentTabFetching || deliverySummaryQuery.isFetching;

    const refreshAll = () => {
        if (tab === 0) incidentsQuery.refetch();
        if (tab === 1) eventsQuery.refetch();
        if (tab === 2) actionsQuery.refetch();
        if (tab === 3) deliveriesQuery.refetch();
        deliverySummaryQuery.refetch();
    };

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <ListHeader
                title="告警与自愈中心"
                subtitle="聚合告警事件、跟踪自愈动作、查看通知通道投递，并支持受控手动 playbook。"
                onRefresh={refreshAll}
                loading={loading}
            />

            <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<WarningAmberIcon fontSize="small" />} label="本页打开事件" value={openIncidents} detail="当前页 OPEN incidents" color="warning" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<RuleIcon fontSize="small" />} label="自愈动作" value={getTotalElements(actionsQuery.data)} detail="按创建时间倒序" color="info" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<NotificationsActiveIcon fontSize="small" />} label="24小时已接受发送" value={deliverySummaryQuery.data?.sentLast24h} detail="notification-worker delivery 层" color="success" />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <SummaryCard icon={<HealthAndSafetyIcon fontSize="small" />} label="24小时失败通知" value={deliverySummaryQuery.data?.failedLast24h} detail="邮件发送历史" color="error" />
                </Grid>
            </Grid>

            <Paper sx={{ mb: 2 }}>
                <Tabs value={tab} onChange={(event, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
                    <Tab icon={<WarningAmberIcon />} label="事件聚合" iconPosition="start" />
                    <Tab icon={<HistoryIcon />} label="事件明细" iconPosition="start" />
                    <Tab icon={<AutoFixHighIcon />} label="自愈动作" iconPosition="start" />
                    <Tab icon={<NotificationsActiveIcon />} label="通知投递" iconPosition="start" />
                    <Tab icon={<PlayArrowIcon />} label="手动 playbook" iconPosition="start" />
                </Tabs>
            </Paper>

            {(incidentsQuery.error || eventsQuery.error || actionsQuery.error || deliveriesQuery.error) && (
                <Alert severity="warning" variant="outlined" sx={{ mb: 2 }}>
                    部分告警中心数据加载失败，已保留其他面板的可用数据。
                </Alert>
            )}

            <TabPanel value={tab} index={0}>
                <SmartTable
                    columns={incidentColumns}
                    data={incidentsQuery.data?.content || []}
                    total={getTotalElements(incidentsQuery.data)}
                    loading={incidentsQuery.isLoading || incidentsQuery.isFetching}
                    page={incidentPage}
                    pageSize={incidentPageSize}
                    onPageChange={setIncidentPage}
                    onPageSizeChange={setIncidentPageSize}
                    noDataMessage="暂无告警事件聚合"
                />
            </TabPanel>

            <TabPanel value={tab} index={1}>
                <SmartTable
                    columns={eventColumns}
                    data={eventsQuery.data?.content || []}
                    total={getTotalElements(eventsQuery.data)}
                    loading={eventsQuery.isLoading || eventsQuery.isFetching}
                    page={eventPage}
                    pageSize={eventPageSize}
                    onPageChange={setEventPage}
                    onPageSizeChange={setEventPageSize}
                    noDataMessage="暂无告警事件"
                />
            </TabPanel>

            <TabPanel value={tab} index={2}>
                <SmartTable
                    columns={actionColumns}
                    data={actionsQuery.data?.content || []}
                    total={getTotalElements(actionsQuery.data)}
                    loading={actionsQuery.isLoading || actionsQuery.isFetching}
                    page={actionPage}
                    pageSize={actionPageSize}
                    onPageChange={setActionPage}
                    onPageSizeChange={setActionPageSize}
                    noDataMessage="暂无自愈动作"
                />
            </TabPanel>

            <TabPanel value={tab} index={3}>
                <Stack spacing={2} sx={{ flex: 1, minHeight: 0 }}>
                    <Alert severity="info" variant="outlined">
                        {deliverySummaryQuery.data?.sentSemantics || 'SENT 表示 notification-worker delivery 层已接受发送意图。'}
                    </Alert>
                    <Box sx={{ flex: 1, minHeight: 0, display: 'flex' }}>
                        <SmartTable
                            columns={deliveryColumns}
                            data={deliveriesQuery.data?.content || []}
                            total={getTotalElements(deliveriesQuery.data)}
                            loading={deliveriesQuery.isLoading || deliveriesQuery.isFetching}
                            page={deliveryPage}
                            pageSize={deliveryPageSize}
                            onPageChange={setDeliveryPage}
                            onPageSizeChange={setDeliveryPageSize}
                            noDataMessage="暂无通知投递记录"
                        />
                    </Box>
                </Stack>
            </TabPanel>

            <TabPanel value={tab} index={4}>
                <Stack spacing={2} sx={{ overflow: 'auto', pr: 1 }}>
                    <Alert severity="info" variant="outlined">
                        手动 playbook 默认以 dry-run 运行；真实执行必须同时通过本页开关、系统参数门禁、服务白名单和后端冷却窗口。
                    </Alert>
                    <LowRiskPlaybookPanel />
                    <Divider />
                    <ServiceRestartPlaybookPanel />
                </Stack>
            </TabPanel>
        </Box>
    );
}

export default AlertCenterPage;
