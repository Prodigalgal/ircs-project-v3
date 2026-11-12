// File: ircs-frontend/src/features/settings/components/CredentialFormDialog.jsx
import React, { useState, useEffect, useMemo } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, Box, Typography,
    CircularProgress, MenuItem, Switch, FormControlLabel,
    Divider, Stack, Alert
} from '@mui/material';
import Grid from '@mui/material/Grid';
import { useLazyGetCredentialTemplateQuery } from '../credentialsApi.js';

const MAIL_SMTP_KEYS = new Set([
    'smtp_host',
    'smtp_port',
    'smtp_protocol',
    'smtp_auth',
    'smtp_ssl_enabled',
    'smtp_starttls_enabled',
    'smtp_timeout_ms'
]);

function isRedactedMarker(value) {
    return value && typeof value === 'object' && value.redacted === true;
}

function normalizeFieldValue(field, rawValue) {
    if (isRedactedMarker(rawValue)) {
        return '';
    }
    if (rawValue !== undefined && rawValue !== null) {
        return rawValue;
    }
    return field.defaultValue !== undefined ? field.defaultValue : '';
}

function normalizePayloadForTemplate(template, sourcePayload = {}) {
    const normalized = {};
    template.forEach(field => {
        const value = normalizeFieldValue(field, sourcePayload[field.key]);
        if (value !== '') {
            normalized[field.key] = value;
        }
    });
    return normalized;
}

function DynamicField({ field, value, onChange, redacted }) {
    const commonProps = {
        fullWidth: true,
        label: field.label,
        size: 'small',
        required: field.required,
        helperText: redacted ? `${field.helpText || ''} 当前值已隐藏，保存时需重新填写。`.trim() : field.helpText,
        disabled: field.key === 'provider', // provider is usually fixed in this context
    };

    if (field.type === 'boolean') {
        return (
            <FormControlLabel
                control={
                    <Switch
                        checked={!!value}
                        onChange={(e) => onChange(field.key, e.target.checked)}
                    />
                }
                label={field.label}
            />
        );
    }

    if (field.type === 'select') {
        return (
            <TextField
                {...commonProps}
                select
                value={value || field.defaultValue || ''}
                onChange={(e) => onChange(field.key, e.target.value)}
            >
                {field.options?.map((opt) => (
                    <MenuItem key={opt.value} value={opt.value}>
                        {opt.label}
                    </MenuItem>
                ))}
            </TextField>
        );
    }

    return (
        <TextField
            {...commonProps}
            type={field.type === 'number' ? 'number' : field.type === 'password' ? 'password' : 'text'}
            value={value ?? ''}
            placeholder={field.placeholder}
            onChange={(e) => onChange(field.key, field.type === 'number'
                ? (e.target.value === '' ? '' : parseInt(e.target.value, 10))
                : e.target.value)}
        />
    );
}

const emptyPayload = {};

function CredentialFormDialog({ open, onClose, onSubmit, item, provider }) {
    const [name, setName] = useState('');
    const [payload, setPayload] = useState(emptyPayload);
    const [priority, setPriority] = useState(0);
    const [enabled, setEnabled] = useState(true);
    const [remark, setRemark] = useState('');

    // Limits
    const [rateLimit, setRateLimit] = useState(0);
    const [rateLimitUnit, setRateLimitUnit] = useState('SECOND');
    const [dayLimit, setDayLimit] = useState(0);
    const [monthLimit, setMonthLimit] = useState(0);

    const [triggerGetTemplate, { data: template = [], isFetching }] = useLazyGetCredentialTemplateQuery();

    const redactedPayloadKeys = useMemo(() => {
        const rawPayload = item?.payload || {};
        return new Set(Object.keys(rawPayload).filter(key => isRedactedMarker(rawPayload[key])));
    }, [item]);

    useEffect(() => {
        if (open && provider) {
            triggerGetTemplate(provider);

            if (item) {
                setName(item.name || '');
                setPayload(item.payload || {});
                setPriority(item.priority || 0);
                setEnabled(item.enabled ?? true);
                setRemark(item.remark || '');
                setRateLimit(item.rateLimit || 0);
                setRateLimitUnit(item.rateLimitUnit || 'SECOND');
                setDayLimit(item.dayLimit || 0);
                setMonthLimit(item.monthLimit || 0);
            } else {
                setName('');
                setPayload({});
                setPriority(0);
                setEnabled(true);
                setRemark('');
                setRateLimit(0);
                setRateLimitUnit('SECOND');
                setDayLimit(0);
                setMonthLimit(0);
            }
        }
    }, [open, provider, item, triggerGetTemplate]);

    useEffect(() => {
        if (!open || template.length === 0) {
            return;
        }
        if (item) {
            setPayload(normalizePayloadForTemplate(template, item.payload || {}));
            return;
        }
        setPayload(prev => Object.keys(prev).length === 0 ? normalizePayloadForTemplate(template, {}) : prev);
    }, [open, template, item]);

    const handlePayloadChange = (key, value) => {
        setPayload(prev => ({ ...prev, [key]: value }));
    };

    const handleSubmit = () => {
        // Basic validation
        if (!name.trim()) {
            // Optional name generation logic could go here, or just require it
            // For now let's allow empty name if the user wants
        }

        // Template validation
        for (const field of template) {
            if (field.required && (payload[field.key] === undefined || payload[field.key] === '')) {
                alert(`${field.label} 是必填项`);
                return;
            }
        }
        if (provider === 'MAIL' && payload.smtp_ssl_enabled && payload.smtp_starttls_enabled) {
            alert('SSL 加密和 STARTTLS 不能同时开启，请根据 SMTP 端口选择其一。');
            return;
        }

        const data = {
            id: item?.id,
            provider,
            name: name || `${provider}_${Date.now()}`,
            payload,
            priority,
            enabled,
            remark,
            rateLimit,
            rateLimitUnit,
            dayLimit,
            monthLimit
        };
        onSubmit(data);
    };

    const coreFields = provider === 'MAIL'
        ? template.filter(field => !MAIL_SMTP_KEYS.has(field.key))
        : template;
    const smtpFields = provider === 'MAIL'
        ? template.filter(field => MAIL_SMTP_KEYS.has(field.key))
        : [];

    const renderFields = (fields) => (
        <Grid container spacing={2}>
            {fields.map(field => (
                <Grid size={{ xs: 12, md: 6 }} key={field.key}>
                    <DynamicField
                        field={field}
                        value={payload[field.key]}
                        onChange={handlePayloadChange}
                        redacted={redactedPayloadKeys.has(field.key)}
                    />
                </Grid>
            ))}
        </Grid>
    );

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>
                {item ? '编辑凭证' : '新建凭证'} - <Typography component="span" color="primary" sx={{
                fontWeight: "bold"
            }}>{provider}</Typography>
            </DialogTitle>
            <DialogContent dividers>
                {isFetching ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
                        <CircularProgress />
                    </Box>
                ) : (
                    <Stack spacing={3}>
                        {/* Dynamic Payload Section */}
                        <Box sx={{ bgcolor: 'action.hover', p: 2, borderRadius: 1 }}>
                            <Typography variant="subtitle2" color="primary" gutterBottom>
                                {provider === 'MAIL' ? '邮箱账号' : '核心凭证 (Credentials)'}
                            </Typography>
                            {renderFields(coreFields)}
                        </Box>

                        {smtpFields.length > 0 && (
                            <Box sx={{ bgcolor: 'action.hover', p: 2, borderRadius: 1 }}>
                                <Typography variant="subtitle2" color="primary" gutterBottom>SMTP 配置</Typography>
                                {renderFields(smtpFields)}
                            </Box>
                        )}

                        {/* Meta Info Section */}
                        <Box>
                            <Typography variant="subtitle2" gutterBottom sx={{
                                color: "text.secondary"
                            }}>管理信息</Typography>
                            <Grid container spacing={2}>
                                <Grid size={{ xs: 12, md: 6 }}>
                                    <TextField
                                        label="别名/备注名"
                                        fullWidth size="small"
                                        value={name}
                                        onChange={e => setName(e.target.value)}
                                        placeholder="用于标识此 Key (可选)"
                                    />
                                </Grid>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <TextField
                                        label="优先级 (Priority)"
                                        type="number"
                                        fullWidth size="small"
                                        value={priority}
                                        onChange={e => setPriority(parseInt(e.target.value) || 0)}
                                        helperText="数字越大越优先使用"
                                    />
                                </Grid>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <FormControlLabel
                                        control={<Switch checked={enabled} onChange={e => setEnabled(e.target.checked)} />}
                                        label="启用状态"
                                    />
                                </Grid>
                                <Grid size={{ xs: 12 }}>
                                    <TextField
                                        label="备注 (Remark)"
                                        fullWidth size="small"
                                        value={remark}
                                        onChange={e => setRemark(e.target.value)}
                                    />
                                </Grid>
                            </Grid>
                        </Box>

                        <Divider />

                        {/* Limit Strategy Section */}
                        <Box>
                            <Typography variant="subtitle2" gutterBottom sx={{
                                color: "warning.main"
                            }}>限流与配额 (Limits)</Typography>
                            <Grid container spacing={2}>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <TextField
                                        label="速率限制"
                                        type="number"
                                        fullWidth size="small"
                                        value={rateLimit}
                                        onChange={e => setRateLimit(parseInt(e.target.value) || 0)}
                                        placeholder="0=不限"
                                    />
                                </Grid>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <TextField
                                        select
                                        label="单位"
                                        fullWidth size="small"
                                        value={rateLimitUnit}
                                        onChange={e => setRateLimitUnit(e.target.value)}
                                    >
                                        <MenuItem value="SECOND">每秒 (TPS)</MenuItem>
                                        <MenuItem value="MINUTE">每分 (TPM)</MenuItem>
                                    </TextField>
                                </Grid>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <TextField
                                        label="日配额 (Day Limit)"
                                        type="number"
                                        fullWidth size="small"
                                        value={dayLimit}
                                        onChange={e => setDayLimit(parseInt(e.target.value) || 0)}
                                        placeholder="0=不限"
                                    />
                                </Grid>
                                <Grid size={{ xs: 6, md: 3 }}>
                                    <TextField
                                        label="月配额 (Month Limit)"
                                        type="number"
                                        fullWidth size="small"
                                        value={monthLimit}
                                        onChange={e => setMonthLimit(parseInt(e.target.value) || 0)}
                                        placeholder="0=不限"
                                    />
                                </Grid>
                            </Grid>
                        </Box>
                    </Stack>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained" disabled={isFetching}>保存</Button>
            </DialogActions>
        </Dialog>
    );
}

export default CredentialFormDialog;
