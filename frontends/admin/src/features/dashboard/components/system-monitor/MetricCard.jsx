// File: frontends/admin/src/features/dashboard/components/system-monitor/MetricCard.jsx
import React, { memo } from 'react';
import { Box, LinearProgress, Paper, Typography } from '@mui/material';

function MetricCard({ title, value, unit, color = 'primary.main', loading, subtitle }) {
    return (
        <Paper
            variant="outlined"
            sx={{
                p: 2.25,
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                position: 'relative',
                overflow: 'hidden',
                bgcolor: 'background.paper',
            }}
        >
            {loading && <LinearProgress sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: 2 }} />}
            <Typography
                variant="caption"
                sx={{
                    color: 'text.secondary',
                    fontSize: '0.74rem',
                    fontWeight: 600,
                    letterSpacing: '0.02em',
                    mb: 1,
                }}
            >
                {title}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75 }}>
                <Typography
                    variant="h4"
                    color={color}
                    sx={{
                        fontWeight: 700,
                        fontSize: '1.55rem',
                        letterSpacing: '-0.02em',
                        fontVariantNumeric: 'tabular-nums',
                        lineHeight: 1.15,
                    }}
                >
                    {value}
                </Typography>
                {unit && (
                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>
                        {unit}
                    </Typography>
                )}
            </Box>
            {subtitle && (
                <Typography
                    variant="caption"
                    noWrap
                    sx={{
                        color: 'text.secondary',
                        mt: 1,
                        fontSize: '0.72rem',
                    }}
                >
                    {subtitle}
                </Typography>
            )}
        </Paper>
    );
}

export default memo(MetricCard);
