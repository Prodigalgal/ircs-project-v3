import React, { memo } from 'react';
import { LinearProgress, Paper, Typography } from '@mui/material';

function MetricCard({ title, value, unit, color = 'primary.main', loading, subtitle }) {
    return (
        <Paper variant="outlined" sx={{ p: 3, textAlign: 'center', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', position: 'relative', overflow: 'hidden' }}>
            {loading && <LinearProgress sx={{ position: 'absolute', top: 0, left: 0, width: '100%' }} />}
            <Typography
                variant="body2"
                gutterBottom
                sx={{
                    color: "text.secondary",
                    fontSize: '0.75rem',
                    textTransform: 'uppercase',
                    letterSpacing: '1px'
                }}>
                {title}
            </Typography>
            <Typography variant="h4" color={color} sx={{
                fontWeight: "500"
            }}>
                {value} <span style={{ fontSize: '0.9rem', color: '#666', fontWeight: 'normal' }}>{unit}</span>
            </Typography>
            {subtitle && (
                <Typography
                    variant="caption"
                    sx={{
                        color: "text.disabled",
                        mt: 1
                    }}>
                    {subtitle}
                </Typography>
            )}
        </Paper>
    );
}

export default memo(MetricCard);
