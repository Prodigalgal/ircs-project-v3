// File: frontends/admin/src/routes/navigation.js

export const PRIMARY_NAV_ITEMS = [
    { id: 'dashboard', text: '仪表盘', path: '/', iconKey: 'dashboard' },
    { id: 'data-sources', text: '数据源管理', path: '/data-sources', iconKey: 'dataSources' },
    { id: 'collection-tasks', text: '采集任务', path: '/collection-tasks', iconKey: 'tasks' },
    { id: 'videos', text: '源视频库', path: '/videos', iconKey: 'videos' },
];

export const NAV_GROUPS = [
    {
        id: 'content',
        title: '内容管理',
        iconKey: 'contentGroup',
        items: [
            { id: 'unified-videos', text: '聚合视频', path: '/content/unified-videos', iconKey: 'unifiedVideos' },
            { id: 'magnets', text: '磁链资源', path: '/content/magnets', iconKey: 'magnets' },
            { id: 'playlists', text: '播放列表', path: '/content/playlists', iconKey: 'playlists' },
            { id: 'images', text: '图片管理', path: '/content/images', iconKey: 'images' },
            { id: 'categories', text: '分类管理', path: '/content/categories', iconKey: 'categories' },
            { id: 'genres', text: '题材管理', path: '/content/genres', iconKey: 'genres' },
            { id: 'resolvers', text: '解析源管理', path: '/content/resolvers', iconKey: 'resolvers' },
        ],
    },
    {
        id: 'members',
        title: '会员与互动',
        iconKey: 'memberGroup',
        items: [
            { id: 'member-list', text: '会员管理', path: '/settings/members', iconKey: 'members' },
            { id: 'messages', text: '留言反馈', path: '/settings/messages', iconKey: 'messages' },
            { id: 'media-requests', text: '求片任务', path: '/settings/media-requests', iconKey: 'mediaRequests' },
        ],
    },
    {
        id: 'ops',
        title: '运维监控',
        iconKey: 'opsGroup',
        items: [
            { id: 'traffic', text: '流量治理', path: '/ops/traffic', iconKey: 'traffic' },
            { id: 'alert-center', text: '告警与自愈', path: '/ops/alert-center', iconKey: 'alertCenter' },
            { id: 'dlq', text: '死信队列', path: '/ops/dlq', iconKey: 'dlq' },
            { id: 'request-audit', text: '请求审计', path: '/ops/request-audit', iconKey: 'requestAudit' },
            { id: 'maintenance', text: '系统维护', path: '/ops/maintenance', iconKey: 'maintenance' },
        ],
    },
    {
        id: 'settings',
        title: '系统设置',
        iconKey: 'settingsGroup',
        items: [
            { id: 'system-config', text: '系统参数', path: '/settings/system', iconKey: 'systemConfig' },
            { id: 'api-tokens', text: 'API Token', path: '/settings/api-tokens', iconKey: 'apiTokens' },
            { id: 'credentials', text: '凭证管理', path: '/settings/credentials', iconKey: 'credentials' },
            { id: 'domains', text: '域名管理', path: '/settings/domains', iconKey: 'domains' },
            { id: 'languages', text: '语言管理', path: '/settings/languages', iconKey: 'languages' },
            { id: 'areas', text: '地区管理', path: '/settings/areas', iconKey: 'areas' },
        ],
    },
];

export function isRouteActive(currentPath, targetPath) {
    if (!currentPath || !targetPath) {
        return false;
    }
    if (targetPath === '/') {
        return currentPath === '/';
    }
    return currentPath === targetPath || currentPath.startsWith(`${targetPath}/`);
}

export function resolveNavigationMeta(pathname) {
    const primaryMatch = PRIMARY_NAV_ITEMS.find((item) => isRouteActive(pathname, item.path));
    if (primaryMatch) {
        return {
            groupId: null,
            groupTitle: '核心工作台',
            pageTitle: primaryMatch.text,
            path: primaryMatch.path,
        };
    }

    for (const group of NAV_GROUPS) {
        const match = group.items.find((item) => isRouteActive(pathname, item.path));
        if (match) {
            return {
                groupId: group.id,
                groupTitle: group.title,
                pageTitle: match.text,
                path: match.path,
            };
        }
    }

    return {
        groupId: null,
        groupTitle: 'IRCS 控制台',
        pageTitle: '概览',
        path: pathname || '/',
    };
}
