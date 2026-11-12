// File: ircs-frontend/src/features/dashboard/components/SourceQualityTable.jsx
import React from 'react';
import {
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Paper,
    LinearProgress, Box, Typography
} from '@mui/material';

function QualityScore({ score }) {
    let color = 'success';
    if (score < 60) color = 'error';
    else if (score < 80) color = 'warning';

    return (
        <Box sx={{ display: 'flex', alignItems: 'center' }}>
            <Box sx={{ width: '100%', mr: 1 }}>
                <LinearProgress variant="determinate" value={score} color={color} />
            </Box>
            <Box sx={{ minWidth: 35 }}>
                <Typography variant="body2" sx={{
                    color: "text.secondary"
                }}>{score}</Typography>
            </Box>
        </Box>
    );
}

function SourceQualityTable({ data, loading }) {
    if (loading && (!data || data.length === 0)) {
        return (
            <Box sx={{ width: '100%', mt: 2 }}>
                <LinearProgress />
            </Box>
        );
    }

    if (!data || data.length === 0) {
        return <Typography align="center" sx={{ p: 4 }}>暂无数据</Typography>;
    }

    return (
        <Box sx={{ mt: 3, position: 'relative' }}>
            {loading && <LinearProgress sx={{ position: 'absolute', top: -4, left: 0, right: 0, zIndex: 1 }} />}
            <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>数据源名称</TableCell>
                            <TableCell align="right">总视频数</TableCell>
                            <TableCell align="right" sx={{ width: 150 }}>健康度评分</TableCell>
                            <TableCell align="right">完美匹配 (Done)</TableCell>
                            <TableCell align="right">ID缺失 (No ID)</TableCell>
                            <TableCell align="right">归一化失败 (Parse Err)</TableCell>
                            <TableCell align="right">图片/死链 (Media Err)</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {data.map((row) => (
                            <TableRow key={row.dataSourceId}>
                                <TableCell component="th" scope="row" sx={{ fontWeight: 'bold' }}>{row.dataSourceName}</TableCell>
                                <TableCell align="right">{row.totalCount}</TableCell>
                                <TableCell align="right">
                                    <QualityScore score={row.qualityScore} />
                                </TableCell>
                                <TableCell align="right" sx={{ color: 'success.main' }}>{row.perfectCount}</TableCell>
                                <TableCell align="right" sx={{ color: 'warning.main' }}>{row.missingDoubanCount + row.missingTmdbCount}</TableCell>
                                <TableCell align="right" sx={{ color: 'error.main' }}>{row.normalizeFailedCount}</TableCell>
                                <TableCell align="right" sx={{ color: 'error.main' }}>{row.imageErrorCount}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}

export default SourceQualityTable;