// File: ircs-frontend/src/features/dashboard/components/StatCard.jsx
import React from 'react';
import { Paper, Box, Typography } from '@mui/material';
import PropTypes from 'prop-types';

function StatCard({ title, value, icon, color = 'primary.main', subtitle }) {
    return (
        <Paper
            variant="outlined"
            sx={{
                p: 2,
                display: 'flex',
                alignItems: 'center',
                height: 120,
                position: 'relative',
                overflow: 'hidden'
            }}
        >
            <Box
                sx={{
                    p: 1.5,
                    borderRadius: 2,
                    bgcolor: `color-mix(in srgb, ${color} 15%, transparent)`,
                    color: color,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mr: 2,
                    minWidth: 56,
                    height: 56
                }}
            >
                {icon}
            </Box>
            <Box sx={{ minWidth: 0 }}>
                <Typography variant="h5" noWrap sx={{
                    fontWeight: "bold"
                }}>
                    {value}
                </Typography>
                <Typography variant="body2" noWrap sx={{
                    color: "text.secondary"
                }}>
                    {title}
                </Typography>
                {subtitle && (
                    <Typography
                        variant="caption"
                        noWrap
                        sx={{
                            color: "text.secondary",
                            mt: 0.5,
                            display: 'block'
                        }}>
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
    color: PropTypes.string,
    subtitle: PropTypes.string
};

export default StatCard;
