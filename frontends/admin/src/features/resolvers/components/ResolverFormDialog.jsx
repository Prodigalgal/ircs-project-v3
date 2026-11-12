// File: ircs-frontend/src/features/settings/components/ResolverFormDialog.jsx
import React, { useState, useEffect } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, Box, IconButton, Typography,
    Switch, FormControlLabel, Stack, Paper
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';

function ResolverFormDialog({ open, onClose, onSubmit, item }) {
    const [name, setName] = useState('');
    const [remark, setRemark] = useState('');
    const [isActive, setIsActive] = useState(true);
    const [lines, setLines] = useState([{ name: '默认线路', url: '' }]);

    useEffect(() => {
        if (open) {
            if (item) {
                setName(item.name || '');
                setRemark(item.remark || '');
                setIsActive(item.isActive ?? true);
                setLines(item.lines && item.lines.length > 0 ? item.lines : [{ name: '默认线路', url: '' }]);
            } else {
                setName('');
                setRemark('');
                setIsActive(true);
                setLines([{ name: '默认线路', url: '' }]);
            }
        }
    }, [item, open]);

    const handleLineChange = (index, field, value) => {
        const newLines = [...lines];
        newLines[index] = { ...newLines[index], [field]: value };
        setLines(newLines);
    };

    const handleAddLine = () => {
        setLines([...lines, { name: `线路${lines.length + 1}`, url: '' }]);
    };

    const handleDeleteLine = (index) => {
        if (lines.length === 1) return;
        const newLines = lines.filter((_, i) => i !== index);
        setLines(newLines);
    };

    const handleSubmit = () => {
        if (!name.trim()) return alert('名称不能为空');

        // 过滤空行
        const validLines = lines.filter(l => l.url && l.url.trim() !== '');
        if (validLines.length === 0) return alert('至少需要配置一条有效的解析URL');

        onSubmit({
            id: item?.id,
            name,
            remark,
            isActive,
            lines: validLines
        });
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>{item ? '编辑解析源' : '新建解析源'}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    <TextField
                        label="解析商名称"
                        fullWidth
                        required
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="例如: 789解析"
                    />
                    <TextField
                        label="备注"
                        fullWidth
                        value={remark}
                        onChange={(e) => setRemark(e.target.value)}
                    />
                    <FormControlLabel
                        control={<Switch checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />}
                        label="启用此解析源"
                    />

                    <Box>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                            <Typography variant="subtitle2">解析线路配置</Typography>
                            <Button startIcon={<AddIcon />} size="small" onClick={handleAddLine}>添加线路</Button>
                        </Box>

                        {lines.map((line, index) => (
                            <Paper key={index} variant="outlined" sx={{ p: 2, mb: 2, bgcolor: 'action.hover' }}>
                                <Stack direction="row" spacing={2} sx={{
                                    alignItems: "center"
                                }}>
                                    <TextField
                                        label="线路名称"
                                        size="small"
                                        value={line.name}
                                        onChange={(e) => handleLineChange(index, 'name', e.target.value)}
                                        sx={{ width: '30%' }}
                                    />
                                    <TextField
                                        label="接口 URL (含参数)"
                                        size="small"
                                        value={line.url}
                                        onChange={(e) => handleLineChange(index, 'url', e.target.value)}
                                        sx={{ flex: 1 }}
                                        placeholder="https://api.example.com/?url="
                                        helperText={index === lines.length - 1 ? "系统会自动将视频地址拼接在URL末尾" : null}
                                    />
                                    <IconButton
                                        color="error"
                                        onClick={() => handleDeleteLine(index)}
                                        disabled={lines.length === 1}
                                    >
                                        <DeleteIcon />
                                    </IconButton>
                                </Stack>
                            </Paper>
                        ))}
                    </Box>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit}>保存</Button>
            </DialogActions>
        </Dialog>
    );
}

export default ResolverFormDialog;