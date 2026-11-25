// File: frontends/admin/src/features/dashboard/components/AnalysisCharts.jsx
import React from 'react';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer,
    PieChart, Pie, Cell, Legend, BarChart, Bar, ComposedChart, Line,
} from 'recharts';
import { Paper, Typography, Box } from '@mui/material';
import { useChartTheme } from '../../../theme/useChartTheme';

const ChartContainer = ({ title, subtitle, children, height = 380 }) => (
    <Paper
        variant="outlined"
        sx={{
            p: 2.5,
            height,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            bgcolor: 'background.paper',
        }}
    >
        <Box sx={{ mb: 1.75 }}>
            <Typography variant="subtitle1" noWrap sx={{ fontWeight: 700, color: 'text.primary' }}>
                {title}
            </Typography>
            {subtitle && (
                <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
                    {subtitle}
                </Typography>
            )}
        </Box>

        <Box sx={{ flex: 1, width: '100%', minHeight: 0, position: 'relative' }}>
            <div style={{ position: 'absolute', inset: 0 }}>
                <ResponsiveContainer width="100%" height="100%">
                    {children}
                </ResponsiveContainer>
            </div>
        </Box>
    </Paper>
);

export function TrendChart({ data, days }) {
    const safeData = data || [];
    const chart = useChartTheme();

    return (
        <ChartContainer title={`入库趋势 (近 ${days} 天)`} subtitle="每日新增入库视频量变化" height={370}>
            <AreaChart data={safeData} margin={{ top: 8, right: 20, left: 0, bottom: 0 }}>
                <defs>
                    <linearGradient id="ircsTrendGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={chart.primary} stopOpacity={0.34} />
                        <stop offset="95%" stopColor={chart.primary} stopOpacity={0} />
                    </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} vertical={false} />
                <XAxis dataKey="label" stroke={chart.axis} tick={{ fontSize: 11, fill: chart.axis }} axisLine={{ stroke: chart.axisLine }} tickLine={false} />
                <YAxis stroke={chart.axis} tick={{ fontSize: 11, fill: chart.axis }} axisLine={false} tickLine={false} />
                <RechartsTooltip
                    contentStyle={chart.tooltipStyle}
                    labelStyle={chart.tooltipLabelStyle}
                    itemStyle={chart.tooltipItemStyle}
                />
                <Area
                    type="monotone"
                    dataKey="value"
                    stroke={chart.primary}
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#ircsTrendGradient)"
                    name="视频数"
                />
            </AreaChart>
        </ChartContainer>
    );
}

export function CategoryPieChart({ data }) {
    const safeData = data || [];
    const chart = useChartTheme();

    return (
        <ChartContainer title="内容分类分布">
            <PieChart>
                <Pie
                    data={safeData}
                    cx="44%"
                    cy="50%"
                    innerRadius={62}
                    outerRadius={98}
                    paddingAngle={2}
                    dataKey="value"
                    nameKey="label"
                    stroke="none"
                >
                    {safeData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={chart.series[index % chart.series.length]} />
                    ))}
                </Pie>
                <RechartsTooltip
                    contentStyle={chart.tooltipStyle}
                    labelStyle={chart.tooltipLabelStyle}
                    itemStyle={chart.tooltipItemStyle}
                />
                <Legend
                    layout="vertical"
                    verticalAlign="middle"
                    align="right"
                    wrapperStyle={{ ...chart.legendStyle, paddingLeft: 10 }}
                />
            </PieChart>
        </ChartContainer>
    );
}

export function StatusBarChart({ data }) {
    const safeData = data || [];
    const chart = useChartTheme();

    return (
        <ChartContainer title="处理状态分布">
            <BarChart data={safeData} layout="vertical" margin={{ top: 4, right: 24, left: 20, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} horizontal={false} />
                <XAxis type="number" stroke={chart.axis} hide />
                <YAxis
                    dataKey="label"
                    type="category"
                    width={104}
                    stroke={chart.axis}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: chart.axis }}
                />
                <RechartsTooltip
                    cursor={{ fill: chart.cursorFill }}
                    contentStyle={chart.tooltipStyle}
                    labelStyle={chart.tooltipLabelStyle}
                    itemStyle={chart.tooltipItemStyle}
                />
                <Bar dataKey="value" fill={chart.primary} name="视频数" radius={[0, 6, 6, 0]} barSize={18}>
                    {safeData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={chart.series[index % chart.series.length]} />
                    ))}
                </Bar>
            </BarChart>
        </ChartContainer>
    );
}

export function IdCoverageChart({ data }) {
    const safeData = data || [];
    const chart = useChartTheme();

    return (
        <ChartContainer title="权威 ID 覆盖率">
            <PieChart>
                <Pie
                    data={safeData}
                    cx="44%"
                    cy="50%"
                    innerRadius={62}
                    outerRadius={98}
                    paddingAngle={2}
                    dataKey="value"
                    nameKey="label"
                    stroke="none"
                >
                    {safeData.map((entry, index) => (
                        <Cell
                            key={`cell-${index}`}
                            fill={chart.coverage[entry.label] || chart.series[index % chart.series.length]}
                        />
                    ))}
                </Pie>
                <RechartsTooltip
                    contentStyle={chart.tooltipStyle}
                    labelStyle={chart.tooltipLabelStyle}
                    itemStyle={chart.tooltipItemStyle}
                />
                <Legend
                    layout="vertical"
                    verticalAlign="middle"
                    align="right"
                    wrapperStyle={{ ...chart.legendStyle, paddingLeft: 10 }}
                />
            </PieChart>
        </ChartContainer>
    );
}

export function EfficiencyChart({ title, data }) {
    const safeData = data || [];
    const chart = useChartTheme();

    return (
        <ChartContainer title={title}>
            <ComposedChart
                data={safeData}
                margin={{ top: 12, right: 16, bottom: 20, left: 10 }}
            >
                <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} vertical={false} />
                <XAxis
                    dataKey="name"
                    scale="band"
                    stroke={chart.axis}
                    axisLine={{ stroke: chart.axisLine }}
                    tickLine={false}
                    tick={{ fontSize: 10, fill: chart.axis }}
                    interval={0}
                    angle={-28}
                    textAnchor="end"
                    height={56}
                />
                <YAxis
                    yAxisId="left"
                    stroke={chart.axis}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: chart.axis }}
                    label={{ value: '总数', angle: -90, position: 'insideLeft', fill: chart.axis, fontSize: 11 }}
                />
                <YAxis
                    yAxisId="right"
                    orientation="right"
                    stroke={chart.secondary}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: chart.secondary }}
                    label={{ value: '成功率 %', angle: 90, position: 'insideRight', fill: chart.secondary, fontSize: 11 }}
                />
                <RechartsTooltip
                    cursor={{ fill: chart.cursorFill }}
                    contentStyle={chart.tooltipStyle}
                    labelStyle={chart.tooltipLabelStyle}
                    itemStyle={chart.tooltipItemStyle}
                />
                <Legend wrapperStyle={chart.legendStyle} />
                <Bar yAxisId="left" dataKey="total" barSize={18} fill={chart.primary} radius={[4, 4, 0, 0]} name="总处理量" />
                <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="successRate"
                    stroke={chart.secondary}
                    name="成功率"
                    strokeWidth={2}
                    dot={{ r: 3.5, fill: chart.secondary }}
                />
            </ComposedChart>
        </ChartContainer>
    );
}
