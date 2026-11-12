// File: ircs-frontend/src/features/settings/SystemConfigList.jsx
import React, { useState } from 'react';
import {
    Box,
    Typography,
    Tabs,
    Tab,
    Container,
    CircularProgress,
    Button,
    Paper,
    Stack
} from '@mui/material';
import KeyIcon from '@mui/icons-material/Key';

import { CONFIG_GROUPS } from './configGroups';
import { useSystemConfigMaps } from './hooks/useSystemConfigMaps';
import { useGetConfigsQuery } from './systemConfigsApi';
import ConfigTabPanel from './components/ConfigTabPanel';
import ChangePasswordDialog from './components/ChangePasswordDialog';

function SystemConfigList() {
    const [currentTab, setCurrentTab] = useState(0);
    const [isPwdDialogOpen, setIsPwdDialogOpen] = useState(false);

    const { data: configsData, isLoading, refetch } = useGetConfigsQuery({ page: 0, size: 1000 });

    const { configsMap, configMetaMap } = useSystemConfigMaps(configsData);

    const handleTabChange = (event, newValue) => {
        setCurrentTab(newValue);
    };

    return (
        <Box sx={{ height: '100%', overflow: 'auto' }}>
            <Container maxWidth="lg" sx={{ pb: 8, pt: 2 }}>
                <Box sx={{ mb: 4 }}>
                    <Typography variant="h4" gutterBottom sx={{
                        fontWeight: "900"
                    }}>
                        SYSTEM_SETTINGS
                    </Typography>
                    <Typography
                        variant="body1"
                        sx={{
                            color: "text.secondary",
                            fontFamily: 'monospace'
                        }}>
                        Global configurations for IRCS Terminal nodes.
                    </Typography>
                </Box>

                <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
                    <Tabs
                        value={currentTab}
                        onChange={handleTabChange}
                        variant="scrollable"
                        scrollButtons="auto"
                        textColor="primary"
                        indicatorColor="primary"
                    >
                        {CONFIG_GROUPS.map((group) => (
                            <Tab
                                key={group.id}
                                label={group.title}
                                icon={group.icon}
                                iconPosition="start"
                                sx={{
                                    minHeight: 60,
                                    fontWeight: 'bold',
                                    fontSize: '0.85rem'
                                }}
                            />
                        ))}
                    </Tabs>
                </Box>

                {isLoading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
                        <CircularProgress color="inherit" />
                    </Box>
                ) : (
                    <Box>
                        {CONFIG_GROUPS.map((group, index) => (
                            <div key={group.id} role="tabpanel" hidden={currentTab !== index}>
                                {currentTab === index && (
                                    <Stack spacing={3}>
                                        <ConfigTabPanel
                                            group={group}
                                            allConfigs={configsMap}
                                            configMeta={configMetaMap}
                                            refetch={refetch}
                                        />

                                        {/* 仅在安全设置页签额外显示密码修改入口 */}
                                        {group.id === 'security' && (
                                            <Paper variant="outlined" sx={{ p: 4, bgcolor: '#050505', border: '1px solid #333' }}>
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                    <Box>
                                                        <Typography variant="h6" sx={{ color: '#fff' }}>认证管理</Typography>
                                                        <Typography variant="body2" sx={{
                                                            color: "text.secondary"
                                                        }}>
                                                            修改管理员登录密码。操作成功后将执行全局令牌失效逻辑，强制注销所有当前在线会话。
                                                        </Typography>
                                                    </Box>
                                                    <Button
                                                        variant="outlined"
                                                        color="error"
                                                        startIcon={<KeyIcon />}
                                                        onClick={() => setIsPwdDialogOpen(true)}
                                                        sx={{ borderRadius: '2px', borderWidth: '1px', '&:hover': { borderWidth: '1px' } }}
                                                    >
                                                        修改管理密码
                                                    </Button>
                                                </Box>
                                            </Paper>
                                        )}
                                    </Stack>
                                )}
                            </div>
                        ))}
                    </Box>
                )}
            </Container>

            <ChangePasswordDialog
                open={isPwdDialogOpen}
                onClose={() => setIsPwdDialogOpen(false)}
            />
        </Box>
    );
}

export default SystemConfigList;
