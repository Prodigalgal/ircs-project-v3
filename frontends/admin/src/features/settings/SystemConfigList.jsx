// File: frontends/admin/src/features/settings/SystemConfigList.jsx
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
    Stack,
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
            <Container maxWidth="lg" sx={{ px: { xs: 0, sm: 1 }, pb: 6, pt: 0.5 }}>
                <Box sx={{ mb: 2.5 }}>
                    <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.015em' }}>
                        系统参数设置
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.35 }}>
                        全局运行参数、调度策略、AI 引擎与安全认证配置
                    </Typography>
                </Box>

                <Paper variant="outlined" sx={{ mb: 2.5, px: 1 }}>
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
                                sx={{ minHeight: 48 }}
                            />
                        ))}
                    </Tabs>
                </Paper>

                {isLoading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
                        <CircularProgress size={28} />
                    </Box>
                ) : (
                    <Box>
                        {CONFIG_GROUPS.map((group, index) => (
                            <div key={group.id} role="tabpanel" hidden={currentTab !== index}>
                                {currentTab === index && (
                                    <Stack spacing={2.5}>
                                        <ConfigTabPanel
                                            group={group}
                                            allConfigs={configsMap}
                                            configMeta={configMetaMap}
                                            refetch={refetch}
                                        />

                                        {/* 仅在安全设置页签额外显示密码修改入口 */}
                                        {group.id === 'security' && (
                                            <Paper variant="outlined" sx={{ p: 3, bgcolor: 'background.paper' }}>
                                                <Box
                                                    sx={{
                                                        display: 'flex',
                                                        flexWrap: 'wrap',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        gap: 2,
                                                    }}
                                                >
                                                    <Box>
                                                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                                                            管理员认证安全
                                                        </Typography>
                                                        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
                                                            修改管理员登录密码。操作成功后将执行全局令牌失效逻辑，强制注销所有当前在线会话。
                                                        </Typography>
                                                    </Box>
                                                    <Button
                                                        variant="outlined"
                                                        color="error"
                                                        startIcon={<KeyIcon />}
                                                        onClick={() => setIsPwdDialogOpen(true)}
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
