// ircs-frontend/src/components/common/FilterPanel.jsx
import React, { useState } from 'react';
import { Box, Button, Collapse, Grid, TextField, MenuItem, Paper } from '@mui/material';
import FilterListIcon from '@mui/icons-material/FilterList';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';

function cleanFilterValues(rawValues) {
    return Object.fromEntries(
        Object.entries(rawValues || {}).filter(([, value]) => {
            if (value === null || value === undefined) return false;
            if (typeof value === 'string') return value.trim() !== '';
            return true;
        })
    );
}

function FilterPanel({ fields, onSearch, onReset, initialValues = {}, defaultExpanded = false }) {
    const [expanded, setExpanded] = useState(defaultExpanded);
    const [values, setValues] = useState(initialValues);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setValues((prev) => ({ ...prev, [name]: value }));
    };

    const handleSearch = () => {
        onSearch(cleanFilterValues(values));
    };

    const handleReset = () => {
        const resetValues = { ...initialValues };
        setValues(resetValues);
        onReset(cleanFilterValues(resetValues));
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter') {
            handleSearch();
        }
    };

    const renderField = (field) => {
        if (field.render) {
            return field.render(values[field.name], handleChange, values);
        }

        if (field.type === 'select' || field.type === 'boolean') {
            return (
                <TextField
                    select
                    fullWidth
                    label={field.label}
                    name={field.name}
                    value={values[field.name] || ''}
                    onChange={handleChange}
                    size="small"
                    variant="outlined"
                >
                    <MenuItem value="">
                        <em>全部</em>
                    </MenuItem>
                    {(field.type === 'boolean'
                        ? [{ value: 'true', label: '是' }, { value: 'false', label: '否' }]
                        : field.options || []
                    ).map((opt) => (
                        <MenuItem key={opt.value} value={opt.value}>
                            {opt.label}
                        </MenuItem>
                    ))}
                </TextField>
            );
        }

        return (
            <TextField
                fullWidth
                label={field.label}
                name={field.name}
                value={values[field.name] || ''}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                size="small"
                variant="outlined"
                placeholder={field.placeholder || ''}
                type={['number', 'date', 'datetime-local'].includes(field.type) ? field.type : 'text'}
                slotProps={{
                    inputLabel: field.type === 'date' || field.type === 'datetime-local' ? { shrink: true } : undefined
                }}
            />
        );
    };

    if (!fields || fields.length === 0) return null;

    return (
        <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                <Button
                    startIcon={<FilterListIcon />}
                    endIcon={expanded ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
                    onClick={() => setExpanded(!expanded)}
                    size="small"
                    variant="text"
                    color="inherit"
                >
                    {expanded ? '隐藏筛选条件' : '显示筛选条件'}
                </Button>
            </Box>

            <Collapse in={expanded}>
                <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
                    <Grid container spacing={2} sx={{
                        alignItems: "center"
                    }}>
                        {fields.map((field) => (
                            <Grid key={field.name} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                                {renderField(field)}
                            </Grid>
                        ))}
                        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                            <Box sx={{ display: 'flex', gap: 1 }}>
                                <Button
                                    variant="contained"
                                    startIcon={<SearchIcon />}
                                    onClick={handleSearch}
                                    size="medium"
                                >
                                    查询
                                </Button>
                                <Button
                                    variant="outlined"
                                    startIcon={<ClearIcon />}
                                    onClick={handleReset}
                                    size="medium"
                                >
                                    重置
                                </Button>
                            </Box>
                        </Grid>
                    </Grid>
                </Paper>
            </Collapse>
        </Box>
    );
}

export default FilterPanel;
