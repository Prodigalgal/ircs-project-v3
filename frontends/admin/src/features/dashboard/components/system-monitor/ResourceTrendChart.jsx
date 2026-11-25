// File: frontends/admin/src/features/dashboard/components/system-monitor/ResourceTrendChart.jsx
import React from 'react';
import { Box, Paper, Typography } from '@mui/material';
import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useChartTheme } from '../../../../theme/useChartTheme';

function ResourceTrendChart({ chartData }) {
    const chart = useChartTheme();

    return (
        <Paper variant="outlined" sx={{ p: 2.5, height: '100%', display: 'flex', flexDirection: 'column', bgcolor: 'background.paper' }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
                资源占用趋势 (Valkey &amp; Total Backlog)
            </Typography>
            <Box sx={{ flex: 1, minHeight: 250 }}>
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} vertical={false} />
                        <XAxis dataKey="time" hide />
                        <YAxis fontSize={11} stroke={chart.axis} axisLine={false} tickLine={false} />
                        <Tooltip
                            contentStyle={chart.tooltipStyle}
                            labelStyle={chart.tooltipLabelStyle}
                            itemStyle={chart.tooltipItemStyle}
                        />
                        <Legend wrapperStyle={chart.legendStyle} />
                        <Area type="monotone" dataKey="redisMem" stackId="1" stroke={chart.primary} fill={chart.primary} fillOpacity={0.24} name="Redis内存(MB)" />
                        <Area type="monotone" dataKey="backlog" stackId="2" stroke={chart.secondary} fill={chart.secondary} fillOpacity={0.24} name="总任务积压" />
                    </AreaChart>
                </ResponsiveContainer>
            </Box>
        </Paper>
    );
}

export default ResourceTrendChart;
