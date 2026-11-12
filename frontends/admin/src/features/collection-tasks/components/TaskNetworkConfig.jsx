// File: ircs-frontend/src/features/collection-tasks/components/TaskNetworkConfig.jsx
import React from 'react';
import { TextField, FormControl, InputLabel, Select, MenuItem, FormControlLabel, Switch, RadioGroup, Radio, Box, Typography, Grid, Tooltip, IconButton } from '@mui/material';
import RestoreIcon from '@mui/icons-material/Restore';
import KeyValueEditor from '../../../components/common/KeyValueEditor';

function TaskNetworkConfig({ formData, errors, handleChange, handleNumberChange, handleHeadersChange, handleResetUserAgent, handleProxyTypeChange }) {
    return (
        <Grid container spacing={3}>
            <Grid size={{ xs: 12 }}>
                <FormControlLabel
                    control={<Switch name="enableRandomUa" checked={formData.enableRandomUa} onChange={handleChange} />}
                    label="随机化 User-Agent (模拟不同浏览器/设备)"
                />
            </Grid>

            <Grid size={{ xs: 12 }} sx={{ display: formData.enableRandomUa ? 'none' : 'block' }}>
                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                    <TextField
                        name="userAgent"
                        label="固定 User-Agent"
                        value={formData.userAgent || ''}
                        onChange={handleChange}
                        fullWidth
                        multiline
                        rows={2}
                        variant="outlined"
                        placeholder="Mozilla/5.0..."
                        disabled={formData.enableRandomUa}
                    />
                    <Tooltip title="重置为默认 Chrome UA">
                        <IconButton onClick={handleResetUserAgent} sx={{ mt: 1 }}>
                            <RestoreIcon />
                        </IconButton>
                    </Tooltip>
                </Box>
            </Grid>

            <Grid size={{ xs: 12 }}>
                <Box sx={{ border: '1px solid rgba(255, 255, 255, 0.23)', borderRadius: 1, p: 2 }}>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>代理设置</Typography>
                    <FormControl component="fieldset" fullWidth sx={{ mt: 1 }}>
                        <RadioGroup row name="useCustomProxy" value={formData.useCustomProxy ? 'custom' : 'system'} onChange={handleProxyTypeChange}>
                            <FormControlLabel value="system" control={<Radio />} label="直连 (Direct)" />
                            <FormControlLabel value="custom" control={<Radio />} label="自定义代理 (Custom Proxy)" />
                        </RadioGroup>
                    </FormControl>

                    {formData.useCustomProxy && (
                        <Grid container spacing={2} sx={{ mt: 1 }}>
                            <Grid size={{ xs: 3 }}>
                                <FormControl fullWidth size="small">
                                    <InputLabel>类型</InputLabel>
                                    <Select name="proxyType" value={formData.proxyType} label="类型" onChange={handleChange}>
                                        <MenuItem value="HTTP">HTTP</MenuItem>
                                        <MenuItem value="SOCKS">SOCKS</MenuItem>
                                    </Select>
                                </FormControl>
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField
                                    name="proxyHost"
                                    label="主机 (Host)"
                                    value={formData.proxyHost || ''}
                                    onChange={handleChange}
                                    fullWidth
                                    size="small"
                                    required={formData.useCustomProxy}
                                    error={!!errors.proxyHost}
                                    helperText={errors.proxyHost}
                                />
                            </Grid>
                            <Grid size={{ xs: 3 }}>
                                <TextField
                                    name="proxyPort"
                                    label="端口"
                                    type="number"
                                    value={formData.proxyPort || ''}
                                    onChange={handleNumberChange}
                                    fullWidth
                                    size="small"
                                    required={formData.useCustomProxy}
                                    error={!!errors.proxyPort}
                                    helperText={errors.proxyPort}
                                />
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField
                                    name="proxyUsername"
                                    label="用户名 (可选)"
                                    value={formData.proxyUsername || ''}
                                    onChange={handleChange}
                                    fullWidth
                                    size="small"
                                />
                            </Grid>
                            <Grid size={{ xs: 6 }}>
                                <TextField
                                    name="proxyPassword"
                                    label="密码 (可选)"
                                    type="password"
                                    value={formData.proxyPassword || ''}
                                    onChange={handleChange}
                                    fullWidth
                                    size="small"
                                />
                            </Grid>
                        </Grid>
                    )}
                </Box>
            </Grid>

            <Grid size={{ xs: 12 }}>
                <KeyValueEditor
                    label="自定义 HTTP Headers (覆盖默认)"
                    value={formData.headers || '{}'}
                    onChange={handleHeadersChange}
                />
            </Grid>
        </Grid>
    );
}
export default TaskNetworkConfig;
