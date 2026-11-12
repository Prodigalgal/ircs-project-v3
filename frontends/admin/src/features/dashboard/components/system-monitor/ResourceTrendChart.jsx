import React from 'react';
import { Box, Paper, Typography } from '@mui/material';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

function ResourceTrendChart({ chartData }) {
    return (
        <Paper variant="outlined" sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Typography variant="subtitle2" gutterBottom>资源占用趋势 (Valkey & Total Backlog)</Typography>
            <Box sx={{ flex: 1, minHeight: 250 }}>
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                        <XAxis dataKey="time" hide />
                        <YAxis fontSize={10} stroke="#666" />
                        <Tooltip contentStyle={{ backgroundColor: '#1e1e1e', border: 'none', borderRadius: '4px' }} />
                        <Area type="monotone" dataKey="redisMem" stackId="1" stroke="#ff7300" fill="#ff7300" fillOpacity={0.4} name="Redis内存(MB)" />
                        <Area type="monotone" dataKey="backlog" stackId="2" stroke="#ffc658" fill="#ffc658" fillOpacity={0.4} name="总任务积压" />
                    </AreaChart>
                </ResponsiveContainer>
            </Box>
        </Paper>
    );
}

export default ResourceTrendChart;
