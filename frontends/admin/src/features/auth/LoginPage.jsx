// File: frontends/admin/src/features/auth/LoginPage.jsx
import React, { useCallback, useEffect, useState } from 'react';
import {
    Box,
    Button,
    Chip,
    CircularProgress,
    Container,
    Fade,
    IconButton,
    InputAdornment,
    Paper,
    Stack,
    TextField,
    Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
    ArrowForward as ArrowIcon,
    Memory as MemoryIcon,
    ShieldOutlined as ShieldIcon,
    Visibility as VisibilityIcon,
    VisibilityOff as VisibilityOffIcon,
} from '@mui/icons-material';
import { useLazyGetPoWChallengeQuery, useLoginMutation } from './authApi';
import { useNavigate } from 'react-router-dom';
import { solvePoW } from '../../utils/powSolver';
import { useDispatch } from 'react-redux';
import { showNotification } from '../common/notificationSlice.js';
import ThemeModeToggle from '../../components/common/ThemeModeToggle.jsx';
import { MONO_FONT } from '../../theme/themeConfig';

function getApiErrorMessage(err) {
    const data = err?.data;
    if (typeof data === 'string') return data;
    if (data?.message) return data.message;
    if (data?.detail) return data.detail;
    if (data?.error?.message) return data.error.message;
    if (typeof data?.error === 'string') return data.error;
    if (err?.error) return err.error;
    if (err?.message) return err.message;
    return null;
}

function LoginPage() {
    const theme = useTheme();
    const isDark = theme.palette.mode === 'dark';
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const [form, setForm] = useState({ username: '', password: '' });
    const [showPassword, setShowPassword] = useState(false);
    const [status, setStatus] = useState('IDLE'); // IDLE, POW, LOGIN
    const [challenge, setChallenge] = useState(null);
    const [challengeLoading, setChallengeLoading] = useState(false);
    const [captchaAnswer, setCaptchaAnswer] = useState('');

    const [fetchChallenge] = useLazyGetPoWChallengeQuery();
    const [login] = useLoginMutation();

    const refreshChallenge = useCallback(async () => {
        setChallengeLoading(true);
        try {
            const nextChallenge = await fetchChallenge('admin.login').unwrap();
            setChallenge(nextChallenge);
            setCaptchaAnswer('');
        } catch (err) {
            console.error('PoW challenge fetch failed:', err);
            dispatch(showNotification({ message: '安全挑战获取失败，请刷新后重试', severity: 'error' }));
        } finally {
            setChallengeLoading(false);
        }
    }, [dispatch, fetchChallenge]);

    useEffect(() => {
        localStorage.removeItem('ircs_token');
        void refreshChallenge();
    }, [refreshChallenge]);

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (challenge?.captchaRequired && !captchaAnswer.trim()) {
            dispatch(showNotification({ message: '请输入数学验证码', severity: 'error' }));
            return;
        }

        try {
            setStatus('POW');
            const activeChallenge = challenge || (await fetchChallenge('admin.login').unwrap());

            if (!activeChallenge || !activeChallenge.id) {
                throw new Error('Invalid PoW challenge received');
            }

            const powResult = await solvePoW(activeChallenge, { captchaAnswer });

            setStatus('LOGIN');
            const loginPayload = {
                username: form.username,
                password: form.password,
                powHeaders: powResult.headers,
            };

            const res = await login(loginPayload).unwrap();
            localStorage.setItem('ircs_token', res.token);
            dispatch(showNotification({ message: '登录成功，正在进入控制台', severity: 'success' }));
            navigate('/');
        } catch (err) {
            console.error('Login failed:', err);
            setStatus('IDLE');
            void refreshChallenge();

            if (err?.message && err.message.includes('Invalid PoW challenge')) {
                dispatch(showNotification({ message: '安全挑战获取失败，请重试', severity: 'error' }));
                return;
            }

            if (err?.message && err.message.includes('PoW')) {
                dispatch(showNotification({ message: '环境安全验证超时，请重试', severity: 'error' }));
                return;
            }

            if (err?.status === 'FETCH_ERROR') {
                dispatch(showNotification({ message: '网络连接失败，请确认前后端服务均已启动', severity: 'error' }));
                return;
            }

            dispatch(showNotification({ message: getApiErrorMessage(err) || '登录失败，请检查账号或密码', severity: 'error' }));
        }
    };

    const isLoading = status !== 'IDLE' || challengeLoading;
    const submitDisabled = isLoading || !challenge || (challenge.captchaRequired && !captchaAnswer.trim());

    let buttonText = '登录控制台';
    if (challengeLoading) buttonText = '正在获取安全挑战...';
    else if (status === 'POW') buttonText = '正在校验运行环境...';
    else if (status === 'LOGIN') buttonText = '正在验证身份...';

    const gridColor = isDark ? 'rgba(148, 163, 184, 0.06)' : 'rgba(15, 23, 42, 0.045)';

    return (
        <Box
            sx={{
                minHeight: '100vh',
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'relative',
                overflow: 'hidden',
                bgcolor: 'background.default',
                backgroundImage: `
                    radial-gradient(640px circle at 20% 15%, ${alpha(theme.palette.primary.main, isDark ? 0.14 : 0.09)}, transparent 60%),
                    radial-gradient(520px circle at 85% 85%, ${alpha(theme.palette.secondary.main, isDark ? 0.1 : 0.07)}, transparent 60%),
                    linear-gradient(${gridColor} 1px, transparent 1px),
                    linear-gradient(90deg, ${gridColor} 1px, transparent 1px)
                `,
                backgroundSize: '100% 100%, 100% 100%, 36px 36px, 36px 36px',
                p: 2,
            }}
        >
            {/* Top-right Dual Theme Switch */}
            <Box sx={{ position: 'fixed', top: 20, right: 20, zIndex: 2 }}>
                <ThemeModeToggle variant="segmented" />
            </Box>

            <Container maxWidth="xs" sx={{ position: 'relative', zIndex: 1 }}>
                <Fade in timeout={450}>
                    <Paper
                        variant="outlined"
                        sx={{
                            p: { xs: 3.5, sm: 4.5 },
                            borderRadius: '16px',
                            bgcolor: alpha(theme.palette.background.paper, isDark ? 0.92 : 0.96),
                            backdropFilter: 'blur(16px)',
                            boxShadow: isDark
                                ? '0 24px 64px -16px rgba(0, 0, 0, 0.7)'
                                : '0 24px 64px -20px rgba(15, 23, 42, 0.14)',
                        }}
                    >
                        {/* Brand Header */}
                        <Stack spacing={1.5} sx={{ mb: 4 }}>
                            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                                <Box
                                    sx={{
                                        width: 40,
                                        height: 40,
                                        borderRadius: '10px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
                                        color: '#FFFFFF',
                                        fontWeight: 800,
                                        fontSize: '0.95rem',
                                        letterSpacing: '-0.04em',
                                        boxShadow: `0 6px 18px ${alpha(theme.palette.primary.main, 0.35)}`,
                                    }}
                                >
                                    IR
                                </Box>
                                <Box>
                                    <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                                        IRCS 后台管理
                                    </Typography>
                                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                                        “希望”智能资源采集系统 · Project Hope
                                    </Typography>
                                </Box>
                            </Stack>
                        </Stack>

                        <form onSubmit={handleSubmit}>
                            <Stack spacing={2.25}>
                                <TextField
                                    label="管理员账号"
                                    placeholder="请输入管理员用户名"
                                    fullWidth
                                    required
                                    autoFocus
                                    autoComplete="username"
                                    value={form.username}
                                    onChange={(e) => setForm({ ...form, username: e.target.value })}
                                    disabled={isLoading}
                                />

                                <TextField
                                    label="登录密码"
                                    placeholder="请输入访问密码"
                                    type={showPassword ? 'text' : 'password'}
                                    fullWidth
                                    required
                                    autoComplete="current-password"
                                    value={form.password}
                                    onChange={(e) => setForm({ ...form, password: e.target.value })}
                                    disabled={isLoading}
                                    slotProps={{
                                        input: {
                                            endAdornment: (
                                                <InputAdornment position="end">
                                                    <IconButton
                                                        aria-label={showPassword ? '隐藏密码' : '显示密码'}
                                                        onClick={() => setShowPassword((prev) => !prev)}
                                                        edge="end"
                                                        size="small"
                                                        tabIndex={-1}
                                                    >
                                                        {showPassword ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
                                                    </IconButton>
                                                </InputAdornment>
                                            ),
                                        },
                                    }}
                                />

                                {challenge?.captchaRequired && (
                                    <TextField
                                        label="安全验证码"
                                        fullWidth
                                        required
                                        value={captchaAnswer}
                                        onChange={(e) => setCaptchaAnswer(e.target.value)}
                                        disabled={isLoading}
                                        placeholder={challenge.captchaQuestion || '请输入计算结果'}
                                        helperText={challenge.captchaQuestion || '请完成数学安全验证'}
                                        slotProps={{
                                            formHelperText: {
                                                sx: { fontFamily: MONO_FONT },
                                            },
                                        }}
                                    />
                                )}

                                {(challengeLoading || status === 'POW') && (
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 1.25,
                                            px: 1.5,
                                            py: 1,
                                            borderRadius: 2,
                                            bgcolor: 'surface.subtle',
                                            border: '1px solid',
                                            borderColor: 'divider',
                                        }}
                                    >
                                        <CircularProgress size={14} color="primary" />
                                        <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: MONO_FONT }}>
                                            {challengeLoading ? '正在获取安全挑战...' : '正在计算环境 PoW 证明...'}
                                        </Typography>
                                    </Box>
                                )}

                                <Button
                                    type="submit"
                                    variant="contained"
                                    size="large"
                                    fullWidth
                                    disabled={submitDisabled}
                                    endIcon={!isLoading ? <ArrowIcon fontSize="small" /> : (status === 'POW' ? <MemoryIcon fontSize="small" /> : null)}
                                    sx={{
                                        mt: 0.5,
                                        height: 44,
                                        fontSize: '0.9rem',
                                        borderRadius: '10px',
                                    }}
                                >
                                    {buttonText}
                                </Button>
                            </Stack>
                        </form>

                        {/* Security Meta Footer */}
                        <Box
                            sx={{
                                mt: 3.5,
                                pt: 2,
                                borderTop: '1px solid',
                                borderColor: 'divider',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 1,
                            }}
                        >
                            <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', color: 'text.secondary' }}>
                                <ShieldIcon sx={{ fontSize: 15, color: 'secondary.main' }} />
                                <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.72rem' }}>
                                    PoW + JWT 安全鉴权
                                </Typography>
                            </Stack>
                            <Chip
                                size="small"
                                variant="outlined"
                                label={challenge ? '安全通道就绪' : '初始化中'}
                                color={challenge ? 'secondary' : 'default'}
                                sx={{ height: 22, fontSize: '0.68rem' }}
                            />
                        </Box>
                    </Paper>
                </Fade>
            </Container>
        </Box>
    );
}

export default LoginPage;
