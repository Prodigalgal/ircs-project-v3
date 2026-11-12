// File: /ircs-ui/src/features/data-sources/wizard/components/JsonPathSelector.jsx
import React from 'react';
import { FormControl, InputLabel, Select, MenuItem, FormHelperText } from '@mui/material';

function JsonPathSelector({ label, value, onChange, jsonPaths = [], required = false, helperText = '' }) {

    const safePaths = React.useMemo(
        () => Array.from(new Set((Array.isArray(jsonPaths) ? jsonPaths : []).filter(Boolean))),
        [jsonPaths]
    );

    const isValueInOptions = value && safePaths.includes(value);
    const hasCurrentValue = value && !isValueInOptions;
    const effectiveHelperText = helperText || (!safePaths.length && !value
        ? '请先获取示例数据，系统会从响应中解析可选 JSONPath。'
        : '');

    return (
        <FormControl fullWidth required={required} margin="normal">
            <InputLabel>{label}</InputLabel>
            <Select
                value={value || ''}
                label={label}
                onChange={onChange}
                MenuProps={{ PaperProps: { sx: { maxHeight: 360 } } }}
            >
                <MenuItem value=""><em>未选择</em></MenuItem>

                {hasCurrentValue && (
                    <MenuItem value={value}>
                        {value} <span style={{ opacity: 0.6, marginLeft: 8, fontSize: '0.8em' }}>(当前配置)</span>
                    </MenuItem>
                )}

                {safePaths.map((path) => (
                    <MenuItem key={path} value={path}>
                        {path}
                    </MenuItem>
                ))}
            </Select>
            {effectiveHelperText && <FormHelperText>{effectiveHelperText}</FormHelperText>}
        </FormControl>
    );
}

export default JsonPathSelector;
