// File: ircs-frontend/src/components/Layout/AppLayout.jsx
import React, { lazy, Suspense, useState } from 'react';
import {
    AppBar, Box, CssBaseline, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography, Divider, Collapse, Button, GlobalStyles, IconButton, Tooltip
} from '@mui/material';
import {
    Dns, Storage, Movie, Dashboard as DashboardIcon, Category, People, ExpandLess, ExpandMore, Settings, Tune, ViewModule, FeaturedPlayList, Link as LinkIcon, Search as SearchIcon, Image as ImageIcon, PlayCircle, Style, Logout as LogoutIcon, Warning as WarningIcon
} from '@mui/icons-material';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import TranslateIcon from '@mui/icons-material/Translate';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SupervisorAccountIcon from '@mui/icons-material/SupervisorAccount';
import EngineeringIcon from '@mui/icons-material/Engineering';
import PublicIcon from '@mui/icons-material/Public';
import FeedbackIcon from '@mui/icons-material/Feedback';
import MovieCreationIcon from '@mui/icons-material/MovieCreation';
import KeyIcon from '@mui/icons-material/Key';
import TrafficIcon from '@mui/icons-material/Traffic'; // Added Icon
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import CrisisAlertIcon from '@mui/icons-material/CrisisAlert';

const drawerWidth = 240;
const ManualSearchDialog = lazy(() => import('../../features/scraper/ManualSearchDialog'));

const scrollbarStyles = {
    '*::-webkit-scrollbar': {
        width: '8px',
        height: '8px',
    },
    '*::-webkit-scrollbar-track': {
        backgroundColor: '#1e1e1e',
        borderRadius: '4px',
    },
    '*::-webkit-scrollbar-thumb': {
        backgroundColor: '#555',
        borderRadius: '4px',
        border: '2px solid #1e1e1e',
    },
    '*::-webkit-scrollbar-thumb:hover': {
        backgroundColor: '#777',
    },
    '*::-webkit-scrollbar-corner': {
        backgroundColor: '#1e1e1e',
    },
};

function AppLayout() {
    const navigate = useNavigate();
    const [contentOpen, setContentOpen] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [opsOpen, setOpsOpen] = useState(false);
    const [manualSearchOpen, setManualSearchOpen] = useState(false);
    const [manualSearchMounted, setManualSearchMounted] = useState(false);
    const [memberOpen, setMemberOpen] = useState(false);

    const handleOpenManualSearch = () => {
        setManualSearchMounted(true);
        setManualSearchOpen(true);
    };

    const handleLogout = () => {
        if (window.confirm('确定要退出系统吗？')) {
            localStorage.removeItem('ircs_token');
            navigate('/login');
        }
    };

    const navItems = [
        { text: '仪表盘', icon: <DashboardIcon />, path: '/' },
        { text: '数据源管理', icon: <Dns />, path: '/data-sources' },
        { text: '采集任务', icon: <Storage />, path: '/collection-tasks' },
        { text: '源视频库', icon: <Movie />, path: '/videos' },
    ];

    const contentManagementItems = [
        { text: '聚合视频', icon: <ViewModule />, path: '/content/unified-videos' },
        { text: '磁链资源', icon: <TravelExploreIcon />, path: '/content/magnets' },
        { text: '播放列表', icon: <FeaturedPlayList />, path: '/content/playlists' },
        { text: '图片管理', icon: <ImageIcon />, path: '/content/images' },
        { text: '分类管理', icon: <Category />, path: '/content/categories' },
        { text: '题材管理', icon: <Style />, path: '/content/genres' },
        { text: '解析源管理', icon: <PlayCircle />, path: '/content/resolvers' },
    ];

    const memberManagementItems = [
        { text: '会员管理', icon: <SupervisorAccountIcon />, path: '/settings/members' },
        { text: '留言反馈', icon: <FeedbackIcon />, path: '/settings/messages' },
        { text: '求片任务', icon: <MovieCreationIcon />, path: '/settings/media-requests' },
    ];

    const settingsItems = [
        { text: '系统参数', icon: <Tune />, path: '/settings/system' },
        { text: 'API Token', icon: <VpnKeyIcon />, path: '/settings/api-tokens' },
        { text: '凭证管理', icon: <KeyIcon />, path: '/settings/credentials' },
        { text: '域名管理', icon: <LinkIcon />, path: '/settings/domains' },
        { text: '语言管理', icon: <TranslateIcon />, path: '/settings/languages' },
        { text: '地区管理', icon: <PublicIcon />, path: '/settings/areas' },
    ];

    const opsItems = [
        { text: '流量治理', icon: <TrafficIcon />, path: '/ops/traffic' }, // New Item
        { text: '告警与自愈', icon: <CrisisAlertIcon />, path: '/ops/alert-center' },
        { text: '死信队列', icon: <WarningIcon />, path: '/ops/dlq' },
        { text: '请求审计', icon: <ReceiptLongIcon />, path: '/ops/request-audit' },
        { text: '系统维护', icon: <EngineeringIcon />, path: '/ops/maintenance' }
    ];

    return (
        <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
            <CssBaseline />
            <GlobalStyles styles={scrollbarStyles} />
            <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
                <Toolbar variant="dense" sx={{ minHeight: 48 }}>
                    <Typography variant="subtitle1" noWrap component="div" sx={{ flexGrow: 1, fontWeight: 700 }}>
                        “希望”智能资源采集系统 (Project Hope)
                    </Typography>

                    <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button
                            color="inherit"
                            variant="outlined"
                            startIcon={<SearchIcon />}
                            onClick={handleOpenManualSearch}
                            size="small"
                            sx={{ borderColor: 'rgba(255,255,255,0.3)', mr: 2 }}
                        >
                            全网搜索采集
                        </Button>

                        <Tooltip title="退出登录">
                            <IconButton color="inherit" onClick={handleLogout}>
                                <LogoutIcon />
                            </IconButton>
                        </Tooltip>
                    </Box>
                </Toolbar>
            </AppBar>
            <Drawer
                variant="permanent"
                sx={{
                    width: drawerWidth,
                    flexShrink: 0,
                    '& .MuiDrawer-paper': {
                        width: drawerWidth,
                        boxSizing: 'border-box',
                        '& .MuiList-root': {
                            py: 0.25
                        },
                        '& .MuiListItemButton-root': {
                            minHeight: 36,
                            py: 0.45,
                            px: 1.75
                        },
                        '& .MuiListItemIcon-root': {
                            minWidth: 36,
                            '& svg': {
                                fontSize: 20
                            }
                        },
                        '& .MuiListItemText-primary': {
                            fontSize: '0.82rem',
                            fontWeight: 500,
                            lineHeight: 1.25
                        }
                    }
                }}
            >
                <Toolbar />
                <Box sx={{ overflow: 'auto' }}>
                    <List>
                        {navItems.map((item) => (
                            <ListItem key={item.text} disablePadding>
                                <ListItemButton component={Link} to={item.path}>
                                    <ListItemIcon>{item.icon}</ListItemIcon>
                                    <ListItemText primary={item.text} />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                    <Divider />

                    <List>
                        <ListItemButton onClick={() => setContentOpen(!contentOpen)}>
                            <ListItemIcon><Movie /></ListItemIcon>
                            <ListItemText primary="内容管理" />
                            {contentOpen ? <ExpandLess /> : <ExpandMore />}
                        </ListItemButton>
                        <Collapse in={contentOpen} timeout="auto" unmountOnExit>
                            <List component="div" disablePadding>
                                {contentManagementItems.map((item) => (
                                    <ListItem key={item.text} disablePadding sx={{ pl: 4 }}>
                                        <ListItemButton component={Link} to={item.path}>
                                            <ListItemIcon>{item.icon}</ListItemIcon>
                                            <ListItemText primary={item.text} />
                                        </ListItemButton>
                                    </ListItem>
                                ))}
                            </List>
                        </Collapse>
                    </List>

                    <Divider />

                    <List>
                        <ListItemButton onClick={() => setMemberOpen(!memberOpen)}>
                            <ListItemIcon><People /></ListItemIcon>
                            <ListItemText primary="会员与互动" />
                            {memberOpen ? <ExpandLess /> : <ExpandMore />}
                        </ListItemButton>
                        <Collapse in={memberOpen} timeout="auto" unmountOnExit>
                            <List component="div" disablePadding>
                                {memberManagementItems.map((item) => (
                                    <ListItem key={item.text} disablePadding sx={{ pl: 4 }}>
                                        <ListItemButton component={Link} to={item.path}>
                                            <ListItemIcon>{item.icon}</ListItemIcon>
                                            <ListItemText primary={item.text} />
                                        </ListItemButton>
                                    </ListItem>
                                ))}
                            </List>
                        </Collapse>
                    </List>

                    <Divider />

                    <List>
                        <ListItemButton onClick={() => setOpsOpen(!opsOpen)}>
                            <ListItemIcon><MonitorHeartIcon /></ListItemIcon>
                            <ListItemText primary="运维监控" />
                            {opsOpen ? <ExpandLess /> : <ExpandMore />}
                        </ListItemButton>
                        <Collapse in={opsOpen} timeout="auto" unmountOnExit>
                            <List component="div" disablePadding>
                                {opsItems.map((item) => (
                                    <ListItem key={item.text} disablePadding sx={{ pl: 4 }}>
                                        <ListItemButton component={Link} to={item.path}>
                                            <ListItemIcon>{item.icon}</ListItemIcon>
                                            <ListItemText primary={item.text} />
                                        </ListItemButton>
                                    </ListItem>
                                ))}
                            </List>
                        </Collapse>
                    </List>

                    <Divider />

                    <List>
                        <ListItemButton onClick={() => setSettingsOpen(!settingsOpen)}>
                            <ListItemIcon><Settings /></ListItemIcon>
                            <ListItemText primary="系统设置" />
                            {settingsOpen ? <ExpandLess /> : <ExpandMore />}
                        </ListItemButton>
                        <Collapse in={settingsOpen} timeout="auto" unmountOnExit>
                            <List component="div" disablePadding>
                                {settingsItems.map((item) => (
                                    <ListItem key={item.text} disablePadding sx={{ pl: 4 }}>
                                        <ListItemButton component={Link} to={item.path}>
                                            <ListItemIcon>{item.icon}</ListItemIcon>
                                            <ListItemText primary={item.text} />
                                        </ListItemButton>
                                    </ListItem>
                                ))}
                            </List>
                        </Collapse>
                    </List>
                </Box>
            </Drawer>
            <Box component="main" sx={{
                flexGrow: 1,
                p: 2,
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden'
            }}>
                <Toolbar />
                <Box sx={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                    <Outlet />
                </Box>
            </Box>

            {manualSearchMounted && (
                <Suspense fallback={null}>
                    <ManualSearchDialog open={manualSearchOpen} onClose={() => setManualSearchOpen(false)} />
                </Suspense>
            )}
        </Box>
    );
}
export default AppLayout;
