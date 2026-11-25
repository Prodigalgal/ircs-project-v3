import test from 'node:test';
import assert from 'node:assert/strict';

import {
    THEME_TOKENS,
    createAdminTheme,
    normalizeThemePreference,
    resolveThemeMode,
} from '../src/theme/themeConfig.js';
import {
    NAV_GROUPS,
    PRIMARY_NAV_ITEMS,
    isRouteActive,
    resolveNavigationMeta,
} from '../src/routes/navigation.js';

test('normalizeThemePreference defaults unknown values to system', () => {
    assert.equal(normalizeThemePreference('light'), 'light');
    assert.equal(normalizeThemePreference('dark'), 'dark');
    assert.equal(normalizeThemePreference('system'), 'system');
    assert.equal(normalizeThemePreference('invalid'), 'system');
    assert.equal(normalizeThemePreference(null), 'system');
});

test('resolveThemeMode honors explicit choice and falls back to system preference', () => {
    assert.equal(resolveThemeMode('light', true), 'light');
    assert.equal(resolveThemeMode('dark', false), 'dark');
    assert.equal(resolveThemeMode('system', true), 'dark');
    assert.equal(resolveThemeMode('system', false), 'light');
});

test('createAdminTheme builds valid light and dark dual-color themes', () => {
    const light = createAdminTheme('light');
    const dark = createAdminTheme('dark');

    assert.equal(light.palette.mode, 'light');
    assert.equal(dark.palette.mode, 'dark');
    assert.equal(light.palette.background.default, THEME_TOKENS.light.canvas);
    assert.equal(dark.palette.background.default, THEME_TOKENS.dark.canvas);
    assert.equal(light.palette.primary.main, THEME_TOKENS.light.primary);
    assert.equal(dark.palette.primary.main, THEME_TOKENS.dark.primary);
    assert.equal(light.palette.surface.codeBg, THEME_TOKENS.light.codeBg);
    assert.equal(dark.palette.surface.consoleBg, THEME_TOKENS.dark.consoleBg);
});

test('navigation metadata resolves all primary and grouped admin routes', () => {
    assert.equal(isRouteActive('/', '/'), true);
    assert.equal(isRouteActive('/videos', '/'), false);
    assert.equal(isRouteActive('/content/unified-videos', '/content/unified-videos'), true);
    assert.equal(isRouteActive('/content/unified-videos/123', '/content/unified-videos'), true);

    assert.deepEqual(resolveNavigationMeta('/'), {
        groupId: null,
        groupTitle: '核心工作台',
        pageTitle: '仪表盘',
        path: '/',
    });

    assert.deepEqual(resolveNavigationMeta('/ops/alert-center'), {
        groupId: 'ops',
        groupTitle: '运维监控',
        pageTitle: '告警与自愈',
        path: '/ops/alert-center',
    });

    const totalItems = PRIMARY_NAV_ITEMS.length + NAV_GROUPS.reduce((sum, g) => sum + g.items.length, 0);
    assert.equal(totalItems, 25);
});
