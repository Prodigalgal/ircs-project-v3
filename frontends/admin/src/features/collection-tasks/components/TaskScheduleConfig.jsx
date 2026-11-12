// File: ircs-frontend/src/features/collection-tasks/components/TaskScheduleConfig.jsx
import React, { useMemo } from 'react';
import {
    TextField,
    FormControlLabel,
    Switch,
    InputAdornment,
    Grid,
    Box,
    Typography,
    CircularProgress,
    Checkbox,
    Tooltip,
    Autocomplete
} from '@mui/material';
import AutoModeIcon from '@mui/icons-material/AutoMode';
import { useGetTimezonesQuery } from '../../common/commonApi.js';

function TaskScheduleConfig({
                                formData,
                                errors,
                                handleChange,
                                handleNumberChange,
                                isAutoPage,
                                handleAutoPageChange
                            }) {

    const { data: timezoneList = [], isLoading: isTimezonesLoading } = useGetTimezonesQuery();

    const timezones = useMemo(() => {
        if (!timezoneList.length) return [];
        const commons = ['Asia/Shanghai', 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo'];
        const list = [...timezoneList];
        commons.forEach(c => {
            const idx = list.indexOf(c);
            if(idx > -1) {
                list.splice(idx, 1);
                list.unshift(c);
            }
        });
        return list;
    }, [timezoneList]);

    return (
        <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                    name="cronExpression"
                    label="CRON 表达式"
                    value={formData.cronExpression || ''}
                    onChange={handleChange}
                    fullWidth
                    helperText="示例: 0 0 3 * * ? (每天凌晨3点)"
                    placeholder="Seconds Minutes Hours DayofMonth Month DayofWeek"
                />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
                <Autocomplete
                    options={timezones}
                    value={formData.timeZone || 'UTC'}
                    onChange={(e, newValue) => {
                        handleChange({ target: { name: 'timeZone', value: newValue } });
                    }}
                    loading={isTimezonesLoading}
                    disableClearable
                    renderInput={(params) => (
                        <TextField
                            {...params}
                            label="执行时区"
                            error={!!errors.timeZone}
                            helperText={errors.timeZone || "调度引擎将按照此基准计算执行时刻"}
                            slotProps={{
                                ...params.slotProps,

                                input: {
                                    ...params.slotProps.input,
                                    endAdornment: (
                                        <React.Fragment>
                                            {isTimezonesLoading ? <CircularProgress color="inherit" size={20} /> : null}
                                            {params.slotProps.input.endAdornment}
                                        </React.Fragment>
                                    )
                                }
                            }}
                        />
                    )}
                />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }} sx={{ display: 'flex', alignItems: 'center' }}>
                <FormControlLabel
                    control={<Switch name="enabled" checked={formData.enabled} onChange={handleChange} color="success" />}
                    label="启用"
                />
            </Grid>

            <Grid size={{ xs: 6, md: 4 }}>
                <TextField
                    name="startPage" type="number" label="起始页码"
                    value={formData.startPage ?? ''} onChange={handleNumberChange} fullWidth
                    error={!!errors.startPage} helperText={errors.startPage}
                    slotProps={{ input: { endAdornment: <InputAdornment position="end">页</InputAdornment> } }}
                />
            </Grid>

            <Grid size={{ xs: 6, md: 4 }}>
                <TextField
                    name="endPage"
                    type="number"
                    label="结束页码"
                    value={isAutoPage ? '' : (formData.endPage ?? '')}
                    onChange={handleNumberChange}
                    fullWidth
                    disabled={isAutoPage}
                    error={!!errors.endPage}
                    helperText={isAutoPage ? "自动检测最后一页" : errors.endPage}
                    placeholder={isAutoPage ? "Auto" : ""}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position="end">页</InputAdornment>,
                            sx: isAutoPage ? { backgroundColor: 'action.hover' } : {}
                        }
                    }}
                />
            </Grid>

            <Grid size={{ xs: 12, md: 4 }} sx={{ display: 'flex', alignItems: 'center' }}>
                <Tooltip title="勾选后，任务将自动从 API 获取总页数并采集到最后一页">
                    <FormControlLabel
                        control={
                            <Checkbox
                                checked={isAutoPage}
                                onChange={(e) => handleAutoPageChange(e.target.checked)}
                                color="primary"
                                icon={<AutoModeIcon />}
                                checkedIcon={<AutoModeIcon color="primary" />}
                            />
                        }
                        label="自动全量 (Auto Detect)"
                        sx={{ ml: 1 }}
                    />
                </Tooltip>
            </Grid>

            <Grid size={{ xs: 12 }}>
                <Box sx={{ bgcolor: 'action.hover', p: 2, borderRadius: 1 }}>
                    <Typography variant="body2" gutterBottom sx={{
                        color: "text.secondary"
                    }}>
                        高级过滤参数 (API Filter)
                    </Typography>
                    <Grid container spacing={2}>
                        <Grid size={{ xs: 4 }}>
                            <TextField
                                name="filterType"
                                label="分类过滤 (t)"
                                value={formData.filterType || ''}
                                onChange={handleChange}
                                fullWidth
                                size="small"
                                placeholder="源站分类 code 或关键字"
                                helperText="直接传给源站 API，不再依赖本地源分类字典"
                            />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField
                                name="filterHours"
                                label="最近更新 (h)"
                                type="number"
                                value={formData.filterHours ?? ''}
                                onChange={handleNumberChange}
                                fullWidth
                                size="small"
                                placeholder="例如: 24"
                                slotProps={{ input: { endAdornment: <InputAdornment position="end">小时</InputAdornment> } }}
                                helperText="增量采集最近N小时"
                            />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField
                                name="filterKeywords"
                                label="搜索关键字 (wd)"
                                value={formData.filterKeywords || ''}
                                onChange={handleChange}
                                fullWidth
                                size="small"
                                placeholder="例如: 复仇者"
                                helperText="按名称搜索"
                            />
                        </Grid>
                    </Grid>
                </Box>
            </Grid>
        </Grid>
    );
}

export default TaskScheduleConfig;
