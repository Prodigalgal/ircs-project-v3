// File: ircs-frontend/src/features/settings/components/ConfigTabPanel.jsx
import React, { useState, useEffect, useMemo } from 'react';
import {
    Box,
    Button,
    Paper,
    Typography,
    Alert,
    CircularProgress,
    Stack,
    Snackbar
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import RefreshIcon from '@mui/icons-material/Refresh';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import ConfigItem from './ConfigItem';
import {
    useUpdateConfigMutation,
    useTestConnectionMutation,
    useGetRestartCapabilitiesQuery,
    useRestartServicesMutation
} from '../systemConfigsApi';

const LLM_PROMPT_KEYS = [
    'app.ai.llm.prompt.language',
    'app.ai.llm.prompt.area',
    'app.ai.llm.prompt.genre',
    'app.ai.llm.prompt.category'
];

function ConfigTabPanel({ group, allConfigs, configMeta = {}, refetch }) {
    const [localConfigs, setLocalConfigs] = useState({});
    const [modifiedKeys, setModifiedKeys] = useState(new Set());
    const [notification, setNotification] = useState({ open: false, message: '', severity: 'success' });

    const [updateConfig, { isLoading: isSaving }] = useUpdateConfigMutation();
    const [testConnection, { isLoading: isTesting }] = useTestConnectionMutation();
    const [requestRestart, { isLoading: isRestarting }] = useRestartServicesMutation();

    const enrichedSettings = useMemo(() => group.settings.map(setting => {
        if (setting.isDivider) return setting;

        const meta = configMeta[setting.key] || {};
        return {
            ...setting,
            activationMode: setting.activationMode || meta.activationMode || 'HOT',
            restartServices: setting.restartServices || meta.restartServices || []
        };
    }), [group.settings, configMeta]);

    const restartRequiredSettings = useMemo(
        () => enrichedSettings.filter(setting => !setting.isDivider && setting.activationMode === 'RESTART_REQUIRED'),
        [enrichedSettings]
    );

    const restartServiceNames = useMemo(() => {
        const names = new Set();
        restartRequiredSettings.forEach(setting => {
            (setting.restartServices || []).forEach(service => names.add(service));
        });
        return Array.from(names).sort();
    }, [restartRequiredSettings]);

    const modifiedRestartSettings = useMemo(
        () => restartRequiredSettings.filter(setting => modifiedKeys.has(setting.key)),
        [modifiedKeys, restartRequiredSettings]
    );

    const { data: restartCapabilities } = useGetRestartCapabilitiesQuery(undefined, {
        skip: restartServiceNames.length === 0
    });

    const restartCapabilityReason = restartCapabilities?.reason || '服务重启能力不可用';
    const restartAllowedServices = useMemo(
        () => new Set(restartCapabilities?.allowedServices || []),
        [restartCapabilities]
    );
    const unsupportedRestartServices = useMemo(
        () => restartServiceNames.filter(service => restartAllowedServices.size > 0 && !restartAllowedServices.has(service)),
        [restartServiceNames, restartAllowedServices]
    );
    const restartCapabilityEnabled = Boolean(restartCapabilities?.enabled)
        && unsupportedRestartServices.length === 0;

    useEffect(() => {
        if (allConfigs) {
            const map = {};
            enrichedSettings.forEach(setting => {
                if (!setting.isDivider) {
                    map[setting.key] = allConfigs[setting.key] ?? '';
                }
            });
            setLocalConfigs(map);
            setModifiedKeys(new Set());
        }
    }, [allConfigs, enrichedSettings]);

    const handleValueChange = (key, newValue) => {
        setLocalConfigs(prev => ({ ...prev, [key]: newValue }));
        setModifiedKeys(prev => new Set(prev).add(key));
    };

    const validateConfig = () => {
        if (group.id === 'mail') {
            const ssl = localConfigs['app.mail.properties.ssl'] === 'true';
            const starttls = localConfigs['app.mail.properties.starttls'] === 'true';

            if (ssl && starttls) {
                return "配置冲突：[SSL 加密] 和 [STARTTLS] 不能同时开启。请根据端口选择其一 (465选SSL, 587选STARTTLS)。";
            }
        }

        if (group.id === 'llm') {
            const invalidPromptKey = LLM_PROMPT_KEYS.find((key) => {
                const value = localConfigs[key] || '';
                return !value.includes('{rawItems}') || !value.includes('{standardItems}');
            });

            if (invalidPromptKey) {
                const setting = enrichedSettings.find(item => item.key === invalidPromptKey);
                return `${setting?.label || invalidPromptKey} 必须同时包含 {rawItems} 和 {standardItems} 占位符。`;
            }
        }

        return null;
    };

    const handleSave = async (e) => {
        if (e) e.preventDefault();

        const errorMsg = validateConfig();
        if (errorMsg) {
            setNotification({ open: true, message: errorMsg, severity: 'error' });
            return;
        }

        let successCount = 0;
        let failCount = 0;

        for (const key of modifiedKeys) {
            try {
                await updateConfig({
                    key: key,
                    value: localConfigs[key],
                    description: 'Updated via System Settings UI'
                }).unwrap();
                successCount++;
            } catch (err) {
                console.error(`Failed to save ${key}`, err);
                failCount++;
            }
        }

        setModifiedKeys(new Set());
        refetch();

        if (failCount > 0) {
            setNotification({ open: true, message: `保存部分完成: ${successCount} 成功, ${failCount} 失败`, severity: 'warning' });
        } else {
            setNotification({ open: true, message: '保存成功', severity: 'success' });
        }
    };

    const handleTest = async () => {
        if (!group.testType) return;

        const errorMsg = validateConfig();
        if (errorMsg) {
            setNotification({ open: true, message: errorMsg, severity: 'error' });
            return;
        }

        const params = {};
        enrichedSettings.forEach(setting => {
            if (!setting.isDivider) {
                params[setting.key] = localConfigs[setting.key] || '';
            }
        });

        try {
            await testConnection({ type: group.testType, params }).unwrap();
            setNotification({ open: true, message: '连接测试成功！配置有效。', severity: 'success' });
        } catch (err) {
            const msg = err.data?.error || err.data?.message || err.error || '连接失败，请检查配置或网络';
            setNotification({ open: true, message: `测试失败: ${msg}`, severity: 'error' });
        }
    };

    const handleReset = () => {
        setModifiedKeys(new Set());
        refetch();
    };

    const handleRestart = async () => {
        if (restartServiceNames.length === 0) return;

        if (modifiedKeys.size > 0) {
            setNotification({ open: true, message: '请先保存当前修改，再重启相关服务。', severity: 'warning' });
            return;
        }

        try {
            const result = await requestRestart({
                services: restartServiceNames,
                reason: `System settings group ${group.id} restart`
            }).unwrap();
            const restartResults = result?.results || [];
            const acceptedCount = restartResults.filter(item => item.accepted).length;
            const rejectedCount = restartResults.length - acceptedCount;

            if (acceptedCount > 0 && rejectedCount === 0) {
                setNotification({ open: true, message: `已提交 ${acceptedCount} 个服务重启请求`, severity: 'success' });
            } else if (acceptedCount > 0) {
                setNotification({ open: true, message: `部分服务已提交重启: ${acceptedCount} 成功, ${rejectedCount} 拒绝`, severity: 'warning' });
            } else {
                setNotification({ open: true, message: '服务重启请求未被接受，请检查重启开关和服务白名单。', severity: 'error' });
            }
        } catch (err) {
            const msg = err.data?.error || err.data?.message || err.error || '服务重启请求失败';
            setNotification({ open: true, message: msg, severity: 'error' });
        }
    };

    return (
        <Paper variant="outlined" sx={{ p: 4, mt: 2, borderRadius: 2 }}>
            <form onSubmit={handleSave} noValidate autoComplete="off">
                <Box sx={{
                    display: 'flex',
                    flexDirection: { xs: 'column', md: 'row' },
                    justifyContent: 'space-between',
                    alignItems: { xs: 'stretch', md: 'center' },
                    gap: 2,
                    mb: 3
                }}>
                    <Box>
                        <Typography variant="h6">{group.title}</Typography>
                        <Typography variant="body2" sx={{
                            color: "text.secondary"
                        }}>
                            {group.description || '修改下方参数以配置此模块'}
                        </Typography>
                    </Box>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                        {group.testType && (
                            <Button
                                variant="outlined"
                                color="info"
                                startIcon={isTesting ? <CircularProgress size={20} /> : <NetworkCheckIcon />}
                                onClick={handleTest}
                                disabled={isTesting || isSaving}
                                type="button"
                            >
                                测试连接
                            </Button>
                        )}
                        {restartServiceNames.length > 0 && (
                            <Button
                                variant="outlined"
                                color="warning"
                                startIcon={isRestarting ? <CircularProgress size={20} /> : <RestartAltIcon />}
                                onClick={handleRestart}
                                disabled={isRestarting || isSaving || isTesting || !restartCapabilityEnabled}
                                type="button"
                            >
                                重启相关服务
                            </Button>
                        )}
                        <Button
                            variant="text"
                            startIcon={<RefreshIcon />}
                            onClick={handleReset}
                            disabled={isSaving}
                            type="button"
                        >
                            重置
                        </Button>
                        <Button
                            variant="contained"
                            startIcon={isSaving ? <CircularProgress size={20} color="inherit" /> : <SaveIcon />}
                            type="submit"
                            disabled={modifiedKeys.size === 0 || isSaving}
                        >
                            {isSaving ? '保存中' : `保存 (${modifiedKeys.size})`}
                        </Button>
                    </Stack>
                </Box>

                {modifiedKeys.size > 0 && (
                    <Alert severity="info" sx={{ mb: 3 }}>
                        本页有 {modifiedKeys.size} 项更改尚未保存。
                    </Alert>
                )}

                {restartRequiredSettings.length > 0 && (
                    <Alert severity={modifiedRestartSettings.length > 0 ? 'warning' : 'info'} sx={{ mb: 3 }}>
                        本组有 {restartRequiredSettings.length} 项配置需要重启后生效。
                        {restartServiceNames.length > 0 && ` 相关服务：${restartServiceNames.join(', ')}`}
                    </Alert>
                )}

                {restartServiceNames.length > 0 && !restartCapabilityEnabled && (
                    <Alert severity="warning" sx={{ mb: 3 }}>
                        服务重启按钮当前不可用：{unsupportedRestartServices.length > 0
                            ? `服务不在白名单：${unsupportedRestartServices.join(', ')}`
                            : restartCapabilityReason}
                    </Alert>
                )}

                <Box sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
                    gap: 3
                }}>
                    {enrichedSettings.map((setting, idx) => (
                        <Box
                            key={setting.key || `sep-${idx}`}
                            sx={setting.isDivider || setting.fullWidth ? { gridColumn: '1 / -1' } : {}}
                        >
                            <ConfigItem
                                setting={setting}
                                value={localConfigs[setting.key]}
                                onChange={handleValueChange}
                            />
                        </Box>
                    ))}
                </Box>
            </form>

            <Snackbar
                open={notification.open}
                autoHideDuration={5000}
                onClose={() => setNotification(prev => ({ ...prev, open: false }))}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                <Alert severity={notification.severity} variant="filled">{notification.message}</Alert>
            </Snackbar>
        </Paper>
    );
}

export default ConfigTabPanel;
