import React from 'react';
import {
    Box, Divider, FormControlLabel, InputAdornment, Switch, TextField
} from '@mui/material';
import Grid from '@mui/material/Grid';
import SearchIcon from '@mui/icons-material/Search';
import KeyValueEditor from '../../../components/common/KeyValueEditor';

function ScraperConfigForm({ formData, setFormData }) {
    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    return (
        <Box sx={{ p: 4, maxWidth: 800, mx: 'auto', overflowY: 'auto', height: '100%' }}>
            <Grid container spacing={3}>
                <Grid size={{ xs: 12 }}>
                    <TextField
                        label="视频关键词"
                        fullWidth
                        value={formData.keyword}
                        onChange={(e) => handleChange('keyword', e.target.value)}
                        placeholder="例如: 复仇者联盟"
                        autoFocus
                        variant="outlined"
                        slotProps={{
                            input: {
                                startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment>
                            }
                        }}
                    />
                </Grid>

                <Grid size={{ xs: 6 }}>
                    <TextField
                        label="起始页码"
                        type="number"
                        fullWidth
                        value={formData.startPage}
                        onChange={(e) => handleChange('startPage', parseInt(e.target.value) || 1)}
                    />
                </Grid>
                <Grid size={{ xs: 6 }}>
                    <TextField
                        label="结束页码"
                        type="number"
                        fullWidth
                        value={formData.endPage}
                        onChange={(e) => handleChange('endPage', parseInt(e.target.value) || 1)}
                    />
                </Grid>

                <Grid size={{ xs: 12 }}><Divider textAlign="left">网络配置 (仅本次有效)</Divider></Grid>

                <Grid size={{ xs: 12 }}>
                    <FormControlLabel
                        control={<Switch checked={formData.enableRandomUa} onChange={(e) => handleChange('enableRandomUa', e.target.checked)} />}
                        label="随机 User-Agent"
                    />
                </Grid>

                <Grid size={{ xs: 12 }}>
                    <FormControlLabel
                        control={<Switch checked={formData.useCustomProxy} onChange={(e) => handleChange('useCustomProxy', e.target.checked)} />}
                        label="启用代理 (覆盖系统默认)"
                    />
                </Grid>

                {formData.useCustomProxy && (
                    <Grid container size={{ xs: 12 }} spacing={2}>
                        <Grid size={{ xs: 8 }}>
                            <TextField label="Proxy Host" fullWidth size="small" value={formData.proxyHost} onChange={(e) => handleChange('proxyHost', e.target.value)} />
                        </Grid>
                        <Grid size={{ xs: 4 }}>
                            <TextField label="Port" fullWidth size="small" type="number" value={formData.proxyPort} onChange={(e) => handleChange('proxyPort', parseInt(e.target.value) || '')} />
                        </Grid>
                    </Grid>
                )}

                <Grid size={{ xs: 12 }}>
                    <KeyValueEditor
                        label="自定义 Headers"
                        value={formData.headers}
                        onChange={(val) => handleChange('headers', val)}
                    />
                </Grid>
            </Grid>
        </Box>
    );
}

export default ScraperConfigForm;