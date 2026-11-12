// File: ircs-frontend/src/features/categories/CategoryForm.jsx
import React, { useState, useEffect } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Stack } from '@mui/material';

function CategoryForm({ open, onClose, onSubmit, item }) {
    const [name, setName] = useState('');
    const [slug, setSlug] = useState('');

    useEffect(() => {
        if (open) {
            setName(item ? item.name : '');
            setSlug(item ? item.slug : '');
        }
    }, [item, open]);

    const handleSubmit = () => {
        if (!name.trim()) {
            alert('名称不能为空。');
            return;
        }
        if (!slug.trim()) {
            alert('标识符 (Slug) 不能为空。');
            return;
        }
        onSubmit({ id: item?.id, name, slug });
    };

    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
            <DialogTitle>{item ? '编辑标准分类' : '新建标准分类'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        autoFocus
                        name="name"
                        label="分类名称"
                        type="text"
                        fullWidth
                        variant="outlined"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="例如: 电影"
                    />
                    <TextField
                        name="slug"
                        label="标识符 (Slug)"
                        type="text"
                        fullWidth
                        variant="outlined"
                        value={slug}
                        onChange={(e) => setSlug(e.target.value)}
                        placeholder="例如: movie"
                        helperText="用于 URL 路径的唯一英文标识"
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained">{item ? '更新' : '创建'}</Button>
            </DialogActions>
        </Dialog>
    );
}
export default CategoryForm;