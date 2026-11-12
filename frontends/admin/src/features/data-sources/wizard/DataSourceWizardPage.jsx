
// File: ircs-frontend/src/features/data-sources/wizard/DataSourceWizardPage.jsx
import React from 'react';
import { Dialog, DialogTitle, DialogContent, Stepper, Step, StepLabel, Box, Button, CircularProgress } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { nextStep, prevStep, updateFormData, setSaveError } from './dataSourceWizardSlice';
import { useCreateDataSourceMutation, useUpdateDataSourceMutation } from '../dataSourcesApi';
import { JSONPath } from 'jsonpath-plus';

import Step1_BasicInfo from './steps/Step1_BasicInfo';
import Step2_ListMapping from './steps/Step2_ListMapping';
import Step3_DetailMapping from './steps/Step3_DetailMapping';
import Step4_Preview from './steps/Step4_Preview';
import { toSourceNetworkPayload } from './networkOptions';

const steps = ['基本信息', '列表接口映射', '详情接口映射', '预览与保存'];

function getStepContent(step) {
    switch (step) {
        case 1: return <Step1_BasicInfo />;
        case 2: return <Step2_ListMapping />;
        case 3: return <Step3_DetailMapping />;
        case 4: return <Step4_Preview />;
        default: throw new Error('Unknown step');
    }
}

function DataSourceWizardPage({ open, onClose }) {
    const dispatch = useDispatch();
    const { currentStep, formData, sampleJsonResponse } = useSelector((state) => state.dataSourceWizard);

    const [createDataSource, { isLoading: isCreating }] = useCreateDataSourceMutation();
    const [updateDataSource, { isLoading: isUpdating }] = useUpdateDataSourceMutation();
    const isSaving = isCreating || isUpdating;

    const handleNext = () => {
        if (currentStep === 2) {
            const { items_path, primary_id_path } = formData.fieldMapping.list_mapping;

            if (!items_path || !primary_id_path) {
                alert("请完善必填映射配置（列表路径和ID路径）。");
                return;
            }

            if (sampleJsonResponse) {
                try {
                    const idResult = JSONPath({ path: primary_id_path, json: sampleJsonResponse, wrap: true });

                    if (idResult && idResult.length > 0) {
                        const firstId = idResult[0];
                        if (firstId !== undefined && firstId !== null) {
                            dispatch(updateFormData({ sampleId: firstId.toString() }));
                        } else {
                            alert("提取到的 ID 无效 (null/undefined)。请检查路径是否正确。");
                            return;
                        }
                    } else {
                        alert("无法根据配置的路径提取到任何 ID，请检查路径规则。");
                        return;
                    }
                } catch (e) {
                    console.error(e);
                    alert("JSONPath 解析出错: " + e.message);
                    return;
                }
            } else {
                const isEditMode = !!formData.id;

                if (!isEditMode && !formData.sampleId) {
                    alert("新建数据源时，请先点击“获取示例数据”以验证 ID 提取规则是否正确。");
                    return;
                }
            }
        }
        dispatch(nextStep());
    };

    const handleBack = () => dispatch(prevStep());

    const handleSave = async () => {
        const rawMapping = formData.fieldMapping;

        const toRule = (pathValue) => ({ path: pathValue || '' });

        const standardizedMapping = {
            list_mapping: { ...rawMapping.list_mapping },
            detail_mapping: Object.fromEntries(
                Object.entries(rawMapping.detail_mapping).map(([key, value]) => [key, toRule(value)])
            )
        };

        const payload = {
            name: formData.name,
            baseUrl: formData.baseUrl,
            listPath: formData.listPath,
            listParams: formData.listParams,
            detailPath: formData.detailPath,
            detailParams: formData.detailParams,
            ...toSourceNetworkPayload(formData),
            adultRestricted: Boolean(formData.adultRestricted),
            fieldMapping: JSON.stringify(standardizedMapping),
        };

        try {
            if (formData.id) {
                await updateDataSource({ id: formData.id, data: payload }).unwrap();
            } else {
                await createDataSource(payload).unwrap();
            }
            onClose();
        } catch (err) {
            console.error("Save failed:", err);
            dispatch(setSaveError(err.data || { error: err.message || "未知错误" }));
        }
    };

    const handleClose = () => {
        if (window.confirm('确定要关闭向导吗？所有未保存的更改都将丢失。')) onClose();
    };

    const wizardTitle = formData.id ? `编辑数据源 (ID: ${formData.id})` : "接入新数据源";

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xl">
            <DialogTitle>{wizardTitle}</DialogTitle>
            <DialogContent>
                <Stepper activeStep={currentStep - 1} sx={{ pt: 3, pb: 5 }}>
                    {steps.map((label) => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
                </Stepper>
                <Box>
                    {getStepContent(currentStep)}
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 3 }}>
                    <Button disabled={currentStep === 1 || isSaving} onClick={handleBack} sx={{ mr: 1 }}>
                        上一步
                    </Button>

                    {currentStep === steps.length ? (
                        <Button variant="contained" color="primary" onClick={handleSave} disabled={isSaving}>
                            {isSaving ? <CircularProgress size={24} /> : '完成并保存'}
                        </Button>
                    ) : (
                        <Button variant="contained" onClick={handleNext}>
                            下一步
                        </Button>
                    )}
                </Box>
            </DialogContent>
        </Dialog>
    );
}

export default DataSourceWizardPage;
