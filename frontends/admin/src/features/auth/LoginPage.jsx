// File: ircs-frontend/src/features/auth/LoginPage.jsx
import React, {useCallback, useEffect, useState} from 'react';
import {
    Box,
    Button,
    CircularProgress,
    Container,
    Fade,
    GlobalStyles,
    Paper,
    TextField,
    Typography
} from '@mui/material';
import {ArrowForward as ArrowIcon, Memory as MemoryIcon, Terminal as TerminalIcon} from '@mui/icons-material';
import {useLazyGetPoWChallengeQuery, useLoginMutation} from './authApi';
import {useNavigate} from 'react-router-dom';
import {styled} from '@mui/material/styles';
import {solvePoW} from '../../utils/powSolver';
import {useDispatch} from 'react-redux';
import {showNotification} from '../common/notificationSlice.js';


const LoginWrapper = styled(Box)({
    width: '100%',
    height: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#000',
    backgroundImage: `
        linear-gradient(#161616 1px, transparent 1px),
        linear-gradient(90deg, #161616 1px, transparent 1px)
    `,
    backgroundSize: '40px 40px',
    position: 'relative',
    overflow: 'hidden',
});

const StyledCard = styled(Paper)(({theme}) => ({
    padding: theme.spacing(6),
    borderRadius: '4px',
    backgroundColor: '#050505',
    border: '1px solid #222',
    width: '100%',
    maxWidth: '400px',
    transition: 'border-color 0.4s ease',
    '&:hover': {
        borderColor: '#444',
    }
}));

const DarkInput = styled(TextField)({
    marginBottom: '24px',
    '& .MuiOutlinedInput-root': {
        borderRadius: '2px',
        backgroundColor: '#000',
        '& fieldset': {borderColor: '#222'},
        '&:hover fieldset': {borderColor: '#444'},
        '&.Mui-focused fieldset': {
            borderColor: '#FFF',
            borderWidth: '1px'
        },
    },
    '& .MuiInputLabel-root': {
        color: '#666',
        fontSize: '0.85rem',
        '&.Mui-focused': {color: '#FFF'}
    },
    '& .MuiInputBase-input': {
        color: '#FFF',
        fontFamily: 'monospace',
        letterSpacing: '1px'
    }
});

const ActionButton = styled(Button)({
    height: '50px',
    borderRadius: '2px',
    backgroundColor: '#FFF',
    color: '#000',
    fontWeight: 'bold',
    fontSize: '0.9rem',
    letterSpacing: '2px',
    textTransform: 'uppercase',
    '&:hover': {
        backgroundColor: '#CCC',
        boxShadow: '0 0 20px rgba(255,255,255,0.2)'
    },
    '&:disabled': {
        backgroundColor: '#222',
        color: '#666'
    }
});

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
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [form, setForm] = useState({username: '', password: ''});
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
            dispatch(showNotification({message: '安全挑战获取失败，请刷新后重试', severity: 'error'}));
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
            dispatch(showNotification({message: '请输入数学验证码', severity: 'error'}));
            return;
        }

        try {
            setStatus('POW');
            const activeChallenge = challenge || (await fetchChallenge('admin.login').unwrap());

            if (!activeChallenge || !activeChallenge.id) {
                throw new Error('Invalid PoW challenge received');
            }

            console.time("PoW");
            const powResult = await solvePoW(activeChallenge, {captchaAnswer});
            console.timeEnd("PoW");

            setStatus('LOGIN');
            const loginPayload = {
                username: form.username,
                password: form.password,
                powHeaders: powResult.headers,
            };

            const res = await login(loginPayload).unwrap();
            localStorage.setItem('ircs_token', res.token);
            dispatch(showNotification({message: '登录成功，正在进入系统', severity: 'success'}));
            navigate('/');

        } catch (err) {
            console.error("Login failed:", err);
            setStatus('IDLE');
            void refreshChallenge();

            if (err?.message && err.message.includes('Invalid PoW challenge')) {
                dispatch(showNotification({message: '安全挑战获取失败，请重试', severity: 'error'}));
                return;
            }

            if (err?.message && err.message.includes('PoW')) {
                dispatch(showNotification({message: '环境安全验证超时，请重试', severity: 'error'}));
                return;
            }

            if (err?.status === 'FETCH_ERROR') {
                dispatch(showNotification({message: '网络连接失败，请确认前后端服务均已启动', severity: 'error'}));
                return;
            }

            dispatch(showNotification({message: getApiErrorMessage(err) || '登录失败，请检查账号或密码', severity: 'error'}));
        }
    };

    const isLoading = status !== 'IDLE' || challengeLoading;
    const submitDisabled = isLoading || !challenge || (challenge.captchaRequired && !captchaAnswer.trim());
    const buttonText = challengeLoading ? 'FETCHING CHALLENGE...' : (status === 'POW' ? 'VERIFYING ENVIRONMENT...' : (status === 'LOGIN' ? 'AUTHENTICATING...' : 'RUN AUTH SEQUENCE'));

    return (
        <Box sx={{width: '100%', height: '100%'}}>
            <GlobalStyles styles={{
                'html, body': {
                    margin: 0,
                    padding: 0,
                    height: '100%',
                    width: '100%',
                    backgroundColor: '#000'
                },
                '#root': {
                    height: '100%',
                    width: '100%'
                }
            }}/>

            <LoginWrapper>
                <Container maxWidth="xs">
                    <Fade in={true} timeout={1000}>
                        <Box>
                            <StyledCard elevation={0}>
                                {/* Header Section */}
                                <Box sx={{mb: 6, textAlign: 'left'}}>
                                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1, mb: 1}}>
                                        <TerminalIcon sx={{color: '#FFF', fontSize: 20}}/>
                                        <Typography
                                            variant="h6"
                                            sx={{
                                                fontWeight: "bold",
                                                color: '#FFF',
                                                letterSpacing: '2px'
                                            }}>
                                            TERMINAL.LOGIN
                                        </Typography>
                                    </Box>
                                    <Typography variant="caption" sx={{color: '#444', fontFamily: 'monospace'}}>
                                        Establish secure connection to IRCS node...
                                    </Typography>
                                </Box>

                                <form onSubmit={handleSubmit}>
                                    <DarkInput
                                        label="USER_IDENT"
                                        fullWidth
                                        required
                                        autoComplete="username"
                                        value={form.username}
                                        onChange={e => setForm({...form, username: e.target.value})}
                                        disabled={isLoading}
                                    />
                                    <DarkInput
                                        label="ACCESS_KEY"
                                        type="password"
                                        fullWidth
                                        required
                                        autoComplete="current-password"
                                        value={form.password}
                                        onChange={e => setForm({...form, password: e.target.value})}
                                        disabled={isLoading}
                                    />

                                    {challenge?.captchaRequired && (
                                        <DarkInput
                                            label="MATH_CHECK"
                                            fullWidth
                                            required
                                            value={captchaAnswer}
                                            onChange={e => setCaptchaAnswer(e.target.value)}
                                            disabled={isLoading}
                                            placeholder={challenge.captchaQuestion || 'Solve challenge'}
                                            helperText={challenge.captchaQuestion || 'Solve the math challenge'}
                                            FormHelperTextProps={{sx: {color: '#666', fontFamily: 'monospace'}}}
                                        />
                                    )}

                                    {(challengeLoading || status === 'POW') && (
                                        <Box sx={{display: 'flex', alignItems: 'center', gap: 1, mb: 2, color: '#666'}}>
                                            <CircularProgress size={14} color="inherit"/>
                                            <Typography variant="caption" sx={{fontFamily: 'monospace'}}>
                                                {challengeLoading ? 'FETCHING SECURITY CHALLENGE...' : 'SOLVING CRYPTO PUZZLE...'}
                                            </Typography>
                                        </Box>
                                    )}

                                    <ActionButton
                                        type="submit"
                                        fullWidth
                                        disabled={submitDisabled}
                                        endIcon={!isLoading ? <ArrowIcon/> : (status === 'POW' ? <MemoryIcon/> : null)}
                                    >
                                        {buttonText}
                                    </ActionButton>
                                </form>

                                <Box sx={{
                                    mt: 6,
                                    pt: 2,
                                    borderTop: '1px solid #111',
                                    display: 'flex',
                                    justifyContent: 'space-between'
                                }}>
                                    <Typography variant="caption" sx={{color: '#333', fontSize: '0.65rem'}}>
                                        ENCRYPTION: AES-256 + PoW
                                    </Typography>
                                    <Typography variant="caption" sx={{color: '#333', fontSize: '0.65rem'}}>
                                        STATUS: RESTRICTED
                                    </Typography>
                                </Box>
                            </StyledCard>
                        </Box>
                    </Fade>
                </Container>
            </LoginWrapper>
        </Box>
    );
}

export default LoginPage;
