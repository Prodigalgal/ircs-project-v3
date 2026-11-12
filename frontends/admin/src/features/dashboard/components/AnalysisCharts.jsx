// File: ircs-frontend/src/features/dashboard/components/AnalysisCharts.jsx
import React from 'react';
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer,
    PieChart, Pie, Cell, Legend, BarChart, Bar, ComposedChart, Line
} from 'recharts';
import { Paper, Typography, Box } from '@mui/material';

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8', '#82ca9d', '#ffc658', '#8dd1e1'];

// ID 覆盖率特定配色
const COVERAGE_COLORS = {
    '双ID齐全': '#00C49F',  // Success Green
    '仅豆瓣': '#007722',   // Douban Brand Green
    '仅TMDB': '#01b4e4',   // TMDB Brand Blue
    '缺失ID': '#FF8042'    // Warning Orange/Red
};

const ChartContainer = ({ title, children, height = 400 }) => (
    <Paper
        variant="outlined"
        sx={{
            p: 2,
            height: height,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden'
        }}
    >
        <Typography variant="h6" gutterBottom noWrap>
            {title}
        </Typography>

        <Box sx={{ flex: 1, width: '100%', minHeight: 0, position: 'relative' }}>
            <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}>
                <ResponsiveContainer width="100%" height="100%">
                    {children}
                </ResponsiveContainer>
            </div>
        </Box>
    </Paper>
);

export function TrendChart({ data, days }) {
    const safeData = data || [];

    return (
        <ChartContainer title={`入库趋势 (近${days}天)`} height={400}>
            <AreaChart data={safeData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <defs>
                    <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#8884d8" stopOpacity={0.8}/>
                        <stop offset="95%" stopColor="#8884d8" stopOpacity={0}/>
                    </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#444" />
                <XAxis dataKey="label" stroke="#888" tick={{ fontSize: 12 }} />
                <YAxis stroke="#888" />
                <RechartsTooltip
                    contentStyle={{ backgroundColor: '#333', border: '1px solid #555' }}
                    labelStyle={{ color: '#ccc' }}
                />
                <Area type="monotone" dataKey="value" stroke="#8884d8" fillOpacity={1} fill="url(#colorValue)" name="视频数" />
            </AreaChart>
        </ChartContainer>
    );
}

export function CategoryPieChart({ data }) {
    const safeData = data || [];

    return (
        <ChartContainer title="内容分类分布">
            <PieChart>
                <Pie
                    data={safeData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={2}
                    dataKey="value"
                    nameKey="label"
                >
                    {safeData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                </Pie>
                <RechartsTooltip contentStyle={{ backgroundColor: '#333', border: '1px solid #555' }} />
                <Legend
                    layout="vertical"
                    verticalAlign="middle"
                    align="right"
                    wrapperStyle={{ paddingLeft: '10px' }}
                />
            </PieChart>
        </ChartContainer>
    );
}

export function StatusBarChart({ data }) {
    const safeData = data || [];

    return (
        <ChartContainer title="处理状态分布">
            <BarChart data={safeData} layout="vertical" margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#444" horizontal={false} />
                <XAxis type="number" stroke="#888" hide />
                <YAxis
                    dataKey="label"
                    type="category"
                    width={100}
                    stroke="#888"
                    tick={{ fontSize: 12, width: 100 }}
                />
                <RechartsTooltip cursor={{ fill: 'rgba(255,255,255,0.1)' }} contentStyle={{ backgroundColor: '#333', border: '1px solid #555' }} />
                <Bar dataKey="value" fill="#82ca9d" name="视频数" radius={[0, 4, 4, 0]} barSize={20}>
                    {safeData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                </Bar>
            </BarChart>
        </ChartContainer>
    );
}

export function IdCoverageChart({ data }) {
    const safeData = data || [];

    return (
        <ChartContainer title="权威ID覆盖率">
            <PieChart>
                <Pie
                    data={safeData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={2}
                    dataKey="value"
                    nameKey="label"
                >
                    {safeData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COVERAGE_COLORS[entry.label] || COLORS[index % COLORS.length]} />
                    ))}
                </Pie>
                <RechartsTooltip contentStyle={{ backgroundColor: '#333', border: '1px solid #555' }} />
                <Legend
                    layout="vertical"
                    verticalAlign="middle"
                    align="right"
                    wrapperStyle={{ paddingLeft: '10px' }}
                />
            </PieChart>
        </ChartContainer>
    );
}

export function EfficiencyChart({ title, data }) {
    const safeData = data || [];

    return (
        <ChartContainer title={title}>
            <ComposedChart
                data={safeData}
                margin={{ top: 20, right: 20, bottom: 20, left: 20 }}
            >
                <CartesianGrid stroke="#f5f5f5" strokeOpacity={0.1} />
                <XAxis dataKey="name" scale="band" stroke="#888" tick={{ fontSize: 10 }} interval={0} angle={-30} textAnchor="end" height={60} />
                <YAxis yAxisId="left" stroke="#888" label={{ value: '总数', angle: -90, position: 'insideLeft', fill: '#888' }} />
                <YAxis yAxisId="right" orientation="right" stroke="#82ca9d" label={{ value: '成功率 %', angle: 90, position: 'insideRight', fill: '#82ca9d' }} />
                <RechartsTooltip contentStyle={{ backgroundColor: '#333', border: '1px solid #555' }} />
                <Legend />
                <Bar yAxisId="left" dataKey="total" barSize={20} fill="#413ea0" name="总处理量" />
                <Line yAxisId="right" type="monotone" dataKey="successRate" stroke="#82ca9d" name="成功率" strokeWidth={2} dot={{ r: 4 }} />
            </ComposedChart>
        </ChartContainer>
    );
}