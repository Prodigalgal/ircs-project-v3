import React, { useEffect, useRef } from 'react';
import { Box, Typography } from '@mui/material';

function ConsoleViewer({ logs, height = 300 }) {
    const endRef = useRef(null);

    useEffect(() => {
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [logs]);

    const getColor = (type) => {
        switch (type) {
            case 'error': return '#ff5252';
            case 'warning': return '#ffb74d';
            case 'success': return '#69f0ae';
            default: return '#b0bec5';
        }
    };

    return (
        <Box sx={{
            bgcolor: '#000',
            border: '1px solid #333',
            borderRadius: 1,
            p: 2,
            height: height,
            overflowY: 'auto',
            fontFamily: 'Consolas, monospace',
            fontSize: '0.8rem',
            lineHeight: 1.6
        }}>
            {logs.length === 0 && (
                <Typography
                    sx={{
                        color: "text.disabled",
                        fontStyle: 'italic'
                    }}>
                    等待任务启动...
                </Typography>
            )}
            {logs.map((log, index) => (
                <div key={index} style={{ color: getColor(log.type) }}>
                    <span style={{ color: '#555', marginRight: 8 }}>
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