// File: frontends/admin/src/components/common/SmartTable.jsx
import React, { memo, useCallback, useMemo } from 'react';
import {
    Box,
    Checkbox,
    LinearProgress,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TablePagination,
    TableRow,
    TableSortLabel,
    Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import PropTypes from 'prop-types';

const CELL_STYLE = {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxWidth: 0,
};

const headCellStyle = (width) => ({
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    width: width || 'auto',
});

const SmartTable = memo(function SmartTable({
    columns,
    data,
    total,
    loading,
    page,
    pageSize,
    onPageChange,
    onPageSizeChange,
    onSortChange,
    sortModel,
    selectionModel,
    onSelectionChange,
    rowKey = 'id',
    noDataMessage = '暂无数据',
}) {
    const selectedKeys = useMemo(() => new Set(selectionModel || []), [selectionModel]);

    const columnCount = columns.length + (onSelectionChange ? 1 : 0);
    const minTableWidth = useMemo(() => Math.min(820, Math.max(560, columnCount * 115)), [columnCount]);

    const resolvedColumnWidths = useMemo(() => {
        const allNumeric = columns.length > 0 && columns.every((col) => typeof col.width === 'number' && col.width > 0);
        if (!allNumeric) {
            return columns.map((col) => col.width || 'auto');
        }
        const totalWidth = columns.reduce((sum, col) => sum + col.width, 0);
        return columns.map((col) => `${((col.width / totalWidth) * 100).toFixed(2)}%`);
    }, [columns]);

    const handleSelectAllClick = useCallback((event) => {
        if (event.target.checked && data.length > 0) {
            const newSelecteds = data.map((n) => n[rowKey]);
            onSelectionChange && onSelectionChange(newSelecteds);
            return;
        }
        onSelectionChange && onSelectionChange([]);
    }, [data, onSelectionChange, rowKey]);

    const handleClick = useCallback((id) => {
        if (!onSelectionChange) return;
        const currentSelection = selectionModel || [];
        const selectedIndex = currentSelection.indexOf(id);
        let newSelected = [];

        if (selectedIndex === -1) {
            newSelected = newSelected.concat(currentSelection, id);
        } else if (selectedIndex === 0) {
            newSelected = newSelected.concat(currentSelection.slice(1));
        } else if (selectedIndex === currentSelection.length - 1) {
            newSelected = newSelected.concat(currentSelection.slice(0, -1));
        } else if (selectedIndex > 0) {
            newSelected = newSelected.concat(
                currentSelection.slice(0, selectedIndex),
                currentSelection.slice(selectedIndex + 1),
            );
        }
        onSelectionChange(newSelected);
    }, [onSelectionChange, selectionModel]);

    const isSelected = useCallback((id) => selectedKeys.has(id), [selectedKeys]);

    const createSortHandler = useCallback((property) => () => {
        if (!onSortChange) return;
        const isAsc = sortModel?.field === property && sortModel?.order === 'asc';
        onSortChange({ field: property, order: isAsc ? 'desc' : 'asc' });
    }, [onSortChange, sortModel]);

    return (
        <Paper
            variant="outlined"
            sx={{
                width: '100%',
                mb: 0,
                display: 'flex',
                flexDirection: 'column',
                flex: 1,
                minHeight: 0,
                overflow: 'hidden',
                bgcolor: 'background.paper',
            }}
        >
            <Box sx={{ position: 'relative', flex: 1, minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                {loading && (
                    <LinearProgress
                        sx={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            right: 0,
                            height: 2,
                            zIndex: 3,
                            borderRadius: 0,
                        }}
                    />
                )}

                {loading && data.length > 0 && (
                    <Box
                        sx={(theme) => ({
                            position: 'absolute',
                            inset: 0,
                            bgcolor: alpha(theme.palette.background.paper, 0.42),
                            zIndex: 2,
                            pointerEvents: 'none',
                        })}
                    />
                )}

                <TableContainer
                    sx={{
                        flex: 1,
                        overflowY: 'auto',
                        overflowX: 'auto',
                    }}
                >
                    <Table
                        stickyHeader
                        size="small"
                        sx={{
                            tableLayout: 'fixed',
                            width: '100%',
                            minWidth: minTableWidth,
                        }}
                    >
                        <TableHead>
                            <TableRow>
                                {onSelectionChange && (
                                    <TableCell padding="checkbox" sx={{ width: 48, ...headCellStyle(48) }}>
                                        <Checkbox
                                            size="small"
                                            color="primary"
                                            indeterminate={selectedKeys.size > 0 && selectedKeys.size < data.length}
                                            checked={data.length > 0 && selectedKeys.size === data.length}
                                            onChange={handleSelectAllClick}
                                            disabled={loading}
                                        />
                                    </TableCell>
                                )}
                                {columns.map((headCell, colIndex) => (
                                    <TableCell
                                        key={headCell.field}
                                        align={headCell.align || 'left'}
                                        padding={headCell.disablePadding ? 'none' : 'normal'}
                                        sx={headCellStyle(resolvedColumnWidths[colIndex])}
                                    >
                                        {headCell.sortable !== false && onSortChange ? (
                                            <TableSortLabel
                                                active={sortModel?.field === headCell.field}
                                                direction={sortModel?.field === headCell.field ? sortModel.order : 'asc'}
                                                onClick={createSortHandler(headCell.field)}
                                            >
                                                {headCell.headerName}
                                            </TableSortLabel>
                                        ) : (
                                            headCell.headerName
                                        )}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {data.length === 0 && !loading ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={columns.length + (onSelectionChange ? 1 : 0)}
                                        sx={{ borderBottom: 'none', py: 7 }}
                                    >
                                        <Stack spacing={1} sx={{ alignItems: 'center', justifyContent: 'center' }}>
                                            <InboxOutlinedIcon sx={{ fontSize: 34, color: 'text.disabled' }} />
                                            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                                                {noDataMessage}
                                            </Typography>
                                        </Stack>
                                    </TableCell>
                                </TableRow>
                            ) : (
                                data.map((row) => {
                                    const isItemSelected = isSelected(row[rowKey]);
                                    return (
                                        <TableRow
                                            hover
                                            role="checkbox"
                                            aria-checked={isItemSelected}
                                            tabIndex={-1}
                                            key={row[rowKey]}
                                            selected={isItemSelected}
                                        >
                                            {onSelectionChange && (
                                                <TableCell padding="checkbox" sx={{ width: 48 }}>
                                                    <Checkbox
                                                        size="small"
                                                        color="primary"
                                                        checked={isItemSelected}
                                                        onClick={() => handleClick(row[rowKey])}
                                                    />
                                                </TableCell>
                                            )}
                                            {columns.map((column, colIndex) => (
                                                <TableCell
                                                    key={column.field}
                                                    align={column.align || 'left'}
                                                    sx={{ ...CELL_STYLE, width: resolvedColumnWidths[colIndex] }}
                                                    title={typeof row[column.field] === 'string' ? row[column.field] : undefined}
                                                >
                                                    {column.renderCell
                                                        ? column.renderCell({ row, value: row[column.field] })
                                                        : (column.valueGetter ? column.valueGetter(row[column.field], row) : row[column.field])}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    );
                                })
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>

                <TablePagination
                    rowsPerPageOptions={[10, 20, 50, 100]}
                    component="div"
                    count={total || 0}
                    rowsPerPage={pageSize}
                    page={page}
                    onPageChange={(e, newPage) => onPageChange(newPage)}
                    onRowsPerPageChange={(e) => onPageSizeChange(parseInt(e.target.value, 10))}
                    labelRowsPerPage="每页"
                    labelDisplayedRows={({ from, to, count }) => `${from}–${to} / 共 ${count} 条`}
                    sx={{
                        borderTop: '1px solid',
                        borderColor: 'divider',
                        bgcolor: 'background.paper',
                        flexShrink: 0,
                    }}
                />
            </Box>
        </Paper>
    );
});

SmartTable.propTypes = {
    columns: PropTypes.array.isRequired,
    data: PropTypes.array.isRequired,
    total: PropTypes.number,
    loading: PropTypes.bool,
    page: PropTypes.number.isRequired,
    pageSize: PropTypes.number.isRequired,
    onPageChange: PropTypes.func.isRequired,
    onPageSizeChange: PropTypes.func.isRequired,
    onSortChange: PropTypes.func,
    sortModel: PropTypes.object,
    selectionModel: PropTypes.array,
    onSelectionChange: PropTypes.func,
    rowKey: PropTypes.string,
    noDataMessage: PropTypes.string,
};

export default SmartTable;
