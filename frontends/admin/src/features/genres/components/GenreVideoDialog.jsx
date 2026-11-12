// File: src/features/genres/components/GenreVideoDialog.jsx
import React, { useState, useMemo } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, Box, Typography, Chip
} from '@mui/material';
import SmartTable from '../../../components/common/SmartTable';
import { useGetVideosQuery } from '../../videos/videosApi';
import { getTotalElements } from '../../../utils/paginationUtils';

function GenreVideoDialog({ open, onClose, genre, type }) {
    const [page, setPage] = useState(0);
    const [pageSize, setPageSize] = useState(10);

    // 根据类型构建查询参数
    const queryParams = useMemo(() => {
        if (!genre) return { page, size: pageSize };

        const params = {
            page,
            size: pageSize,
            sort: 'score,desc' // 默认按评分降序，更容易看到热门内容
        };

        if (type === 'STANDARD') {
            params.standardGenreId = genre.id;
        } else {
            params.rawGenreId = genre.id;
        }

        return params;
    }, [page, pageSize, genre, type]);

    const { data, isLoading, isFetching } = useGetVideosQuery(queryParams, {
        skip: !open || !genre
    });

    const columns = useMemo(() => [
        { field: 'title', headerName: '标题', width: 300 },
        { field: 'year', headerName: '年份', width: 80 },
        { field: 'area', headerName: '地区', width: 100 },
        {
            field: 'score',
            headerName: '评分',
            width: 80,
            renderCell: ({ value }) => (
                <Typography color={value > 0 ? "warning.main" : "text.secondary"} sx={{
                    fontWeight: "bold"
                }}>
                    {value > 0 ? value : '-'}
                </Typography>
            )
        },
        {
            field: 'dataSourceName',
            headerName: '数据源',
            width: 150,
            renderCell: ({ row }) => (
                <Box>
                    <Typography variant="body2">{row.dataSourceName}</Typography>
                    {row.sourceCategoryName && (
                        <Typography variant="caption" sx={{
                            color: "text.secondary"
                        }}>
                            {row.sourceCategoryName}
                        </Typography>
                    )}
                </Box>
            )
        }
    ], []);

    if (!open) return null;

    const titlePrefix = type === 'STANDARD' ? '标准题材' : '原始题材';
    const genreName = type === 'STANDARD' ? genre?.name : genre?.sourceValue;

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    关联视频列表
                    <Chip label={`${titlePrefix}: ${genreName}`} color="primary" size="small" variant="outlined" />
                </Box>
            </DialogTitle>

            <DialogContent dividers sx={{ p: 0, height: 500, display: 'flex', flexDirection: 'column' }}>
                <SmartTable
                    columns={columns}
                    data={data?.content || []}
                    total={getTotalElements(data)}
                    loading={isLoading || isFetching}
                    page={page}
                    pageSize={pageSize}
                    onPageChange={setPage}
                    onPageSizeChange={setPageSize}
                    noDataMessage="暂无关联视频"
                />
            </DialogContent>

            <DialogActions>
                <Button onClick={onClose}>关闭</Button>
            </DialogActions>
        </Dialog>
    );
}

export default GenreVideoDialog;