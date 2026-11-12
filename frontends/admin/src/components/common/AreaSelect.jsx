// File: ircs-frontend/src/components/common/AreaSelect.jsx
import React, { useState } from 'react';
import { Autocomplete, TextField, CircularProgress, Chip } from '@mui/material';
import { LockToggle } from './LockableTextField';
import { useGetStandardAreasQuery } from '../../features/areas/areasApi';

function AreaSelect({
                        name,
                        label,
                        value = [],
                        onChange,
                        locked,
                        onLockChange,
                        fullWidth,
                        required
                    }) {

    const [inputValue, setInputValue] = useState('');
    const { data: standardAreas = [], isLoading } = useGetStandardAreasQuery();
    const options = standardAreas;

    const getOptionLabel = (option) => {
        if (typeof option === 'string') return option;
        return option.name || option.code || '';
    };

    const isOptionEqualToValue = (opt, val) => {
        return opt.id === val.id;
    };

    const handleInternalChange = (event, newValue) => {
        onChange({ target: { name, value: newValue } });
    };

    return (
        <Autocomplete
            multiple
            options={options}
            value={value || []}
            onChange={handleInternalChange}
            inputValue={inputValue}
            onInputChange={(e, v) => setInputValue(v)}
            loading={isLoading}
            autoSelect
            getOptionLabel={getOptionLabel}
            isOptionEqualToValue={isOptionEqualToValue}
            filterSelectedOptions
            renderValue={(tagValue, getItemProps) =>
                tagValue.map((option, index) => {
                    const { key, ...tagProps } = getItemProps({ index });
                    return (
                        <Chip
                            key={key}
                            variant="outlined"
                            label={getOptionLabel(option)}
                            {...tagProps}
                        />
                    );
                })
            }
            renderInput={(params) => {
                const { InputProps, ...restParams } = params;
                return (
                    <TextField
                        {...restParams}
                        label={label}
                        name={name}
                        required={required}
                        fullWidth={fullWidth}
                        variant="outlined"
                        slotProps={{
                            input: {
                                ...InputProps,
                                endAdornment: (
                                    <React.Fragment>
                                        {isLoading ? <CircularProgress color="inherit" size={20} /> : null}
                                        {InputProps.endAdornment}
                                        {onLockChange && (
                                            <LockToggle
                                                locked={locked}
                                                onLockChange={onLockChange}
                                                name={name}
                                                edge="end"
                                            />
                                        )}
                                    </React.Fragment>
                                )
                            }
                        }}
                    />
                );
            }}
        />
    );
}

export default AreaSelect;
