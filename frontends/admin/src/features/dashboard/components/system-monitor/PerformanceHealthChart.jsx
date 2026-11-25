// File: frontends/admin/src/features/dashboard/components/system-monitor/PerformanceHealthChart.jsx
import React from 'react';
import { Paper, Typography } from '@mui/material';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useChartTheme } from '../../../../theme/useChartTheme';

function PerformanceHealthChart({ chartData }) {
    const chart = useChartTheme();

    return (
        <Paper variant="outlined" sx={{ p: 2.5, height: 390, bgcolor: 'background.paper' }}>
            <Typography variant="subtitle1" gutterBottom sx={{ fontWeight: 700, mb: 2 }}>
                速率与积压趋势对比 (Performance Health)
            </Typography>
            <ResponsiveContainer width="100%" height="88%">
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} vertical={false} />
                    <XAxis dataKey="time" hide />
                    <YAxis
                        yAxisId="left"
                        stroke={chart.primary}
                        axisLine={false}
                        tickLine={false}
                        fontSize={11}
                        label={{ value: 'TPM (条/分)', angle: -90, position: 'insideLeft', style: { fill: chart.primary, fontSize: 11 } }}
                    />
                    <YAxis
                        yAxisId="right"
                        orientation="right"
                        stroke={chart.warning}
                        axisLine={false}
                        tickLine={false}
                        fontSize={11}
                        label={{ value: '积压数量', angle: 90, position: 'insideRight', style: { fill: chart.warning, fontSize: 11 } }}
                    />
                    <Tooltip
                        contentStyle={chart.tooltipStyle}
                        labelStyle={chart.tooltipLabelStyle}
                        itemStyle={chart.tooltipItemStyle}
                    />
                    <Legend verticalAlign="top" height={34} wrapperStyle={chart.legendStyle} />
                    <Line yAxisId="left" type="monotone" dataKey="pageDiscovery" stroke={chart.primary} strokeWidth={2} dot={false} name="PT发现" />
                    <Line yAxisId="left" type="monotone" dataKey="detailCollection" stroke={chart.info} strokeWidth={2} dot={false} name="DT采集" />
                    <Line yAxisId="left" type="monotone" dataKey="normalization" stroke={chart.secondary} strokeWidth={2} dot={false} name="清洗" />
                    <Line yAxisId="left" type="monotone" dataKey="metadata" stroke={chart.success} strokeWidth={2} dot={false} name="元数据" />
                    <Line yAxisId="right" type="monotone" dataKey="backlog" stroke={chart.warning} strokeWidth={2.5} dot={false} name="全队总积压" />
                </LineChart>
            </ResponsiveContainer>
        </Paper>
    );
}

export default PerformanceHealthChart;
