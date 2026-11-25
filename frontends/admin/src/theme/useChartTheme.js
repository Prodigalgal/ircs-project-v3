// File: frontends/admin/src/theme/useChartTheme.js
import { useMemo } from 'react';
import { alpha, useTheme } from '@mui/material/styles';

/**
 * Provides theme-reactive colors for Recharts components,
 * anchored to the admin dual-color palette (Indigo + Teal + restrained semantic tones).
 */
export function useChartTheme() {
    const theme = useTheme();
    const isDark = theme.palette.mode === 'dark';

    return useMemo(
        () => ({
            isDark,
            grid: theme.palette.divider,
            axis: theme.palette.text.secondary,
            axisLine: theme.palette.divider,
            cursorFill: isDark ? 'rgba(148, 163, 184, 0.08)' : 'rgba(15, 23, 42, 0.04)',
            primary: theme.palette.primary.main,
            primarySoft: alpha(theme.palette.primary.main, isDark ? 0.32 : 0.2),
            secondary: theme.palette.secondary.main,
            secondarySoft: alpha(theme.palette.secondary.main, isDark ? 0.3 : 0.18),
            success: theme.palette.success.main,
            warning: theme.palette.warning.main,
            error: theme.palette.error.main,
            info: theme.palette.info.main,
            series: isDark
                ? ['#818CF8', '#2DD4BF', '#6366F1', '#38BDF8', '#34D399', '#FBBF24', '#94A3B8', '#A5B4FC']
                : ['#4F46E5', '#0F766E', '#6366F1', '#0284C7', '#15803D', '#D97706', '#64748B', '#818CF8'],
            coverage: {
                '双ID齐全': theme.palette.secondary.main,
                '仅豆瓣': theme.palette.primary.main,
                '仅TMDB': theme.palette.info.main,
                '缺失ID': theme.palette.warning.main,
            },
            tooltipStyle: {
                backgroundColor: theme.palette.background.paper,
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 10,
                boxShadow: isDark
                    ? '0 12px 28px rgba(0, 0, 0, 0.45)'
                    : '0 10px 24px rgba(15, 23, 42, 0.1)',
                color: theme.palette.text.primary,
                fontSize: '0.8rem',
            },
            tooltipLabelStyle: {
                color: theme.palette.text.secondary,
                fontWeight: 600,
                marginBottom: 4,
            },
            tooltipItemStyle: {
                color: theme.palette.text.primary,
                padding: '2px 0',
            },
            legendStyle: {
                fontSize: '0.78rem',
                color: theme.palette.text.secondary,
            },
        }),
        [isDark, theme],
    );
}
