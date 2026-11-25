// File: frontends/admin/src/features/ops/maintenance/components/ConsoleViewer.jsx
import React, { useEffect, useRef } from 'react';
import { Box, Typography } from '@mui/material';
import { MONO_FONT } from '../../../../theme/themeConfig';

const LOG_COLORS = {
    error: '#F87171',
    warning: '#FBBF24',
    success: '#34D399',
    default: '#CBD5E1',
};

function ConsoleViewer({ logs, height = 300 }) {
    const endRef = useRef(null);

    useEffect(() => {
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [logs]);

    const getColor = (type) => LOG_COLORS[type] || LOG_COLORS.default;

    return (
        <Box
            sx={(theme) => ({
                bgcolor: theme.palette.surface.consoleBg,
                border: '1px solid',
                borderColor: theme.palette.surface.consoleBorder,
                borderRadius: 2,
                p: 2,
                height,
                overflowY: 'auto',
                fontFamily: MONO_FONT,
                fontSize: '0.78rem',
                lineHeight: 1.65,
                color: theme.palette.surface.consoleText,
            })}
        >
            {logs.length === 0 && (
                <Typography
                    sx={(theme) => ({
                        color: theme.palette.surface.consoleMuted,
                        fontStyle: 'italic',
                        fontSize: '0.8rem',
                    })}
                >
                    等待任务启动...
                </Typography>
            )}
            {logs.map((log, index) => (
                <div key={index} style={{ color: getColor(log.type) }}>
                    <span style={{ color: '#64748B', marginRight: 8 }}>
                        [{new Date(log.time).toLocaleTimeString()}]
                    </span>
                    {log.msg}
                </div>
            ))}
            <div ref={endRef} />
        </Box>
    );
}

export default ConsoleViewer;
