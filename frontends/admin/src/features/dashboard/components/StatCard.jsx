// File: frontends/admin/src/features/dashboard/components/StatCard.jsx
import React from 'react';
import { Paper, Box, Typography, Stack } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import PropTypes from 'prop-types';

function resolveAccentColor(theme, tone, color) {
    if (tone && theme.palette[tone]?.main) {
        return theme.palette[tone].main;
    }
    if (color && color.includes('.')) {
        const [group, shade] = color.split('.');
        if (theme.palette[group]?.[shade]) {
            return theme.palette[group][shade];
        }
    }
    return color || theme.palette.primary.main;
}

function StatCard({ title, value, icon, tone, color = 'primary.main', subtitle }) {
    const theme = useTheme();
    const isDark = theme.palette.mode === 'dark';
    const accent = resolveAccentColor(theme, tone, color);

    return (
        <Paper
            variant="outlined"
            sx={{
                p: 2.25,
                height: '100%',
                minHeight: 118,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                position: 'relative',
                overflow: 'hidden',
                bgcolor: 'background.paper',
                transition: 'border-color 160ms ease, transform 160ms ease',
                '&:hover': {
                    borderColor: alpha(accent, isDark ? 0.5 : 0.45),
                },
            }}
        >
            <Stack direction="row" sx={{ alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5 }}>
                <Typography
                    variant="caption"
                    noWrap
                    sx={{
                        color: 'text.secondary',
                        fontWeight: 600,
                        fontSize: '0.78rem',
                    }}
                >
                    {title}
                </Typography>
                <Box
                    sx={{
                        width: 36,
                        height: 36,
                        borderRadius: '10px',
                        bgcolor: alpha(accent, isDark ? 0.16 : 0.1),
                        color: accent,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        '& svg': {
                            fontSize: 20,
                        },
                    }}
                >
                    {icon}
                </Box>
            </Stack>

            <Box sx={{ mt: 1.25 }}>
                <Typography
                    variant="h4"
                    noWrap
                    sx={{
                        fontWeight: 700,
                        fontSize: '1.6rem',
                        letterSpacing: '-0.025em',
                        fontVariantNumeric: 'tabular-nums',
                        color: 'text.primary',
                        lineHeight: 1.15,
                    }}
                >
                    {value}
                </Typography>
                {subtitle && (
                    <Typography
                        variant="caption"
                        noWrap
                        sx={{
                            color: 'text.secondary',
                            mt: 0.65,
                            display: 'block',
                            fontSize: '0.74rem',
                        }}
                    >
                        {subtitle}
                    </Typography>
                )}
            </Box>
        </Paper>
    );
}

StatCard.propTypes = {
    title: PropTypes.string.isRequired,
    value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
    icon: PropTypes.node.isRequired,
    tone: PropTypes.string,
    color: PropTypes.string,
    subtitle: PropTypes.string,
};

export default StatCard;
