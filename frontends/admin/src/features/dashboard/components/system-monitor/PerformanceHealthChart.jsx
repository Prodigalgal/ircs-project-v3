import React from 'react';
import { Paper, Typography } from '@mui/material';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

function PerformanceHealthChart({ chartData }) {
    return (
        <Paper variant="outlined" sx={{ p: 3, height: 400 }}>
            <Typography variant="h6" gutterBottom sx={{ fontWeight: '500', mb: 2 }}>速率与积压趋势对比 (Performance Health)</Typography>
            <ResponsiveContainer width="100%" height="90%">
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                    <XAxis dataKey="time" hide />
                    <YAxis yAxisId="left" stroke="#8884d8" fontSize={12} label={{ value: 'TPM (条/分)', angle: -90, position: 'insideLeft', style: { fill: '#8884d8' } }} />
                    <YAxis yAxisId="right" orientation="right" stroke="#ffc658" fontSize={12} label={{ value: '积压数量', angle: 90, position: 'insideRight', style: { fill: '#ffc658' } }} />
                    <Tooltip contentStyle={{ backgroundColor: '#1e1e1e', borderColor: '#333', borderRadius: '4px' }} />
                    <Legend verticalAlign="top" height={36}/>
                    <Line yAxisId="left" type="monotone" dataKey="pageDiscovery" stroke="#8884d8" strokeWidth={2} dot={false} name="PT发现" />
                    <Line yAxisId="left" type="monotone" dataKey="detailCollection" stroke="#38bdf8" strokeWidth={2} dot={false} name="DT采集" />
                    <Line yAxisId="left" type="monotone" dataKey="normalization" stroke="#82ca9d" strokeWidth={2} dot={false} name="清洗" />
                    <Line yAxisId="left" type="monotone" dataKey="metadata" stroke="#00C49F" strokeWidth={2} dot={false} name="元数据" />
                    <Line yAxisId="right" type="monotone" dataKey="backlog" stroke="#ffc658" strokeWidth={3} dot={false} name="全队总积压" />
                </LineChart>
            </ResponsiveContainer>
        </Paper>
    );
}

export default PerformanceHealthChart;
