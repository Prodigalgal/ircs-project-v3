// File: ircs-frontend/src/features/data-sources/wizard/steps/Step2_ListMapping.jsx
import React from 'react';
import { TextField, Typography, Button, CircularProgress, Alert, Paper, Divider } from '@mui/material';
import Grid from '@mui/material/Grid';


import { updateFormData, updateFieldMapping, setSampleJson } from '../dataSourceWizardSlice';
import { useFetchSampleDataMutation } from '../../dataSourcesApi'; // Updated import
import JsonPathSelector from '../components/JsonPathSelector';
import {useDispatch, useSelector} from "react-redux";
import { toSourceNetworkPayload } from '../networkOptions';

function Step2_ListMapping() {
    const dispatch = useDispatch();
    const { formData, sampleJsonResponse, jsonPaths } = useSelector((state) => state.dataSourceWizard);
    const listMapping = formData.fieldMapping.list_mapping;

    const [fetchSample, { isLoading, error }] = useFetchSampleDataMutation();

    const handleChange = (event) => {
        dispatch(updateFormData({ [event.target.name]: event.target.value }));
    };

    const handleMappingChange = (key, mapType = 'list_mapping') => (event) => {
        dispatch(updateFieldMapping({ mapType, key, value: event.target.value }));
    };

    const handlePaginationMappingChange = (key) => (event) => {
        dispatch(updateFieldMapping({ mapType: 'list_mapping_pagination', key, value: event.target.value }));
    };

    const handleFetchSample = async () => {
        const payload = {
            requestType: 'LIST',
            baseUrl: formData.baseUrl,
            listPath: formData.listPath,
            listParams: formData.listParams,
            ...toSourceNetworkPayload(formData),
        };
        // No try-catch needed for global error, but here we might want to locally show if data is fetched or not
        try {
            const result = await fetchSample(payload).unwrap();
            dispatch(setSampleJson({ data: result, type: 'LIST' }));
        } catch {
            // Error handled by middleware
        }
    };

    return (
        <React.Fragment>
            <Grid container spacing={4}>
                <Grid size={{ xs: 12, md: 5 }}>
                    <Typography variant="h6" gutterBottom>配置列表接口</Typography>
                    <TextField required id="listPath" name="listPath" label="列表接口路径 (Path)" fullWidth variant="outlined" value={formData.listPath} onChange={handleChange} margin="normal"/>
                    <TextField id="listParams" name="listParams" label="接口参数 (JSON)" fullWidth multiline rows={3} variant="outlined" value={formData.listParams} onChange={handleChange} margin="normal"/>
                    <Button variant="contained" onClick={handleFetchSample} disabled={isLoading || !formData.baseUrl || !formData.listPath} sx={{ mt: 1 }}>
                        {isLoading ? <CircularProgress size={24} /> : '获取示例数据'}
                    </Button>
                    <Paper variant="outlined" sx={{ p: 2, mt: 2, minHeight: 250, backgroundColor: '#222', overflow: 'auto', maxHeight: '40vh' }}>
                        <Typography variant="subtitle2" sx={{color: 'text.secondary', mb: 1}}>API 响应预览</Typography>
                        {/* We can keep local error display if we want specific context, or rely on global snackbar */}
                        {error && <Alert severity="error">请求失败 (详情见右上角通知)</Alert>}
                        {sampleJsonResponse && <pre style={{ color: '#eee', margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{JSON.stringify(sampleJsonResponse, null, 2)}</pre>}
                    </Paper>
                </Grid>

                <Grid size={{ xs: 12, md: 7 }}>
                    <Typography variant="h6" gutterBottom>列表字段映射</Typography>
                    <JsonPathSelector label="列表数组路径" value={listMapping.items_path} onChange={handleMappingChange('items_path')} jsonPaths={jsonPaths} required />
                    <JsonPathSelector label="视频主键ID路径" value={listMapping.primary_id_path} onChange={handleMappingChange('primary_id_path')} jsonPaths={jsonPaths} required />
                    <JsonPathSelector label="视频标题路径" value={listMapping.title_path} onChange={handleMappingChange('title_path')} jsonPaths={jsonPaths} required />

                    <JsonPathSelector
                        label="更新时间路径"
                        value={listMapping.update_time_path}
                        onChange={handleMappingChange('update_time_path')}
                        jsonPaths={jsonPaths}
                        helperText="用于检测数据新鲜度，实现自动停止采集 (Time Circuit Breaker)"
                    />

                    <Divider sx={{ my: 2 }}><Typography variant="overline">分页信息 (可选)</Typography></Divider>
                    <JsonPathSelector label="总页数路径" value={listMapping.pagination.total_pages_path} onChange={handlePaginationMappingChange('total_pages_path')} jsonPaths={jsonPaths} />
                    <JsonPathSelector label="总记录数路径" value={listMapping.pagination.total_items_path} onChange={handlePaginationMappingChange('total_items_path')} jsonPaths={jsonPaths} />
                </Grid>
            </Grid>
        </React.Fragment>
    );
}

export default Step2_ListMapping;
