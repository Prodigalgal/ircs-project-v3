// File: ircs-frontend/src/components/common/UnifiedVideoSelect.jsx
import React, { useState, useMemo, useEffect } from 'react';
import { Autocomplete, TextField, CircularProgress, Box, Typography } from '@mui/material';
import { debounce } from '@mui/material/utils';
import { useLazyGetUnifiedVideosQuery } from '../../features/unified-videos/unifiedVideosApi';

function UnifiedVideoSelect({ value, onChange, label = "关联聚合视频" }) {
    const [open, setOpen] = useState(false);
    const [options, setOptions] = useState([]);
    const [inputValue, setInputValue] = useState('');

    const [triggerSearch, { data, isFetching }] = useLazyGetUnifiedVideosQuery();

    const fetchDebounced = useMemo(() => debounce((input) => {
        triggerSearch({ title: input, page: 0, size: 20 });
    }, 400), [triggerSearch]);

    useEffect(() => {
        if (open) {
            fetchDebounced(inputValue);
        }
    }, [inputValue, open, fetchDebounced]);

    useEffect(() => {
        if (data?.content) {
            setOptions(data.content);
        }
    }, [data]);

    return (
        <Autocomplete
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            isOptionEqualToValue={(option, value) => option.id === value.id}
            getOptionLabel={(option) => `${option.title} (${option.year || '-'})`}
            options={options}
            loading={isFetching}
            value={value}
            onChange={(event, newValue) => {
                onChange(newValue);
            }}
            onInputChange={(event, newInputValue) => {
                setInputValue(newInputValue);
            }}
            renderOption={(props, option) => {
                // 从 props 中分离 key，避免 React 警告
                const { key, ...otherProps } = props;
                return (
                    <li key={key} {...otherProps}>
                        <Box>
                            <Typography variant="body2">{option.title}</Typography>
                            <Typography variant="caption" sx={{
                                color: "text.secondary"
                            }}>
                                {option.year} | {option.area} | {option.id.substring(0, 8)}...
                            </Typography>
                        </Box>
                    </li>
                );
            }}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label={label}
                    fullWidth
                    variant="outlined"
                    slotProps={{
                        ...params.slotProps,

                        input: {
                            ...params.slotProps.input,
                            endAdornment: (
                                <React.Fragment>
                                    {isFetching ? <CircularProgress color="inherit" size={20} /> : null}
                                    {params.slotProps.input.endAdornment}
                                </React.Fragment>
                            ),
                        }
                    }}
                />
            )}
        />
    );
}

export default UnifiedVideoSelect;
