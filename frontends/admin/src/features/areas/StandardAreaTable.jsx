import React, { useState, useMemo } from 'react';
import {
    Box, Button, Dialog, DialogTitle, DialogContent, DialogActions,
    TextField, Stack
} from '@mui/material';
import { useGetStandardAreasQuery, useCreateStandardAreaMutation } from './areasApi';
import SmartTable from '../../components/common/SmartTable';
import ListHeader from '../../components/common/ListHeader';

function AreaForm({ open, onClose, onSubmit }) {
    const [formData, setFormData] = useState({ name: '', code: '', region: '' });

    const handleSubmit = () => {
        if (!formData.name.trim() || !formData.code.trim()) return alert("名称和代码不能为空");
        onSubmit(formData);
        setFormData({ name: '', code: '', region: '' });
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>新建标准地区</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        autoFocus
                        label="地区名称"
                        fullWidth
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        placeholder="如: 中国大陆"
                    />
                    <TextField
                        label="地区代码 (ISO)"
                        fullWidth
                        value={formData.code}
                        onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                        placeholder="如: CN"
                    />
                    <TextField
                        label="所属大区 (Region)"
                        fullWidth
                        value={formData.region}
                        onChange={(e) => setFormData({ ...formData, region: e.target.value })}
                        placeholder="如: Asia"
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit}>创建</Button>
            </DialogActions>
        </Dialog>
    );
}

function StandardAreaTable() {
    const [isFormOpen, setIsFormOpen] = useState(false);

    const { data: areas = [], isLoading, isFetching, refetch } = useGetStandardAreasQuery();
    const [createArea] = useCreateStandardAreaMutation();

    const handleSubmit = async (data) => {
        try {
            await createArea(data).unwrap();
            setIsFormOpen(false);
        } catch (e) {
            alert('创建失败: ' + (e.data?.message || e.message));
        }
    };

    const columns = useMemo(() => [
        { field: 'name', headerName: '地区名称', width: 200 },
        { field: 'code', headerName: '代码 (Code)', width: 150 },
        { field: 'region', headerName: '所属大区', width: 150, valueGetter: (v) => v || '-' },
        { field: 'id', headerName: 'ID', width: 300, valueGetter: (v) => v || '-' },
    ], []);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <ListHeader
                title="标准地区库"
                onAdd={() => setIsFormOpen(true)}
                addLabel="新建标准地区"
                onRefresh={refetch}
                loading={isLoading || isFetching}
            />

            <SmartTable
                columns={columns}
                data={areas}
                total={areas.length}
                loading={isLoading || isFetching}
                page={0}
                pageSize={100}
                onPageChange={() => {}}
                onPageSizeChange={() => {}}
            />

            <AreaForm
                open={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSubmit={handleSubmit}
            />
        </Box>
    );
}

export default StandardAreaTable;