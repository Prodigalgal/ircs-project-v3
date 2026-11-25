// File: frontends/admin/src/routes/AppRouter.jsx
import React, { lazy, Suspense } from 'react';
import { Route, Routes, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Box, LinearProgress, Typography } from '@mui/material';
import RouteErrorBoundary from '../components/common/RouteErrorBoundary.jsx';

const AppLayout = lazy(() => import('../components/Layout/AppLayout.jsx'));
const LoginPage = lazy(() => import('../features/auth/LoginPage'));
const Dashboard = lazy(() => import('../features/dashboard/Dashboard'));
const DataSourceListPage = lazy(() => import('../features/data-sources/DataSourceListPage.jsx'));
const CollectionTaskList = lazy(() => import('../features/collection-tasks/CollectionTaskList'));
const VideoList = lazy(() => import('../features/videos/VideoList'));
const CategoryList = lazy(() => import('../features/categories/CategoryList'));
const UnifiedVideoList = lazy(() => import('../features/unified-videos/UnifiedVideoList'));
const MagnetResourceList = lazy(() => import('../features/magnets/MagnetResourceList.jsx'));
const PlaylistList = lazy(() => import('../features/playlists/PlaylistList'));
const CoverImageList = lazy(() => import('../features/images/CoverImageList'));
const SystemConfigList = lazy(() => import('../features/settings/SystemConfigList'));
const ApiTokenList = lazy(() => import('../features/settings/ApiTokenList.jsx'));
const SourceDomainList = lazy(() => import('../features/source-domain/SourceDomainList.jsx'));
const ResolverList = lazy(() => import('../features/resolvers/ResolverList.jsx'));
const LanguageMappingList = lazy(() => import('../features/language/LanguageMappingList.jsx'));
const GenreList = lazy(() => import('../features/genres/GenreList.jsx'));
const DlqList = lazy(() => import('../features/ops/dlq/DlqList.jsx'));
const MemberList = lazy(() => import('../features/members/MemberList.jsx'));
const MessageList = lazy(() => import('../features/messages/MessageList.jsx'));
const MediaRequestBatchList = lazy(() => import('../features/media-requests/MediaRequestBatchList.jsx'));
const MaintenancePage = lazy(() => import('../features/ops/maintenance/MaintenancePage.jsx'));
const CredentialList = lazy(() => import('../features/credential/CredentialList.jsx'));
const TrafficDashboard = lazy(() => import('../features/ops/traffic/TrafficDashboard.jsx'));
const RequestAuditPage = lazy(() => import('../features/ops/request-audit/RequestAuditPage.jsx'));
const AlertCenterPage = lazy(() => import('../features/ops/alert-center/AlertCenterPage.jsx'));
const AreaList = lazy(() => import('../features/areas/AreaList.jsx'));

function PageFallback() {
    return (
        <Box sx={{ width: '100%', p: 3 }}>
            <LinearProgress sx={{ borderRadius: 999, mb: 1.5, maxWidth: 320 }} />
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                正在加载页面...
            </Typography>
        </Box>
    );
}

const PrivateRoute = () => {
    const auth = localStorage.getItem('ircs_token');
    return auth ? <Outlet /> : <Navigate to="/login" />;
};

function AppRouter() {
    const location = useLocation();
    const resetKey = `${location.pathname}${location.search}`;

    return (
        <RouteErrorBoundary resetKey={resetKey}>
            <Suspense fallback={<PageFallback />}>
                <Routes>
                    <Route path="/login" element={<LoginPage />} />
                    <Route element={<PrivateRoute />}>
                        <Route path="/" element={<AppLayout />}>
                            <Route index element={<Dashboard />} />
                            <Route path="data-sources" element={<DataSourceListPage />} />
                            <Route path="collection-tasks" element={<CollectionTaskList />} />
                            <Route path="videos" element={<VideoList />} />

                            <Route path="content/unified-videos" element={<UnifiedVideoList />} />
                            <Route path="content/magnets" element={<MagnetResourceList />} />
                            <Route path="content/categories" element={<CategoryList />} />
                            <Route path="content/genres" element={<GenreList />} />
                            <Route path="content/playlists" element={<PlaylistList />} />
                            <Route path="content/images" element={<CoverImageList />} />
                            <Route path="content/resolvers" element={<ResolverList />} />

                            <Route path="ops/traffic" element={<TrafficDashboard />} />
                            <Route path="ops/alert-center" element={<AlertCenterPage />} />
                            <Route path="ops/dlq" element={<DlqList />} />
                            <Route path="ops/request-audit" element={<RequestAuditPage />} />
                            <Route path="ops/log-audit" element={<Navigate to="/ops/request-audit" replace />} />
                            <Route path="ops/maintenance" element={<MaintenancePage />} />

                            <Route path="settings/system" element={<SystemConfigList />} />
                            <Route path="settings/api-tokens" element={<ApiTokenList />} />
                            <Route path="settings/credentials" element={<CredentialList />} />
                            <Route path="settings/domains" element={<SourceDomainList />} />
                            <Route path="settings/languages" element={<LanguageMappingList />} />
                            <Route path="settings/areas" element={<AreaList />} />
                            <Route path="settings/members" element={<MemberList />} />
                            <Route path="settings/messages" element={<MessageList />} />
                            <Route path="settings/media-requests" element={<MediaRequestBatchList />} />
                        </Route>
                    </Route>
                </Routes>
            </Suspense>
        </RouteErrorBoundary>
    );
}

export default AppRouter;
