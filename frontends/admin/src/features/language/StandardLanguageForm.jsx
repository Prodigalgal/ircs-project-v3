import React, { useState, useEffect } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Stack } from '@mui/material';

function StandardLanguageForm({ open, onClose, onSubmit, item }) {
    const [name, setName] = useState('');
    const [code, setCode] = useState('');

    useEffect(() => {
        if (open) {
            setName(item ? item.name : '');
            setCode(item ? item.code : '');
        }
    }, [item, open]);

    const handleSubmit = () => {
        if (!name.trim()) return alert("名称不能为空");
        onSubmit({ id: item?.id, name, code });
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
            <DialogTitle>{item ? '编辑标准语言' : '新建标准语言'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        autoFocus
                        label="语言名称"
                        fullWidth
                        required
                        value={name}
                        onChange={e => setName(e.target.value)}
                        placeholder="如: 德语"
                    />
                    <TextField
                        label="ISO 代码 (可选)"
                        fullWidth
                        value={code}
                        onChange={e => setCode(e.target.value)}
                        placeholder="如: de"
                        helperText="建议遵循 ISO 639-1 标准"
                    />
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit}>{item ? '更新' : '创建'}</Button>
            </DialogActions>
        </Dialog>
    );
}

export default StandardLanguageForm;