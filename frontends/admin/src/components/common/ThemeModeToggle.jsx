// File: frontends/admin/src/components/common/ThemeModeToggle.jsx
import React from 'react';
import { Box, IconButton, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import { useThemeMode } from '../../theme/useThemeMode';

function ThemeModeToggle({ variant = 'segmented' }) {
    const { resolvedMode, setPreference, toggleMode } = useThemeMode();
    const isDark = resolvedMode === 'dark';

    if (variant === 'icon') {
        return (
            <Tooltip title={isDark ? '切换到浅色主题' : '切换到深色主题'}>
                <IconButton
                    size="small"
                    onClick={toggleMode}
                    aria-label={isDark ? '切换到浅色主题' : '切换到深色主题'}
                    sx={(theme) => ({
                        width: 34,
                        height: 34,
                        border: '1px solid',
                        borderColor: 'divider',
                        color: 'text.secondary',
                        bgcolor: 'background.paper',
                        '&:hover': {
                            color: 'primary.main',
                            borderColor: 'primary.main',
                            bgcolor: alpha(theme.palette.primary.main, 0.06),
                        },
                    })}
                >
                    {isDark ? <LightModeOutlinedIcon sx={{ fontSize: 18 }} /> : <DarkModeOutlinedIcon sx={{ fontSize: 18 }} />}
                </IconButton>
            </Tooltip>
        );
    }

    const options = [
        { value: 'light', label: '浅色', icon: <LightModeOutlinedIcon sx={{ fontSize: 15 }} /> },
        { value: 'dark', label: '深色', icon: <DarkModeOutlinedIcon sx={{ fontSize: 15 }} /> },
    ];

    return (
        <Box
            role="group"
            aria-label="主题切换"
            sx={(theme) => ({
                display: 'inline-flex',
                alignItems: 'center',
                p: '3px',
                gap: '2px',
                borderRadius: '9px',
                border: '1px solid',
                borderColor: 'divider',
                bgcolor: theme.palette.surface.subtle,
            })}
        >
            {options.map((option) => {
                const active = resolvedMode === option.value;
                return (
                    <Box
                        key={option.value}
                        component="button"
                        type="button"
                        aria-pressed={active}
                        onClick={() => setPreference(option.value)}
                        sx={(theme) => ({
                            appearance: 'none',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 0.6,
                            px: 1.15,
                            py: 0.45,
                            borderRadius: '6px',
                            fontSize: '0.76rem',
                            fontWeight: active ? 600 : 500,
                            fontFamily: 'inherit',
                            lineHeight: 1.2,
                            transition: 'all 160ms ease',
                            bgcolor: active ? theme.palette.background.paper : 'transparent',
                            color: active ? theme.palette.primary.main : theme.palette.text.secondary,
                            boxShadow: active
                                ? (theme.palette.mode === 'dark'
                                    ? '0 1px 2px rgba(0, 0, 0, 0.35)'
                                    : '0 1px 2px rgba(15, 23, 42, 0.08)')
                                : 'none',
                            '&:hover': {
                                color: active ? theme.palette.primary.main : theme.palette.text.primary,
                            },
                            '&:focus-visible': {
                                outline: `2px solid ${theme.palette.primary.main}`,
                                outlineOffset: 1,
                            },
                        })}
                    >
                        {option.icon}
                        <span>{option.label}</span>
                    </Box>
                );
            })}
        </Box>
    );
}

export default ThemeModeToggle;
