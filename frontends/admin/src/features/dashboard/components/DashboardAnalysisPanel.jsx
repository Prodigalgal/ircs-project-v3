import React from 'react';
import Grid from '@mui/material/Grid';
import {
    CategoryPieChart,
    StatusBarChart,
    TrendChart,
    IdCoverageChart,
    EfficiencyChart
} from './AnalysisCharts';

function DashboardAnalysisPanel({
    weeklyTrend,
    days,
    idCoverageData,
    enrichmentStatusDistribution,
    sourceEfficiencyData,
    categoryEfficiencyData,
    categoryDistribution
}) {
    return (
        <>
            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{ xs: 12 }}>
                    <TrendChart data={weeklyTrend} days={days} />
                </Grid>
            </Grid>

            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{ xs: 12, md: 6 }}>
                    <IdCoverageChart data={idCoverageData} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                    <StatusBarChart data={enrichmentStatusDistribution} />
                </Grid>
            </Grid>

            <Grid container spacing={3} sx={{ mb: 4 }}>
                <Grid size={{ xs: 12, md: 6 }}>
                    <EfficiencyChart title="数据源搜刮效率 (Top 10)" data={sourceEfficiencyData} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                    <EfficiencyChart title="分类处理效率 (Top 10)" data={categoryEfficiencyData} />
                </Grid>
            </Grid>

            <Grid container spacing={3}>
                <Grid size={{ xs: 12, md: 6 }}>
                    <CategoryPieChart data={categoryDistribution} />
                </Grid>
            </Grid>
        </>
    );
}

export default DashboardAnalysisPanel;
