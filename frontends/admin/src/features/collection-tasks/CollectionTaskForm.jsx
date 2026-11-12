// File: ircs-frontend/src/features/collection-tasks/CollectionTaskForm.jsx
import React, { useEffect } from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Typography,
    Grid,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    TextField,
    InputAdornment
} from '@mui/material';

import { useCollectionTaskForm } from './hooks/useCollectionTaskForm';
import TaskBasicConfig from './components/TaskBasicConfig';
import TaskScheduleConfig from './components/TaskScheduleConfig';
import TaskNetworkConfig from './components/TaskNetworkConfig';

const LOCAL_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai';
const DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const MIN_SAFE_DELAY = 3000;

const emptyTask = {
    name: '',
    enabled: true,
    cronExpression: '0 0 3 * * ?',
    timeZone: LOCAL_TIMEZONE,
    taskType: 'BY_PAGE',
    startPage: 1,
    endPage: 0,
    filterType: '',
    filterHours: '',
    filterKeywords: '',
    duplicateStrategy: 'UPDATE',
    requestDelayType: 'RANDOM',
    fixedDelayMs: MIN_SAFE_DELAY,
    randomDelayMinMs: MIN_SAFE_DELAY,
    randomDelayMaxMs: MIN_SAFE_DELAY * 2,
    timeoutMs: 15000,
    maxRetries: 3,
    userAgent: DEFAULT_USER_AGENT,
    enableRandomUa: true,
    headers: '{}',
    dataSourceId: '',
    useCustomProxy: false,
    proxyType: 'HTTP',
    proxyHost: '',
    proxyPort: '',
    proxyUsername: '',
    proxyPassword: ''
};

function CollectionTaskForm({ open, onClose, onSubmit, task, dataSources = [], isDataSourcesLoading = false }) {
    const {
        formData,
        setFormData,
        errors,
        handleChange,
        handleNumberChange,
        handleHeadersChange,
        handleResetUserAgent,
        handleProxyTypeChange,
        handleSubmit,
        handleAutoPageChange,
        isAutoPage
    } = useCollectionTaskForm(task, onSubmit);

    useEffect(() => {
        if (open && !task) {
            setFormData({ ...emptyTask });
            handleAutoPageChange(true);
        }
    }, [open, task, setFormData, handleAutoPageChange]);

    const isEditMode = !!task?.id;
    const isSelectedDataSourceMissing = formData.dataSourceId && !dataSources.find(ds => ds.id === formData.dataSourceId);

    return (
        <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
            <DialogTitle sx={{ pb: 1 }}>
                {isEditMode ? `编辑任务: ${formData.name}` : '新建采集任务'}
                <Typography variant="body2" sx={{
                    color: "text.secondary"
                }}>
                    配置采集规则、反爬策略及网络参数
                </Typography>
            </DialogTitle>

            <DialogContent dividers>
                <Grid container spacing={3}>
                    <Grid size={{ xs: 12 }}><Typography variant="subtitle2" color="primary">基础配置</Typography></Grid>
                    <Grid size={{ xs: 12 }}>
                        <TaskBasicConfig
                            formData={formData}
                            errors={errors}
                            handleChange={handleChange}
                            dataSources={dataSources}
                            isEditMode={isEditMode}
                            isDataSourcesLoading={isDataSourcesLoading}
                            isSelectedDataSourceMissing={isSelectedDataSourceMissing}
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}><Divider /><Typography variant="subtitle2" color="primary" sx={{ mt: 1 }}>调度与范围</Typography></Grid>
                    <Grid size={{ xs: 12 }}>
                        <TaskScheduleConfig
                            formData={formData}
                            errors={errors}
                            handleChange={handleChange}
                            handleNumberChange={handleNumberChange}
                            isAutoPage={isAutoPage}
                            handleAutoPageChange={handleAutoPageChange}
                        />
                    </Grid>

                    <Grid size={{ xs: 12 }}><Divider /><Typography variant="subtitle2" color="primary" sx={{ mt: 1 }}>执行策略</Typography></Grid>

                    <Grid size={{ xs: 6, md: 6 }}>
                        <TextField
                            name="timeoutMs" type="number" label="请求超时"
                            value={formData.timeoutMs ?? ''} onChange={handleNumberChange} fullWidth
                            slotProps={{ input: { endAdornment: <InputAdornment position="end">ms</InputAdornment> } }}
                        />
                    </Grid>
                    <Grid size={{ xs: 6, md: 6 }}>
                        <TextField
                            name="maxRetries" type="number" label="最大重试"
                            value={formData.maxRetries ?? ''} onChange={handleNumberChange} fullWidth
                            slotProps={{ input: { endAdornment: <InputAdornment position="end">次</InputAdornment> } }}
                        />
                    </Grid>

                    <Grid size={{ xs: 12, md: 4 }}>
                        <FormControl fullWidth>
                            <InputLabel>反爬延时策略</InputLabel>
                            <Select name="requestDelayType" value={formData.requestDelayType} label="反爬延时策略" onChange={handleChange}>
                                <MenuItem value="FIXED">固定延时</MenuItem>
                                <MenuItem value="RANDOM">随机延时 (推荐)</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                    {formData.requestDelayType === 'FIXED' ? (
                        <Grid size={{ xs: 12, md: 8 }}>
                            <TextField
                                name="fixedDelayMs" type="number" label="每次请求间隔"
                                value={formData.fixedDelayMs ?? ''} onChange={handleNumberChange} fullWidth
                                slotProps={{ input: { endAdornment: <InputAdornment position="end">ms</InputAdornment> } }}
                            />
                        </Grid>
                    ) : (
                        <>
                            <Grid size={{ xs: 6, md: 4 }}>
                                <TextField
                                    name="randomDelayMinMs" type="number" label="最小延时"
                                    value={formData.randomDelayMinMs ?? ''} onChange={handleNumberChange} fullWidth
                                    error={!!errors.randomDelayMinMs} helperText={errors.randomDelayMinMs}
                                    slotProps={{ input: { endAdornment: <InputAdornment position="end">ms</InputAdornment> } }}
                                />
                            </Grid>
                            <Grid size={{ xs: 6, md: 4 }}>
                                <TextField
                                    name="randomDelayMaxMs" type="number" label="最大延时"
                                    value={formData.randomDelayMaxMs ?? ''} onChange={handleNumberChange} fullWidth
                                    slotProps={{ input: { endAdornment: <InputAdornment position="end">ms</InputAdornment> } }}
                                />
                            </Grid>
                        </>
                    )}

                    <Grid size={{ xs: 12 }}><Divider /><Typography variant="subtitle2" color="primary" sx={{ mt: 1 }}>网络配置</Typography></Grid>
                    <Grid size={{ xs: 12 }}>
                        <TaskNetworkConfig
                            formData={formData}
                            errors={errors}
                            handleChange={handleChange}
                            handleNumberChange={handleNumberChange}
                            handleHeadersChange={handleHeadersChange}
                            handleResetUserAgent={handleResetUserAgent}
                            handleProxyTypeChange={handleProxyTypeChange}
                        />
                    </Grid>
                </Grid>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained" color="primary">
                    {isEditMode ? '更新任务' : '创建任务'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

export default CollectionTaskForm;
