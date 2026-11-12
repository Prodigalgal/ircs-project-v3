// ircs-frontend/src/components/common/SmartTable.jsx
import React, { memo, useCallback, useMemo } from 'react';
import {
    Box,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TablePagination,
    Checkbox,
    Paper,
    CircularProgress,
    Typography,
    TableSortLabel,
    Stack
} from '@mui/material';
import PropTypes from 'prop-types';

const CELL_STYLE = {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxWidth: 0,
};

const headCellStyle = (width) => ({
    fontWeight: 'bold',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    width: width || 'auto'
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
                                                noDataMessage = '暂无数据'
                                            }) {

    const selectedKeys = useMemo(() => new Set(selectionModel || []), [selectionModel]);

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
        <Paper sx={{ width: '100%', mb: 0, display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
            <Box sx={{ position: 'relative', flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                {loading && (
                    <Box sx={{
                        position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        backgroundColor: 'rgba(0, 0, 0, 0.1)', zIndex: 1
                    }}>
                        <CircularProgress />
                    </Box>
                )}
                <TableContainer sx={{
                    flex: 1,
                    overflowY: 'auto',
                    overflowX: 'hidden'
                }}>
                    <Table
                        stickyHeader
                        size="small"
                        sx={{
                            tableLayout: 'fixed',
                            width: '100%'
                        }}
                    >
                        <TableHead>
                            <TableRow>
                                {onSelectionChange && (
                                    <TableCell padding="checkbox" sx={{ width: 50, ...headCellStyle(50) }}>
                                        <Checkbox
                                            color="primary"
                                            indeterminate={selectedKeys.size > 0 && selectedKeys.size < data.length}
                                            checked={data.length > 0 && selectedKeys.size === data.length}
                                            onChange={handleSelectAllClick}
                                            disabled={loading}
                                        />
                                    </TableCell>
                                )}
                                {columns.map((headCell) => (
                                    <TableCell
                                        key={headCell.field}
                                        align={headCell.align || 'left'}
                                        padding={headCell.disablePadding ? 'none' : 'normal'}
                                        sx={headCellStyle(headCell.width)}
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
                                <TableRow style={{ height: 100 }}>
                                    <TableCell colSpan={columns.length + (onSelectionChange ? 1 : 0)}>
                                        <Stack
                                            sx={{
                                                alignItems: "center",
                                                justifyContent: "center"
                                            }}>
                                            <Typography sx={{
                                                color: "text.secondary"
                                            }}>{noDataMessage}</Typography>
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
                                                <TableCell padding="checkbox" sx={{ width: 50 }}>
                                                    <Checkbox
                                                        color="primary"
                                                        checked={isItemSelected}
                                                        onClick={() => handleClick(row[rowKey])}
                                                    />
                                                </TableCell>
                                            )}
                                            {columns.map((column) => (
                                                <TableCell
                                                    key={column.field}
                                                    align={column.align || 'left'}
                                                    sx={column.width ? { ...CELL_STYLE, width: column.width, maxWidth: column.width } : CELL_STYLE}
                                                    title={typeof row[column.field] === 'string' ? row[column.field] : undefined} // 鼠标悬停显示完整内容
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
                    labelRowsPerPage="每页:"
                    labelDisplayedRows={({ from, to, count }) => `${from}-${to} / ${count}`}
                    sx={{ borderTop: 1, borderColor: 'divider' }}
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
    noDataMessage: PropTypes.string
};

export default SmartTable;
