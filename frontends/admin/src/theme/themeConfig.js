// File: frontends/admin/src/theme/themeConfig.js
import { alpha, createTheme } from '@mui/material/styles';
import { zhCN } from '@mui/material/locale';

export const THEME_STORAGE_KEY = 'ircs_admin_theme_mode';
export const VALID_THEME_PREFERENCES = ['light', 'dark', 'system'];

export function normalizeThemePreference(value) {
    if (VALID_THEME_PREFERENCES.includes(value)) {
        return value;
    }
    return 'system';
}

export function resolveThemeMode(preference, prefersDark = false) {
    const normalized = normalizeThemePreference(preference);
    if (normalized === 'light' || normalized === 'dark') {
        return normalized;
    }
    return prefersDark ? 'dark' : 'light';
}

export const THEME_TOKENS = {
    light: {
        mode: 'light',
        canvas: '#F5F7FA',
        paper: '#FFFFFF',
        subtle: '#EEF1F6',
        elevated: '#FFFFFF',
        sidebar: '#FFFFFF',
        header: 'rgba(255, 255, 255, 0.86)',
        codeBg: '#F4F6FA',
        codeText: '#1E293B',
        consoleBg: '#0F141E',
        consoleHeader: '#171D2B',
        consoleBorder: '#242C3D',
        consoleText: '#CBD5E1',
        consoleMuted: '#64748B',
        primary: '#4F46E5',
        primaryLight: '#6366F1',
        primaryDark: '#4338CA',
        primaryContrast: '#FFFFFF',
        secondary: '#0F766E',
        secondaryLight: '#14B8A6',
        secondaryDark: '#115E59',
        secondaryContrast: '#FFFFFF',
        textPrimary: '#0F172A',
        textSecondary: '#475569',
        textDisabled: '#94A3B8',
        divider: '#E2E8F0',
        borderStrong: '#CBD5E1',
        success: '#15803D',
        successContrast: '#FFFFFF',
        warning: '#B45309',
        warningContrast: '#FFFFFF',
        error: '#DC2626',
        errorContrast: '#FFFFFF',
        info: '#0369A1',
        infoContrast: '#FFFFFF',
        scrollbarTrack: 'transparent',
        scrollbarThumb: 'rgba(100, 116, 139, 0.28)',
        scrollbarThumbHover: 'rgba(71, 85, 105, 0.48)',
        cardShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
        popoverShadow: '0 12px 32px -8px rgba(15, 23, 42, 0.14), 0 4px 10px -4px rgba(15, 23, 42, 0.06)',
    },
    dark: {
        mode: 'dark',
        canvas: '#0A0D12',
        paper: '#11151E',
        subtle: '#171C28',
        elevated: '#1B2130',
        sidebar: '#0D1017',
        header: 'rgba(10, 13, 18, 0.84)',
        codeBg: '#0D1017',
        codeText: '#E2E8F0',
        consoleBg: '#080B10',
        consoleHeader: '#121622',
        consoleBorder: '#202637',
        consoleText: '#CBD5E1',
        consoleMuted: '#64748B',
        primary: '#6366F1',
        primaryLight: '#818CF8',
        primaryDark: '#4F46E5',
        primaryContrast: '#FFFFFF',
        secondary: '#2DD4BF',
        secondaryLight: '#5EEAD4',
        secondaryDark: '#14B8A6',
        secondaryContrast: '#042F2E',
        textPrimary: '#F1F5F9',
        textSecondary: '#94A3B8',
        textDisabled: '#64748B',
        divider: '#1E2536',
        borderStrong: '#2E3850',
        success: '#34D399',
        successContrast: '#052E16',
        warning: '#FBBF24',
        warningContrast: '#1C1917',
        error: '#F87171',
        errorContrast: '#1F0A0A',
        info: '#38BDF8',
        infoContrast: '#082F49',
        scrollbarTrack: 'transparent',
        scrollbarThumb: 'rgba(148, 163, 184, 0.24)',
        scrollbarThumbHover: 'rgba(148, 163, 184, 0.42)',
        cardShadow: '0 1px 2px rgba(0, 0, 0, 0.25)',
        popoverShadow: '0 16px 40px -8px rgba(0, 0, 0, 0.6), 0 4px 12px -4px rgba(0, 0, 0, 0.4)',
    },
};

const SANS_FONT = '"Inter", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
export const MONO_FONT = '"JetBrains Mono", "Fira Code", ui-monospace, SFMono-Regular, Consolas, Menlo, monospace';

export function createAdminTheme(mode = 'dark') {
    const tokens = THEME_TOKENS[mode === 'light' ? 'light' : 'dark'];
    const isDark = tokens.mode === 'dark';

    return createTheme(
        {
            palette: {
                mode: tokens.mode,
                primary: {
                    main: tokens.primary,
                    light: tokens.primaryLight,
                    dark: tokens.primaryDark,
                    contrastText: tokens.primaryContrast,
                },
                secondary: {
                    main: tokens.secondary,
                    light: tokens.secondaryLight,
                    dark: tokens.secondaryDark,
                    contrastText: tokens.secondaryContrast,
                },
                background: {
                    default: tokens.canvas,
                    paper: tokens.paper,
                },
                text: {
                    primary: tokens.textPrimary,
                    secondary: tokens.textSecondary,
                    disabled: tokens.textDisabled,
                },
                divider: tokens.divider,
                success: {
                    main: tokens.success,
                    contrastText: tokens.successContrast,
                },
                warning: {
                    main: tokens.warning,
                    contrastText: tokens.warningContrast,
                },
                error: {
                    main: tokens.error,
                    contrastText: tokens.errorContrast,
                },
                info: {
                    main: tokens.info,
                    contrastText: tokens.infoContrast,
                },
                action: {
                    hover: isDark ? 'rgba(148, 163, 184, 0.08)' : 'rgba(15, 23, 42, 0.04)',
                    selected: alpha(tokens.primary, isDark ? 0.16 : 0.09),
                    focus: alpha(tokens.primary, isDark ? 0.22 : 0.14),
                    disabledBackground: isDark ? 'rgba(148, 163, 184, 0.1)' : 'rgba(15, 23, 42, 0.06)',
                },
                surface: {
                    subtle: tokens.subtle,
                    elevated: tokens.elevated,
                    sidebar: tokens.sidebar,
                    header: tokens.header,
                    borderStrong: tokens.borderStrong,
                    codeBg: tokens.codeBg,
                    codeText: tokens.codeText,
                    consoleBg: tokens.consoleBg,
                    consoleHeader: tokens.consoleHeader,
                    consoleBorder: tokens.consoleBorder,
                    consoleText: tokens.consoleText,
                    consoleMuted: tokens.consoleMuted,
                },
            },
            shape: {
                borderRadius: 8,
            },
            typography: {
                fontFamily: SANS_FONT,
                fontSize: 14,
                h4: {
                    fontSize: '1.5rem',
                    fontWeight: 700,
                    letterSpacing: '-0.02em',
                    lineHeight: 1.25,
                },
                h5: {
                    fontSize: '1.25rem',
                    fontWeight: 700,
                    letterSpacing: '-0.015em',
                    lineHeight: 1.3,
                },
                h6: {
                    fontSize: '1rem',
                    fontWeight: 600,
                    letterSpacing: '-0.01em',
                    lineHeight: 1.35,
                },
                subtitle1: {
                    fontSize: '0.95rem',
                    fontWeight: 600,
                    lineHeight: 1.4,
                },
                subtitle2: {
                    fontSize: '0.84rem',
                    fontWeight: 600,
                    lineHeight: 1.4,
                },
                body1: {
                    fontSize: '0.875rem',
                    lineHeight: 1.55,
                },
                body2: {
                    fontSize: '0.825rem',
                    lineHeight: 1.5,
                },
                caption: {
                    fontSize: '0.75rem',
                    lineHeight: 1.4,
                },
                overline: {
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    letterSpacing: '0.08em',
                    lineHeight: 1.4,
                },
                button: {
                    textTransform: 'none',
                    fontWeight: 600,
                    letterSpacing: '0.005em',
                },
            },
            components: {
                MuiCssBaseline: {
                    styleOverrides: {
                        ':root': {
                            colorScheme: tokens.mode,
                            '--ircs-canvas': tokens.canvas,
                            '--ircs-paper': tokens.paper,
                            '--ircs-subtle': tokens.subtle,
                            '--ircs-text': tokens.textPrimary,
                            '--ircs-text-secondary': tokens.textSecondary,
                            '--ircs-border': tokens.divider,
                            '--ircs-primary': tokens.primary,
                            '--ircs-primary-contrast': tokens.primaryContrast,
                            '--ircs-error': tokens.error,
                        },
                        'html, body, #root': {
                            height: '100%',
                            margin: 0,
                            padding: 0,
                        },
                        body: {
                            backgroundColor: tokens.canvas,
                            color: tokens.textPrimary,
                            WebkitFontSmoothing: 'antialiased',
                            MozOsxFontSmoothing: 'grayscale',
                            fontFeatureSettings: '"cv02", "cv03", "cv04", "cv11"',
                        },
                        '*::-webkit-scrollbar': {
                            width: 7,
                            height: 7,
                        },
                        '*::-webkit-scrollbar-track': {
                            backgroundColor: tokens.scrollbarTrack,
                        },
                        '*::-webkit-scrollbar-thumb': {
                            backgroundColor: tokens.scrollbarThumb,
                            borderRadius: 999,
                            border: '2px solid transparent',
                            backgroundClip: 'content-box',
                        },
                        '*::-webkit-scrollbar-thumb:hover': {
                            backgroundColor: tokens.scrollbarThumbHover,
                        },
                        '*::-webkit-scrollbar-corner': {
                            backgroundColor: 'transparent',
                        },
                        '@media (prefers-reduced-motion: reduce)': {
                            '*, *::before, *::after': {
                                animationDuration: '0.01ms !important',
                                animationIterationCount: '1 !important',
                                transitionDuration: '0.01ms !important',
                                scrollBehavior: 'auto !important',
                            },
                        },
                    },
                },
                MuiAppBar: {
                    defaultProps: {
                        elevation: 0,
                        color: 'transparent',
                    },
                    styleOverrides: {
                        root: {
                            backgroundImage: 'none',
                            backgroundColor: tokens.header,
                            backdropFilter: 'blur(12px)',
                            WebkitBackdropFilter: 'blur(12px)',
                            borderBottom: `1px solid ${tokens.divider}`,
                            color: tokens.textPrimary,
                        },
                    },
                },
                MuiDrawer: {
                    styleOverrides: {
                        paper: {
                            backgroundImage: 'none',
                            backgroundColor: tokens.sidebar,
                            borderRight: `1px solid ${tokens.divider}`,
                        },
                    },
                },
                MuiPaper: {
                    defaultProps: {
                        elevation: 0,
                    },
                    styleOverrides: {
                        root: {
                            backgroundImage: 'none',
                            borderRadius: 12,
                        },
                        outlined: {
                            borderColor: tokens.divider,
                            boxShadow: tokens.cardShadow,
                        },
                    },
                },
                MuiCard: {
                    defaultProps: {
                        variant: 'outlined',
                    },
                    styleOverrides: {
                        root: {
                            backgroundImage: 'none',
                            borderRadius: 12,
                            borderColor: tokens.divider,
                            boxShadow: tokens.cardShadow,
                            transition: 'border-color 180ms ease, box-shadow 180ms ease',
                        },
                    },
                },
                MuiButton: {
                    defaultProps: {
                        disableElevation: true,
                    },
                    styleOverrides: {
                        root: {
                            borderRadius: 8,
                            fontWeight: 600,
                            transition: 'background-color 150ms ease, border-color 150ms ease, color 150ms ease, box-shadow 150ms ease',
                        },
                        sizeSmall: {
                            padding: '5px 12px',
                            fontSize: '0.8rem',
                        },
                        sizeMedium: {
                            padding: '7px 16px',
                            fontSize: '0.84rem',
                        },
                        outlined: {
                            borderColor: tokens.borderStrong,
                            '&:hover': {
                                borderColor: tokens.primary,
                                backgroundColor: alpha(tokens.primary, isDark ? 0.1 : 0.05),
                            },
                        },
                    },
                },
                MuiIconButton: {
                    styleOverrides: {
                        root: {
                            borderRadius: 8,
                            transition: 'background-color 150ms ease, color 150ms ease',
                        },
                    },
                },
                MuiChip: {
                    styleOverrides: {
                        root: {
                            borderRadius: 6,
                            fontWeight: 500,
                            fontSize: '0.75rem',
                        },
                        sizeSmall: {
                            height: 24,
                        },
                        outlined: {
                            borderColor: tokens.borderStrong,
                        },
                    },
                },
                MuiTabs: {
                    styleOverrides: {
                        root: {
                            minHeight: 42,
                        },
                        indicator: {
                            height: 2,
                            borderRadius: 2,
                        },
                    },
                },
                MuiTab: {
                    styleOverrides: {
                        root: {
                            minHeight: 42,
                            padding: '8px 14px',
                            fontSize: '0.84rem',
                            fontWeight: 500,
                            textTransform: 'none',
                            color: tokens.textSecondary,
                            transition: 'color 150ms ease',
                            '&.Mui-selected': {
                                fontWeight: 600,
                                color: tokens.primary,
                            },
                            '& .MuiTab-icon': {
                                fontSize: 18,
                            },
                        },
                    },
                },
                MuiTableHead: {
                    styleOverrides: {
                        root: {
                            '& .MuiTableCell-head': {
                                backgroundColor: tokens.subtle,
                                color: tokens.textSecondary,
                                fontWeight: 600,
                                fontSize: '0.76rem',
                                letterSpacing: '0.02em',
                                borderBottom: `1px solid ${tokens.divider}`,
                                paddingTop: 10,
                                paddingBottom: 10,
                            },
                        },
                    },
                },
                MuiTableCell: {
                    styleOverrides: {
                        root: {
                            borderBottom: `1px solid ${tokens.divider}`,
                            fontSize: '0.825rem',
                            fontVariantNumeric: 'tabular-nums',
                        },
                        sizeSmall: {
                            padding: '9px 14px',
                        },
                    },
                },
                MuiTableRow: {
                    styleOverrides: {
                        root: {
                            transition: 'background-color 120ms ease',
                            '&.MuiTableRow-hover:hover': {
                                backgroundColor: isDark
                                    ? 'rgba(148, 163, 184, 0.06)'
                                    : 'rgba(15, 23, 42, 0.025)',
                            },
                            '&.Mui-selected': {
                                backgroundColor: alpha(tokens.primary, isDark ? 0.14 : 0.07),
                                '&:hover': {
                                    backgroundColor: alpha(tokens.primary, isDark ? 0.18 : 0.1),
                                },
                            },
                        },
                    },
                },
                MuiTablePagination: {
                    styleOverrides: {
                        root: {
                            fontVariantNumeric: 'tabular-nums',
                            fontSize: '0.8rem',
                            color: tokens.textSecondary,
                        },
                        toolbar: {
                            minHeight: 46,
                        },
                    },
                },
                MuiOutlinedInput: {
                    styleOverrides: {
                        root: {
                            borderRadius: 8,
                            backgroundColor: isDark ? alpha(tokens.subtle, 0.5) : tokens.paper,
                            transition: 'border-color 150ms ease, box-shadow 150ms ease, background-color 150ms ease',
                            '& .MuiOutlinedInput-notchedOutline': {
                                borderColor: tokens.divider,
                            },
                            '&:hover .MuiOutlinedInput-notchedOutline': {
                                borderColor: tokens.borderStrong,
                            },
                            '&.Mui-focused': {
                                boxShadow: `0 0 0 3px ${alpha(tokens.primary, isDark ? 0.2 : 0.12)}`,
                                '& .MuiOutlinedInput-notchedOutline': {
                                    borderColor: tokens.primary,
                                    borderWidth: 1,
                                },
                            },
                        },
                    },
                },
                MuiInputLabel: {
                    styleOverrides: {
                        root: {
                            fontSize: '0.84rem',
                        },
                    },
                },
                MuiDialog: {
                    styleOverrides: {
                        paper: {
                            backgroundImage: 'none',
                            backgroundColor: tokens.paper,
                            border: `1px solid ${tokens.divider}`,
                            borderRadius: 14,
                            boxShadow: tokens.popoverShadow,
                        },
                    },
                },
                MuiBackdrop: {
                    styleOverrides: {
                        root: {
                            backgroundColor: isDark ? 'rgba(5, 7, 11, 0.72)' : 'rgba(15, 23, 42, 0.36)',
                            backdropFilter: 'blur(3px)',
                        },
                        invisible: {
                            backgroundColor: 'transparent',
                            backdropFilter: 'none',
                        },
                    },
                },
                MuiDialogTitle: {
                    styleOverrides: {
                        root: {
                            fontSize: '1.05rem',
                            fontWeight: 700,
                            padding: '16px 24px',
                        },
                    },
                },
                MuiDialogContent: {
                    styleOverrides: {
                        root: {
                            padding: '20px 24px',
                        },
                    },
                },
                MuiDialogActions: {
                    styleOverrides: {
                        root: {
                            padding: '14px 24px',
                        },
                    },
                },
                MuiMenu: {
                    styleOverrides: {
                        paper: {
                            borderRadius: 10,
                            border: `1px solid ${tokens.divider}`,
                            boxShadow: tokens.popoverShadow,
                        },
                    },
                },
                MuiPopover: {
                    styleOverrides: {
                        paper: {
                            borderRadius: 10,
                            border: `1px solid ${tokens.divider}`,
                            boxShadow: tokens.popoverShadow,
                        },
                    },
                },
                MuiTooltip: {
                    styleOverrides: {
                        tooltip: {
                            backgroundColor: isDark ? '#1E2536' : '#0F172A',
                            color: '#F8FAFC',
                            fontSize: '0.74rem',
                            fontWeight: 500,
                            borderRadius: 6,
                            padding: '6px 10px',
                            border: isDark ? `1px solid ${tokens.borderStrong}` : 'none',
                        },
                    },
                },
                MuiAlert: {
                    styleOverrides: {
                        root: {
                            borderRadius: 10,
                            fontSize: '0.825rem',
                            alignItems: 'center',
                        },
                        outlined: {
                            backgroundColor: isDark ? alpha(tokens.subtle, 0.5) : tokens.paper,
                        },
                    },
                },
                MuiLinearProgress: {
                    styleOverrides: {
                        root: {
                            borderRadius: 999,
                            backgroundColor: isDark
                                ? 'rgba(148, 163, 184, 0.14)'
                                : 'rgba(15, 23, 42, 0.08)',
                        },
                        bar: {
                            borderRadius: 999,
                        },
                    },
                },
                MuiSwitch: {
                    styleOverrides: {
                        root: {
                            '& .MuiSwitch-track': {
                                borderRadius: 999,
                            },
                        },
                    },
                },
                MuiDivider: {
                    styleOverrides: {
                        root: {
                            borderColor: tokens.divider,
                        },
                    },
                },
            },
        },
        zhCN,
    );
}
