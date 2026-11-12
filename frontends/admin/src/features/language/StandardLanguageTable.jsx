
import React, { useState, useCallback } from 'react';
import {
    Box, IconButton, Stack, Typography, Paper, TableContainer, Table, TableHead, TableRow, TableCell, TableBody
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useGetStandardLanguagesQuery, useCreateStandardLanguageMutation } from './standardLanguagesApi';
import StandardLanguageForm from './StandardLanguageForm';
import ListHeader from '../../components/common/ListHeader';

function StandardLanguageTable() {
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);

    const { data: languages = [], isLoading, isFetching, refetch } = useGetStandardLanguagesQuery();
    const [createStandard] = useCreateStandardLanguageMutation();
    // 暂未提供 update API，若后续后端支持，可在此引入

    const handleNew = useCallback(() => {
        setEditingItem(null);
        setIsFormOpen(true);
    }, []);

    const handleEdit = useCallback((item) => {
        setEditingItem(item);
        setIsFormOpen(true);
    }, []);

    const handleFormSubmit = async (data) => {
        try {
            if (data.id) {
                alert("暂不支持编辑标准语言 (API Pending)");
                // await updateStandard(data).unwrap();
            } else {
                await createStandard(data).unwrap();
            }
            setIsFormOpen(false);
        } catch (e) {
            alert("操作失败: " + (e.data?.message || e.message));
        }
    };

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="标准语言库"
                onAdd={handleNew}
                addLabel="添加标准语言"
                onRefresh={refetch}
                loading={isLoading || isFetching}
            />

            <Paper variant="outlined" sx={{ flex: 1, overflow: 'auto' }}>
                <TableContainer>
                    <Table size="small" stickyHeader>
                        <TableHead>
                            <TableRow>
                                <TableCell>名称</TableCell>
                                <TableCell>代码 (ISO)</TableCell>
                                <TableCell>ID</TableCell>
                                <TableCell align="right">操作</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {languages.map((std) => (
                                <TableRow key={std.id} hover>
                                    <TableCell sx={{ fontWeight: 'bold' }}>{std.name}</TableCell>
                                    <TableCell sx={{ fontFamily: 'monospace' }}>{std.code || '-'}</TableCell>
                                    <TableCell sx={{ color: 'text.secondary', fontSize: '0.8rem' }}>{std.id}</TableCell>
                                    <TableCell align="right">
                                        <Stack direction="row" spacing={1} sx={{
                                            justifyContent: "flex-end"
                                        }}>
                                            <IconButton size="small" onClick={() => handleEdit(std)} disabled>
                                                <EditIcon fontSize="small" />
                                            </IconButton>
                                            <IconButton size="small" color="error" disabled>
                                                <DeleteIcon fontSize="small" />
                                            </IconButton>
                                        </Stack>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Paper>

            <StandardLanguageForm
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleFormSubmit}
                item={editingItem}
            />
        </Box>
    );
}

export default StandardLanguageTable;
