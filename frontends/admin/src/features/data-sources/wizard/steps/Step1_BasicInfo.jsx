// File: ircs-frontend/src/features/data-sources/wizard/steps/Step1_BasicInfo.jsx
import React from 'react';
import { Divider, FormControlLabel, MenuItem, Switch, TextField, Typography } from '@mui/material';
import Grid from '@mui/material/Grid';

import { updateFormData } from '../dataSourceWizardSlice';
import {useDispatch, useSelector} from "react-redux";

function Step1_BasicInfo() {
    const dispatch = useDispatch();
    const formData = useSelector((state) => state.dataSourceWizard.formData);

    const handleChange = (event) => {
        const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
        dispatch(updateFormData({ [event.target.name]: value }));
    };

    const selectOptions = {
        transportMode: ['AUTO', 'JDK', 'APACHE_HC5', 'CURL_NATIVE'],
        httpProtocol: ['AUTO', 'HTTP_1_1', 'HTTP_2', 'HTTP_1_0'],
        ipVersionPolicy: ['AUTO', 'IPV4_ONLY', 'IPV6_ONLY', 'IPV4_PREFERRED', 'IPV6_PREFERRED'],
        dnsResolverType: ['SYSTEM', 'DOH', 'DOT'],
    };

    return (
        <React.Fragment>
            <Typography variant="h6" gutterBottom>
                第一步：基本信息
            </Typography>
            <Typography variant="body2" gutterBottom sx={{
                color: "text.secondary"
            }}>
                请为该数据源提供一个唯一的名称和 API 根地址。
            </Typography>
            <Grid container spacing={3} sx={{ mt: 2 }}>
                <Grid size={{ xs: 12 }}>
                    <TextField
                        required
                        id="name"
                        name="name"
                        label="数据源名称"
                        fullWidth
                        variant="outlined"
                        value={formData.name}
                        onChange={handleChange}
                        placeholder="例如：飞飞资源站"
                    />
                </Grid>
                <Grid size={{ xs: 12 }}>
                    <TextField
                        required
                        id="baseUrl"
                        name="baseUrl"
                        label="API 根地址 (Base URL)"
                        fullWidth
                        variant="outlined"
                        value={formData.baseUrl}
                        onChange={handleChange}
                        placeholder="http://api.example.com"
                    />
                </Grid>
                <Grid size={{ xs: 12 }}>
                    <FormControlLabel
                        control={(
                            <Switch
                                name="adultRestricted"
                                checked={Boolean(formData.adultRestricted)}
                                onChange={handleChange}
                            />
                        )}
                        label="成人限制资源站"
                    />
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            display: "block"
                        }}>
                        开启后，该资源站采集入库的聚合视频在上架前会作为成人高置信来源参与判断。
                    </Typography>
                </Grid>

                <Grid size={{ xs: 12 }}>
                    <Divider sx={{ my: 1 }} />
                    <Typography variant="subtitle1" gutterBottom>
                        源站网络策略
                    </Typography>
                    <Typography variant="body2" sx={{
                        color: "text.secondary"
                    }}>
                        针对双栈、DNS 污染、旧 HTTP 协议或特殊源站，保存后采集和示例请求都会使用这里的策略。
                    </Typography>
                </Grid>

                <Grid size={{ xs: 12, md: 3 }}>
                    <TextField select name="transportMode" label="传输实现" fullWidth value={formData.transportMode}
                               onChange={handleChange} helperText="AUTO 会按协议/DNS/IP 策略自动选择">
                        {selectOptions.transportMode.map(option => (
                            <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                    </TextField>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <TextField select name="httpProtocol" label="HTTP 协议" fullWidth value={formData.httpProtocol}
                               onChange={handleChange} helperText="老源站可尝试 HTTP_1_0">
                        {selectOptions.httpProtocol.map(option => (
                            <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                    </TextField>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <TextField select name="ipVersionPolicy" label="IP 策略" fullWidth value={formData.ipVersionPolicy}
                               onChange={handleChange} helperText="双栈异常时可固定 IPv4/IPv6">
                        {selectOptions.ipVersionPolicy.map(option => (
                            <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                    </TextField>
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                    <TextField select name="dnsResolverType" label="DNS 解析" fullWidth value={formData.dnsResolverType}
                               onChange={handleChange} helperText="支持 SYSTEM/DOH/DOT">
                        {selectOptions.dnsResolverType.map(option => (
                            <MenuItem key={option} value={option}>{option}</MenuItem>
                        ))}
                    </TextField>
                </Grid>

                <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                        id="dnsResolverEndpoint"
                        name="dnsResolverEndpoint"
                        label="DNS Endpoint"
                        fullWidth
                        variant="outlined"
                        value={formData.dnsResolverEndpoint}
                        onChange={handleChange}
                        placeholder="https://cloudflare-dns.com/dns-query 或 cloudflare-dns.com:853"
                    />
                </Grid>
                <Grid size={{ xs: 12, md: 2 }}>
                    <TextField
                        id="connectTimeoutMs"
                        name="connectTimeoutMs"
                        label="连接超时(ms)"
                        type="number"
                        fullWidth
                        value={formData.connectTimeoutMs}
                        onChange={handleChange}
                    />
                </Grid>
                <Grid size={{ xs: 12, md: 2 }}>
                    <TextField
                        id="readTimeoutMs"
                        name="readTimeoutMs"
                        label="读取超时(ms)"
                        type="number"
                        fullWidth
                        value={formData.readTimeoutMs}
                        onChange={handleChange}
                    />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                    <TextField
                        id="userAgent"
                        name="userAgent"
                        label="User-Agent"
                        fullWidth
                        value={formData.userAgent}
                        onChange={handleChange}
                        placeholder="留空则使用系统默认"
                    />
                </Grid>
            </Grid>
        </React.Fragment>
    );
}

export default Step1_BasicInfo;
