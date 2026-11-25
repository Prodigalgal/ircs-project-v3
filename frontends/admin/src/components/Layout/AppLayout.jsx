// File: frontends/admin/src/components/Layout/AppLayout.jsx
import React, { lazy, Suspense, useMemo, useState } from 'react';
import {
    AppBar,
    Box,
    Button,
    Collapse,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Divider,
    Drawer,
    IconButton,
    List,
    ListItem,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Stack,
    Toolbar,
    Tooltip,
    Typography,
    useMediaQuery,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
    Category,
    ChevronLeft as ChevronLeftIcon,
    ChevronRight as ChevronRightIcon,
    Dashboard as DashboardIcon,
    Dns,
    ExpandLess,
    ExpandMore,
    FeaturedPlayList,
    Image as ImageIcon,
    Link as LinkIcon,
    Logout as LogoutIcon,
    Menu as MenuIcon,
    Movie,
    People,
    PlayCircle,
    Search as SearchIcon,
    Settings,
    Storage,
    Style,
    Tune,
    ViewModule,
    Warning as WarningIcon,
} from '@mui/icons-material';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import TranslateIcon from '@mui/icons-material/Translate';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SupervisorAccountIcon from '@mui/icons-material/SupervisorAccount';
import EngineeringIcon from '@mui/icons-material/Engineering';
import PublicIcon from '@mui/icons-material/Public';
import FeedbackIcon from '@mui/icons-material/Feedback';
import MovieCreationIcon from '@mui/icons-material/MovieCreation';
import KeyIcon from '@mui/icons-material/Key';
import TrafficIcon from '@mui/icons-material/Traffic';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import CrisisAlertIcon from '@mui/icons-material/CrisisAlert';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

import ThemeModeToggle from '../common/ThemeModeToggle';
import {
    NAV_GROUPS,
    PRIMARY_NAV_ITEMS,
    isRouteActive,
    resolveNavigationMeta,
} from '../../routes/navigation';

const DRAWER_WIDTH = 248;
const MINI_DRAWER_WIDTH = 68;
const HEADER_HEIGHT = 56;
const SIDEBAR_STORAGE_KEY = 'ircs_admin_sidebar_collapsed';

const ManualSearchDialog = lazy(() => import('../../features/scraper/ManualSearchDialog'));

const ICON_MAP = {
    dashboard: <DashboardIcon />,
    dataSources: <Dns />,
    tasks: <Storage />,
    videos: <Movie />,
    contentGroup: <ViewModule />,
    unifiedVideos: <ViewModule />,
    magnets: <TravelExploreIcon />,
    playlists: <FeaturedPlayList />,
    images: <ImageIcon />,
    categories: <Category />,
    genres: <Style />,
    resolvers: <PlayCircle />,
    memberGroup: <People />,
    members: <SupervisorAccountIcon />,
    messages: <FeedbackIcon />,
    mediaRequests: <MovieCreationIcon />,
    opsGroup: <MonitorHeartIcon />,
    traffic: <TrafficIcon />,
    alertCenter: <CrisisAlertIcon />,
    dlq: <WarningIcon />,
    requestAudit: <ReceiptLongIcon />,
    maintenance: <EngineeringIcon />,
    settingsGroup: <Settings />,
    systemConfig: <Tune />,
    apiTokens: <VpnKeyIcon />,
    credentials: <KeyIcon />,
    domains: <LinkIcon />,
    languages: <TranslateIcon />,
    areas: <PublicIcon />,
};

function readInitialSidebarCollapsed() {
    if (typeof window === 'undefined') {
        return false;
    }
    try {
        return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true';
    } catch {
        return false;
    }
}

function BrandMark() {
    return (
        <Box
            sx={(theme) => ({
                width: 30,
                height: 30,
                borderRadius: '8px',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
                color: '#FFFFFF',
                fontWeight: 800,
                fontSize: '0.78rem',
                letterSpacing: '-0.04em',
                boxShadow: `0 2px 8px ${alpha(theme.palette.primary.main, 0.35)}`,
            })}
        >
            IR
        </Box>
    );
}

function AppLayout() {
    const theme = useTheme();
    const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
    const navigate = useNavigate();
    const location = useLocation();

    const [sidebarCollapsed, setSidebarCollapsed] = useState(readInitialSidebarCollapsed);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [groupOverrides, setGroupOverrides] = useState({});
    const [manualSearchOpen, setManualSearchOpen] = useState(false);
    const [manualSearchMounted, setManualSearchMounted] = useState(false);
    const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);

    const navMeta = useMemo(
        () => resolveNavigationMeta(location.pathname),
        [location.pathname],
    );

    const isMini = isDesktop && sidebarCollapsed;
    const activeDrawerWidth = isMini ? MINI_DRAWER_WIDTH : DRAWER_WIDTH;

    const isGroupExpanded = (groupId) => {
        if (groupId in groupOverrides) {
            return groupOverrides[groupId];
        }
        return navMeta.groupId === groupId;
    };

    const handleToggleGroup = (groupId) => {
        if (isMini) {
            setSidebarCollapsed(false);
            try {
                window.localStorage.setItem(SIDEBAR_STORAGE_KEY, 'false');
            } catch {
                // Ignore storage errors.
            }
            setGroupOverrides((prev) => ({ ...prev, [groupId]: true }));
            return;
        }
        setGroupOverrides((prev) => ({
            ...prev,
            [groupId]: !isGroupExpanded(groupId),
        }));
    };

    const handleToggleCollapse = () => {
        setSidebarCollapsed((prev) => {
            const next = !prev;
            try {
                window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
            } catch {
                // Ignore storage errors.
            }
            return next;
        });
    };

    const handleOpenManualSearch = () => {
        setManualSearchMounted(true);
        setManualSearchOpen(true);
    };

    const handleConfirmLogout = () => {
        setLogoutDialogOpen(false);
        localStorage.removeItem('ircs_token');
        navigate('/login');
    };

    const handleNavItemClick = () => {
        if (!isDesktop) {
            setMobileOpen(false);
        }
    };

    const renderNavButton = (item, nested = false) => {
        const active = isRouteActive(location.pathname, item.path);
        const button = (
            <ListItemButton
                component={Link}
                to={item.path}
                onClick={handleNavItemClick}
                selected={active}
                sx={{
                    minHeight: 36,
                    mx: isMini ? 1 : 1.25,
                    my: 0.25,
                    px: isMini ? 1.25 : 1.5,
                    pl: isMini ? 1.25 : (nested ? 3.5 : 1.5),
                    borderRadius: '8px',
                    position: 'relative',
                    justifyContent: isMini ? 'center' : 'flex-start',
                    color: active ? 'primary.main' : 'text.secondary',
                    bgcolor: active ? alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.16 : 0.09) : 'transparent',
                    '&:hover': {
                        bgcolor: active
                            ? alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.22 : 0.13)
                            : 'action.hover',
                        color: active ? 'primary.main' : 'text.primary',
                    },
                    '&.Mui-selected': {
                        bgcolor: alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.16 : 0.09),
                        '&:hover': {
                            bgcolor: alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.22 : 0.13),
                        },
                    },
                    '&::before': active
                        ? {
                            content: '""',
                            position: 'absolute',
                            left: 3,
                            top: 8,
                            bottom: 8,
                            width: 3,
                            borderRadius: '2px',
                            bgcolor: 'primary.main',
                        }
                        : undefined,
                }}
            >
                <ListItemIcon
                    sx={{
                        minWidth: isMini ? 0 : 30,
                        justifyContent: 'center',
                        color: active ? 'primary.main' : 'inherit',
                        '& svg': {
                            fontSize: nested ? 17 : 19,
                        },
                    }}
                >
                    {ICON_MAP[item.iconKey]}
                </ListItemIcon>
                {!isMini && (
                    <ListItemText
                        primary={item.text}
                        slotProps={{
                            primary: {
                                noWrap: true,
                                sx: {
                                    fontSize: nested ? '0.8rem' : '0.83rem',
                                    fontWeight: active ? 600 : 500,
                                    lineHeight: 1.3,
                                },
                            },
                        }}
                    />
                )}
            </ListItemButton>
        );

        if (isMini) {
            return (
                <Tooltip key={item.id} title={item.text} placement="right" arrow>
                    <ListItem disablePadding>{button}</ListItem>
                </Tooltip>
            );
        }

        return (
            <ListItem key={item.id} disablePadding>
                {button}
            </ListItem>
        );
    };

    const drawerContent = (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Sidebar Brand Header */}
            <Box
                sx={{
                    height: HEADER_HEIGHT,
                    px: isMini ? 1.5 : 2,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: isMini ? 'center' : 'space-between',
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    flexShrink: 0,
                }}
            >
                <Box
                    component={Link}
                    to="/"
                    onClick={handleNavItemClick}
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.25,
                        textDecoration: 'none',
                        color: 'inherit',
                        minWidth: 0,
                    }}
                >
                    <BrandMark />
                    {!isMini && (
                        <Box sx={{ minWidth: 0 }}>
                            <Typography
                                variant="subtitle2"
                                noWrap
                                sx={{ fontWeight: 700, letterSpacing: '-0.01em', lineHeight: 1.2, color: 'text.primary' }}
                            >
                                IRCS 控制台
                            </Typography>
                            <Typography
                                variant="caption"
                                noWrap
                                sx={{ display: 'block', fontSize: '0.68rem', color: 'text.secondary', lineHeight: 1.2 }}
                            >
                                Project Hope
                            </Typography>
                        </Box>
                    )}
                </Box>
            </Box>

            {/* Scrollable Navigation Tree */}
            <Box sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', py: 1.25 }}>
                {!isMini && (
                    <Typography
                        variant="overline"
                        sx={{
                            px: 2.25,
                            pb: 0.5,
                            display: 'block',
                            color: 'text.disabled',
                            fontSize: '0.66rem',
                        }}
                    >
                        工作台
                    </Typography>
                )}
                <List disablePadding>
                    {PRIMARY_NAV_ITEMS.map((item) => renderNavButton(item, false))}
                </List>

                <Divider sx={{ my: 1.25, mx: isMini ? 1.5 : 2 }} />

                {!isMini && (
                    <Typography
                        variant="overline"
                        sx={{
                            px: 2.25,
                            pb: 0.5,
                            display: 'block',
                            color: 'text.disabled',
                            fontSize: '0.66rem',
                        }}
                    >
                        业务模块
                    </Typography>
                )}

                {NAV_GROUPS.map((group) => {
                    const expanded = isGroupExpanded(group.id);
                    const hasActiveChild = group.items.some((item) => isRouteActive(location.pathname, item.path));

                    const groupButton = (
                        <ListItemButton
                            onClick={() => handleToggleGroup(group.id)}
                            sx={{
                                minHeight: 36,
                                mx: isMini ? 1 : 1.25,
                                my: 0.25,
                                px: isMini ? 1.25 : 1.5,
                                borderRadius: '8px',
                                justifyContent: isMini ? 'center' : 'flex-start',
                                color: hasActiveChild ? 'primary.main' : 'text.primary',
                                '&:hover': {
                                    bgcolor: 'action.hover',
                                },
                            }}
                        >
                            <ListItemIcon
                                sx={{
                                    minWidth: isMini ? 0 : 30,
                                    justifyContent: 'center',
                                    color: hasActiveChild ? 'primary.main' : 'text.secondary',
                                    '& svg': { fontSize: 19 },
                                }}
                            >
                                {ICON_MAP[group.iconKey]}
                            </ListItemIcon>
                            {!isMini && (
                                <>
                                    <ListItemText
                                        primary={group.title}
                                        slotProps={{
                                            primary: {
                                                noWrap: true,
                                                sx: {
                                                    fontSize: '0.83rem',
                                                    fontWeight: hasActiveChild ? 600 : 500,
                                                },
                                            },
                                        }}
                                    />
                                    <Box sx={{ color: 'text.disabled', display: 'flex', alignItems: 'center' }}>
                                        {expanded ? <ExpandLess sx={{ fontSize: 18 }} /> : <ExpandMore sx={{ fontSize: 18 }} />}
                                    </Box>
                                </>
                            )}
                        </ListItemButton>
                    );

                    return (
                        <List key={group.id} disablePadding>
                            {isMini ? (
                                <Tooltip title={group.title} placement="right" arrow>
                                    <ListItem disablePadding>{groupButton}</ListItem>
                                </Tooltip>
                            ) : (
                                <ListItem disablePadding>{groupButton}</ListItem>
                            )}
                            {!isMini && (
                                <Collapse in={expanded} timeout="auto" unmountOnExit>
                                    <List component="div" disablePadding sx={{ pb: 0.5 }}>
                                        {group.items.map((item) => renderNavButton(item, true))}
                                    </List>
                                </Collapse>
                            )}
                        </List>
                    );
                })}
            </Box>

            {/* Sidebar Footer */}
            {isDesktop && (
                <Box
                    sx={{
                        p: 1.25,
                        borderTop: '1px solid',
                        borderColor: 'divider',
                        display: 'flex',
                        justifyContent: isMini ? 'center' : 'flex-end',
                    }}
                >
                    <Tooltip title={isMini ? '展开导航栏' : '收起导航栏'} placement="right">
                        <IconButton
                            size="small"
                            onClick={handleToggleCollapse}
                            aria-label={isMini ? '展开导航栏' : '收起导航栏'}
                            sx={{
                                color: 'text.secondary',
                                border: '1px solid',
                                borderColor: 'divider',
                                width: 30,
                                height: 30,
                            }}
                        >
                            {isMini ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
                        </IconButton>
                    </Tooltip>
                </Box>
            )}
        </Box>
    );

    return (
        <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden', bgcolor: 'background.default' }}>
            {/* Desktop Permanent Sidebar */}
            {isDesktop ? (
                <Drawer
                    variant="permanent"
                    sx={{
                        width: activeDrawerWidth,
                        flexShrink: 0,
                        transition: theme.transitions.create('width', {
                            easing: theme.transitions.easing.sharp,
                            duration: 200,
                        }),
                        '& .MuiDrawer-paper': {
                            width: activeDrawerWidth,
                            boxSizing: 'border-box',
                            overflowX: 'hidden',
                            transition: theme.transitions.create('width', {
                                easing: theme.transitions.easing.sharp,
                                duration: 200,
                            }),
                        },
                    }}
                >
                    {drawerContent}
                </Drawer>
            ) : (
                <Drawer
                    variant="temporary"
                    open={mobileOpen}
                    onClose={() => setMobileOpen(false)}
                    ModalProps={{ keepMounted: true }}
                    sx={{
                        '& .MuiDrawer-paper': {
                            width: DRAWER_WIDTH,
                            boxSizing: 'border-box',
                        },
                    }}
                >
                    {drawerContent}
                </Drawer>
            )}

            {/* Right Main Viewport */}
            <Box
                sx={{
                    flexGrow: 1,
                    minWidth: 0,
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'hidden',
                }}
            >
                <AppBar position="static" sx={{ flexShrink: 0 }}>
                    <Toolbar
                        sx={{
                            minHeight: `${HEADER_HEIGHT}px !important`,
                            px: { xs: 1.5, sm: 2.5 },
                            gap: 1.5,
                        }}
                    >
                        {!isDesktop && (
                            <IconButton
                                size="small"
                                onClick={() => setMobileOpen(true)}
                                aria-label="打开导航菜单"
                                sx={{
                                    border: '1px solid',
                                    borderColor: 'divider',
                                    color: 'text.primary',
                                }}
                            >
                                <MenuIcon fontSize="small" />
                            </IconButton>
                        )}

                        {/* Contextual Breadcrumb */}
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0, flexGrow: 1 }}>
                            <Typography
                                variant="caption"
                                noWrap
                                sx={{
                                    color: 'text.secondary',
                                    display: { xs: 'none', sm: 'block' },
                                    fontWeight: 500,
                                }}
                            >
                                {navMeta.groupTitle}
                            </Typography>
                            <Typography
                                variant="caption"
                                sx={{
                                    color: 'text.disabled',
                                    display: { xs: 'none', sm: 'block' },
                                }}
                            >
                                /
                            </Typography>
                            <Typography
                                variant="subtitle2"
                                noWrap
                                sx={{ fontWeight: 700, color: 'text.primary' }}
                            >
                                {navMeta.pageTitle}
                            </Typography>
                        </Stack>

                        {/* Topbar Utility Controls */}
                        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', flexShrink: 0 }}>
                            <Button
                                variant="outlined"
                                size="small"
                                startIcon={<SearchIcon sx={{ fontSize: '17px !important' }} />}
                                onClick={handleOpenManualSearch}
                                sx={{
                                    borderColor: 'divider',
                                    color: 'text.primary',
                                    bgcolor: 'background.paper',
                                    px: { xs: 1.25, sm: 1.5 },
                                    height: 34,
                                    '&:hover': {
                                        borderColor: 'primary.main',
                                        color: 'primary.main',
                                    },
                                }}
                            >
                                <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                                    全网搜索采集
                                </Box>
                                <Box component="span" sx={{ display: { xs: 'inline', sm: 'none' } }}>
                                    搜索
                                </Box>
                            </Button>

                            <Box sx={{ display: { xs: 'none', sm: 'flex' } }}>
                                <ThemeModeToggle variant="segmented" />
                            </Box>
                            <Box sx={{ display: { xs: 'flex', sm: 'none' } }}>
                                <ThemeModeToggle variant="icon" />
                            </Box>

                            <Tooltip title="退出登录">
                                <IconButton
                                    size="small"
                                    onClick={() => setLogoutDialogOpen(true)}
                                    aria-label="退出登录"
                                    sx={{
                                        width: 34,
                                        height: 34,
                                        border: '1px solid',
                                        borderColor: 'divider',
                                        color: 'text.secondary',
                                        bgcolor: 'background.paper',
                                        '&:hover': {
                                            color: 'error.main',
                                            borderColor: 'error.main',
                                            bgcolor: alpha(theme.palette.error.main, 0.08),
                                        },
                                    }}
                                >
                                    <LogoutIcon sx={{ fontSize: 18 }} />
                                </IconButton>
                            </Tooltip>
                        </Stack>
                    </Toolbar>
                </AppBar>

                {/* Page Content Area */}
                <Box
                    component="main"
                    sx={{
                        flex: 1,
                        minHeight: 0,
                        p: { xs: 1.5, sm: 2.5 },
                        display: 'flex',
                        flexDirection: 'column',
                        overflow: 'hidden',
                    }}
                >
                    <Outlet />
                </Box>
            </Box>

            {/* Clean Logout Confirmation Dialog */}
            <Dialog
                open={logoutDialogOpen}
                onClose={() => setLogoutDialogOpen(false)}
                maxWidth="xs"
                fullWidth
            >
                <DialogTitle>退出控制台</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        确定要退出当前管理员会话吗？退出后需要重新验证登录。
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setLogoutDialogOpen(false)} color="inherit">
                        取消
                    </Button>
                    <Button onClick={handleConfirmLogout} variant="contained" color="error">
                        确认退出
                    </Button>
                </DialogActions>
            </Dialog>

            {manualSearchMounted && (
                <Suspense fallback={null}>
                    <ManualSearchDialog open={manualSearchOpen} onClose={() => setManualSearchOpen(false)} />
                </Suspense>
            )}
        </Box>
    );
}

export default AppLayout;
