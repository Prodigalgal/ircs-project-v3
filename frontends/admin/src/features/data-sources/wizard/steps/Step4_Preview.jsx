// File: ircs-frontend/src/features/data-sources/wizard/steps/Step4_Preview.jsx
import React, { useEffect, useMemo } from 'react';
import { Typography, Paper, Alert } from '@mui/material';
import Grid from '@mui/material/Grid';

import { generatePreview } from '../previewGenerator';
import { setPreviewResult } from '../dataSourceWizardSlice';
import {useDispatch, useSelector} from "react-redux";

function Step4_Preview() {
    const dispatch = useDispatch();
    const { formData, sampleJsonResponse, saveError } = useSelector((state) => state.dataSourceWizard);

    const previewResult = useMemo(() => {
        return generatePreview(formData.fieldMapping, sampleJsonResponse);
    }, [formData.fieldMapping, sampleJsonResponse]);

    useEffect(() => {
        dispatch(setPreviewResult(previewResult));
    }, [previewResult, dispatch]);


    return (
        <React.Fragment>
            <Typography variant="h6" gutterBottom>
                第四步：预览与保存
            </Typography>
            <Typography variant="body2" gutterBottom sx={{
                color: "text.secondary"
            }}>
                这是根据您的映射规则和示例数据生成的最终视频对象预览。如果看起来正确，请点击“完成并保存”。
            </Typography>

            {saveError && (
                <Alert severity="error" sx={{ mt: 2 }}>
                    <strong>保存失败:</strong> {saveError.error || JSON.stringify(saveError)}
                </Alert>
            )}

            <Grid container spacing={2} sx={{ mt: 2 }}>
                <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="subtitle1" gutterBottom>映射配置 (摘要)</Typography>
                    <Paper variant="outlined" sx={{ p: 2, backgroundColor: '#222', overflow: 'auto', height: '50vh' }}>
                         <pre style={{ color: '#eee', margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                             {JSON.stringify(formData.fieldMapping, null, 2)}
                         </pre>
                    </Paper>
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                    <Typography variant="subtitle1" gutterBottom>生成的视频对象预览</Typography>
                    <Paper variant="outlined" sx={{ p: 2, backgroundColor: '#222', overflow: 'auto', height: '50vh' }}>
                         <pre style={{ color: '#eee', margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                             {JSON.stringify(previewResult, null, 2)}
                         </pre>
                    </Paper>
                </Grid>
            </Grid>
        </React.Fragment>
    );
}

export default Step4_Preview;