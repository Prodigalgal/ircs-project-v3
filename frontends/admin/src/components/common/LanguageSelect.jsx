// File: ircs-frontend/src/components/common/LanguageSelect.jsx
import React, { useMemo } from 'react';
import { Autocomplete, TextField, CircularProgress, Chip } from '@mui/material';
import { useGetStandardLanguagesQuery } from '../../features/language/standardLanguagesApi.js';
import { LockToggle } from './LockableTextField';

function LanguageSelect({
                            name,
                            label,
                            value,
                            onChange,
                            locked,
                            onLockChange,
                            fullWidth,
                            required,
                            multiple = false,
                            freeSolo = true
                        }) {
    const { data: languages = [], isLoading } = useGetStandardLanguagesQuery();

    const options = useMemo(() => languages.map(l => l.name), [languages]);

    const uiValue = useMemo(() => {
        if (multiple) {
            return Array.isArray(value) ? value : [];
        }
        return value || null;
    }, [value, multiple]);

    const handleInternalChange = (event, newValue) => {
        let exportedValue;

        if (multiple) {
            exportedValue = newValue.map(item =>
                (typeof item === 'string' ? item : item?.name)
            ).filter(Boolean);
        } else {
            exportedValue = (typeof newValue === 'string' ? newValue : newValue?.name) || '';
        }

        onChange({ target: { name, value: exportedValue } });
    };

    return (
        <Autocomplete
            multiple={multiple}
            freeSolo={freeSolo}
            options={options}
            value={uiValue}
            onChange={handleInternalChange}
            loading={isLoading}
            autoSelect
            renderValue={(tagValue, getItemProps) =>
                tagValue.map((option, index) => {
                    const { key, ...tagProps } = getItemProps({ index });
                    return (
                        <Chip
                            key={key}
                            variant="outlined"
                            label={option}
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

export default LanguageSelect;