// File: ircs-frontend/src/features/settings/components/ConfigItem.jsx
import React, {useState, useMemo} from 'react';
import {
    Box,
    TextField,
    Switch,
    Typography,
    IconButton,
    InputAdornment,
    Divider,
    Autocomplete,
    CircularProgress,
    Chip,
    Stack
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import {useGetTimezonesQuery} from '../../common/commonApi';
import {toTimeInputValue} from '../../../utils/dateInput';

function ConfigItem({setting, value, onChange}) {
    const [showPassword, setShowPassword] = useState(false);

    // Lazy load timezones only if needed.
    const shouldFetchTimezones = setting.type === 'timezone';
    const {data: timezoneList = [], isLoading: isTzLoading} = useGetTimezonesQuery(undefined, {
        skip: !shouldFetchTimezones
    });

    const timezones = useMemo(() => {
        if (!timezoneList.length) return [];
        const commons = ['Asia/Shanghai', 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo'];
        const list = [...timezoneList];
        commons.forEach(c => {
            const idx = list.indexOf(c);
            if (idx > -1) {
                list.splice(idx, 1);
                list.unshift(c);
            }
        });
        return list;
    }, [timezoneList]);

    if (setting.isDivider) {
        return <Divider sx={{my: 3, borderStyle: 'dashed'}}/>;
    }

    const isRestartRequired = setting.activationMode === 'RESTART_REQUIRED';
    const activationLabel = isRestartRequired ? '需重启' : '热生效';
    const activationHint = isRestartRequired && setting.restartServices?.length
        ? `需重启: ${setting.restartServices.join(', ')}`
        : activationLabel;
    const helperText = [setting.helperText || setting.key, activationHint].filter(Boolean).join(' · ');

    const commonProps = {
        fullWidth: true,
        label: setting.label,
        helperText,
        size: "small",
        variant: "outlined",
        name: setting.key,
        id: setting.key,
    };

    // --- Boolean Switch ---
    if (setting.type === 'boolean') {
        return (
            <Box sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.5}}>
                <Box sx={{pr: 2}}>
                    <Stack
                        direction="row"
                        spacing={1}
                        sx={{
                            alignItems: "center",
                            mb: 0.25
                        }}>
                        <Typography variant="body2" sx={{
                            fontWeight: "500"
                        }}>{setting.label}</Typography>
                        <Chip
                            size="small"
                            color={isRestartRequired ? 'warning' : 'success'}
                            variant="outlined"
                            label={activationLabel}
                            sx={{height: 20, fontSize: '0.68rem'}}
                        />
                    </Stack>
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            fontFamily: 'monospace'
                        }}>
                        {setting.key}
                    </Typography>
                </Box>
                <Switch
                    checked={value === 'true'}
                    onChange={(e) => onChange(setting.key, String(e.target.checked))}
                    color="primary"
                />
            </Box>
        );
    }

    // --- Time Picker (HH:mm) ---
    if (setting.key.includes('time-window.start') || setting.key.includes('time-window.end')) {
        return (
            <TextField
                {...commonProps}
                type="time"
                value={toTimeInputValue(value)}
                onChange={(e) => onChange(setting.key, e.target.value)}
                helperText={[setting.helperText || "格式: HH:mm (24小时制)", activationHint].join(' · ')}
                slotProps={{
                    inputLabel: {
                        shrink: true
                    },
                    htmlInput: {
                        step: 60
                    }
                }}
                sx={{mb: 3}}
            />
        );
    }

    // --- Timezone Select ---
    if (shouldFetchTimezones) {
        return (
            <Box sx={{mb: 3}}>
                <Autocomplete
                    options={timezones}
                    value={value || 'UTC'}
                    onChange={(e, newValue) => onChange(setting.key, newValue)}
                    loading={isTzLoading}
                    disableClearable
                    renderInput={(params) => (
                        <TextField
                            {...params}
                            {...commonProps}
                            slotProps={{
                                ...params.slotProps,

                                input: {
                                    ...params.slotProps.input,
                                    endAdornment: (
                                        <React.Fragment>
                                            {isTzLoading ? <CircularProgress color="inherit" size={20}/> : null}
                                            {params.slotProps.input.endAdornment}
                                        </React.Fragment>
                                    )
                                }
                            }}
                        />
                    )}
                />
            </Box>
        );
    }

    // --- Password Field ---
    if (setting.type === 'password') {
        return (
            <TextField
                {...commonProps}
                type={showPassword ? 'text' : 'password'}
                value={value || ''}
                onChange={(e) => onChange(setting.key, e.target.value)}
                autoComplete="new-password"
                slotProps={{
                    input: {
                        endAdornment: (
                            <InputAdornment position="end">
                                <IconButton
                                    onClick={() => setShowPassword(!showPassword)}
                                    edge="end"
                                    tabIndex={-1}
                                >
                                    {showPassword ? <VisibilityOff/> : <Visibility/>}
                                </IconButton>
                            </InputAdornment>
                        ),
                    }
                }}
                sx={{mb: 3}}
            />
        );
    }

    // --- Standard Select ---
    if (setting.type === 'select') {
        return (
            <TextField
                {...commonProps}
                select
                value={value || ''}
                onChange={(e) => onChange(setting.key, e.target.value)}
                slotProps={{
                    select: {
                        native: true
                    },
                    inputLabel: {
                        shrink: true
                    }
                }}
                sx={{mb: 3}}
            >
                <option value="">请选择...</option>
                {setting.options.map(opt => (
                    <option key={opt} value={opt}>{opt}</option>
                ))}
            </TextField>
        );
    }

    // --- JSON / Multiline ---
    if (setting.type === 'json' || setting.multiline) {
        return (
            <TextField
                {...commonProps}
                multiline
                rows={setting.rows || 6}
                value={value || ''}
                onChange={(e) => onChange(setting.key, e.target.value)}
                autoComplete="off"
                sx={{
                    mb: 3,
                    '& .MuiInputBase-input': {
                        fontFamily: 'Consolas, Monaco, "Andale Mono", monospace',
                        fontSize: '0.85rem',
                        lineHeight: 1.5
                    }
                }}
            />
        );
    }

    // --- Default Text/Number ---
    return (
        <TextField
            {...commonProps}
            type={setting.type === 'number' ? 'number' : 'text'}
            value={value || ''}
            onChange={(e) => onChange(setting.key, e.target.value)}
            autoComplete="off"
            sx={{mb: 3}}
        />
    );
}

export default ConfigItem;
