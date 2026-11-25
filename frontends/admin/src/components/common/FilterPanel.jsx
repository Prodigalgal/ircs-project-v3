// File: frontends/admin/src/components/common/FilterPanel.jsx
import React, { useMemo, useState } from 'react';
import { Box, Button, Chip, Collapse, Grid, MenuItem, Paper, Stack, TextField } from '@mui/material';
import { alpha } from '@mui/material/styles';
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
        }),
    );
}

function FilterPanel({ fields, onSearch, onReset, initialValues = {}, defaultExpanded = false }) {
    const [expanded, setExpanded] = useState(defaultExpanded);
    const [values, setValues] = useState(initialValues);

    const activeCount = useMemo(
        () => Object.keys(cleanFilterValues(values)).length,
        [values],
    );

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
                    inputLabel: field.type === 'date' || field.type === 'datetime-local' ? { shrink: true } : undefined,
                }}
            />
        );
    };

    if (!fields || fields.length === 0) return null;

    return (
        <Box sx={{ mb: 1.75 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: expanded ? 1.25 : 0 }}>
                <Button
                    startIcon={<FilterListIcon sx={{ fontSize: '17px !important' }} />}
                    endIcon={expanded ? <KeyboardArrowUpIcon fontSize="small" /> : <KeyboardArrowDownIcon fontSize="small" />}
                    onClick={() => setExpanded((prev) => !prev)}
                    size="small"
                    variant="outlined"
                    color="inherit"
                    sx={(theme) => ({
                        borderColor: activeCount > 0 ? alpha(theme.palette.primary.main, 0.45) : 'divider',
                        bgcolor: activeCount > 0 ? alpha(theme.palette.primary.main, 0.06) : 'background.paper',
                        color: activeCount > 0 ? 'primary.main' : 'text.secondary',
                        px: 1.5,
                        '&:hover': {
                            borderColor: 'primary.main',
                            color: 'primary.main',
                        },
                    })}
                >
                    {expanded ? '收起筛选' : '筛选条件'}
                </Button>

                {activeCount > 0 && (
                    <Chip
                        size="small"
                        color="primary"
                        variant="outlined"
                        label={`已选 ${activeCount} 项`}
                        onDelete={handleReset}
                        sx={{ height: 26, fontSize: '0.74rem' }}
                    />
                )}
            </Stack>

            <Collapse in={expanded}>
                <Paper
                    variant="outlined"
                    sx={{
                        p: 2,
                        bgcolor: 'background.paper',
                    }}
                >
                    <Grid container spacing={1.75} sx={{ alignItems: 'center' }}>
                        {fields.map((field) => (
                            <Grid key={field.name} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                                {renderField(field)}
                            </Grid>
                        ))}
                        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
                            <Stack direction="row" spacing={1}>
                                <Button
                                    variant="contained"
                                    startIcon={<SearchIcon sx={{ fontSize: '17px !important' }} />}
                                    onClick={handleSearch}
                                    size="small"
                                    sx={{ px: 2, height: 38 }}
                                >
                                    查询
                                </Button>
                                <Button
                                    variant="outlined"
                                    color="inherit"
                                    startIcon={<ClearIcon sx={{ fontSize: '17px !important' }} />}
                                    onClick={handleReset}
                                    size="small"
                                    sx={{ px: 1.75, height: 38, borderColor: 'divider', color: 'text.secondary' }}
                                >
                                    重置
                                </Button>
                            </Stack>
                        </Grid>
                    </Grid>
                </Paper>
            </Collapse>
        </Box>
    );
}

export default FilterPanel;
