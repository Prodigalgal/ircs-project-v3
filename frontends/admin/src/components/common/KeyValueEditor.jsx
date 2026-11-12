// File: ircs-frontend/src/components/common/KeyValueEditor.jsx
import React, { useState, useEffect } from 'react';
import { Box, TextField, IconButton, Button, Typography, Stack } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';

/**
 * 通用键值对编辑器
 * @param {string} value - JSON 字符串 (e.g., '{"Key": "Value"}')
 * @param {function} onChange - 回调函数，返回新的 JSON 字符串
 */
function KeyValueEditor({ value, onChange, label = "HTTP Headers" }) {
    const [rows, setRows] = useState([]);

    // 初始化：将 JSON 字符串解析为数组
    useEffect(() => {
        try {
            if (!value || value === '{}') {
                setRows([]);
                return;
            }
            const parsed = JSON.parse(value);
            const newRows = Object.entries(parsed).map(([k, v]) => ({ key: k, value: v }));
            setRows(newRows);
        } catch (e) {
            console.error("Failed to parse key-value JSON", e);
            setRows([]);
        }
    }, [value]);

    // 更新：将数组序列化为 JSON 字符串并通知父组件
    const updateParent = (newRows) => {
        const obj = {};
        newRows.forEach(row => {
            if (row.key.trim()) {
                obj[row.key.trim()] = row.value;
            }
        });
        onChange(JSON.stringify(obj));
    };

    const handleAddRow = () => {
        const newRows = [...rows, { key: '', value: '' }];
        setRows(newRows);
        // 不立即更新 Parent，等到输入内容后再说，或者在此处更新也行
    };

    const handleDeleteRow = (index) => {
        const newRows = rows.filter((_, i) => i !== index);
        setRows(newRows);
        updateParent(newRows);
    };

    const handleRowChange = (index, field, val) => {
        const newRows = [...rows];
        newRows[index] = { ...newRows[index], [field]: val };
        setRows(newRows);
        // 实时更新
        updateParent(newRows);
    };

    return (
        <Box sx={{ border: '1px solid rgba(255, 255, 255, 0.23)', borderRadius: 1, p: 2, mt: 1 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="caption" sx={{
                    color: "text.secondary"
                }}>{label}</Typography>
                <Button startIcon={<AddIcon />} size="small" onClick={handleAddRow}>添加</Button>
            </Box>

            {rows.length === 0 && (
                <Typography
                    variant="body2"
                    align="center"
                    sx={{
                        color: "text.secondary",
                        py: 1
                    }}>
                    暂无自定义 Header
                </Typography>
            )}

            <Stack spacing={1}>
                {rows.map((row, index) => (
                    <Box key={index} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <TextField
                            label="Key"
                            size="small"
                            value={row.key}
                            onChange={(e) => handleRowChange(index, 'key', e.target.value)}
                            sx={{ width: '40%' }}
                            placeholder="e.g., Cookie"
                        />
                        <TextField
                            label="Value"
                            size="small"
                            value={row.value}
                            onChange={(e) => handleRowChange(index, 'value', e.target.value)}
                            sx={{ width: '50%' }}
                            placeholder="Value"
                        />
                        <IconButton size="small" color="error" onClick={() => handleDeleteRow(index)}>
                            <DeleteIcon />
                        </IconButton>
                    </Box>
                ))}
            </Stack>
        </Box>
    );
}

export default KeyValueEditor;