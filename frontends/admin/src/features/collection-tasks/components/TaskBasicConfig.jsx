// File: ircs-frontend/src/features/collection-tasks/components/TaskBasicConfig.jsx
import React from 'react';
import { TextField, FormControl, InputLabel, Select, MenuItem, FormHelperText, Grid } from '@mui/material';

function TaskBasicConfig({ formData, errors, handleChange, dataSources, isEditMode, isDataSourcesLoading, isSelectedDataSourceMissing }) {
    return (
        <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                    name="name"
                    label="任务名称"
                    value={formData.name}
                    onChange={handleChange}
                    fullWidth
                    required
                    error={!!errors.name}
                    helperText={errors.name}
                    placeholder="例如：每日全量采集"
                />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
                <FormControl fullWidth required error={!!errors.dataSourceId}>
                    <InputLabel>数据源</InputLabel>
                    <Select
                        name="dataSourceId"
                        value={formData.dataSourceId || ''}
                        label="数据源"
                        onChange={handleChange}
                        disabled={isEditMode || isDataSourcesLoading}
                    >
                        {isDataSourcesLoading && (
                            <MenuItem value="" disabled>加载中...</MenuItem>
                        )}
                        {!isDataSourcesLoading && isSelectedDataSourceMissing && (
                            <MenuItem value={formData.dataSourceId} disabled>
                                <em>未知数据源 (ID: {formData.dataSourceId})</em>
                            </MenuItem>
                        )}
                        {dataSources.map(ds => (
                            <MenuItem key={ds.id} value={ds.id}>{ds.name}</MenuItem>
                        ))}
                    </Select>
                    {errors.dataSourceId && <FormHelperText>{errors.dataSourceId}</FormHelperText>}
                </FormControl>
            </Grid>
        </Grid>
    );
}
export default TaskBasicConfig;