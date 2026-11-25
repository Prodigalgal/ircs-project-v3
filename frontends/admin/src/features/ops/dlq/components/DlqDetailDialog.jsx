// File: frontends/admin/src/features/ops/dlq/components/DlqDetailDialog.jsx
import React from 'react';
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    Typography,
    Box,
    Paper,
    Divider,
} from '@mui/material';
import { MONO_FONT } from '../../../../theme/themeConfig';

function DlqDetailDialog({ open, onClose, message }) {
    if (!message) return null;

    let prettyPayload = message.payload;
    try {
        prettyPayload = JSON.stringify(JSON.parse(message.payload), null, 2);
    } catch {
        // Ignore if not JSON
    }

    return (
        <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
            <DialogTitle>死信详情</DialogTitle>
            <DialogContent dividers>
                <Box sx={{ mb: 2 }}>
                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600 }}>
                        Routing Info
                    </Typography>
                    <Typography variant="body2" sx={{ fontFamily: MONO_FONT, mt: 0.5, wordBreak: 'break-all' }}>
                        Queue: {message.queueName} | Exchange: {message.exchange || '(Default)'} | RoutingKey: {message.routingKey}
                    </Typography>
                </Box>
                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
                    <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle2" color="error" gutterBottom>
                            Exception Stacktrace
                        </Typography>
                        <Paper
                            variant="outlined"
                            sx={(theme) => ({
                                p: 2,
                                bgcolor: theme.palette.surface.codeBg,
                                maxHeight: 400,
                                overflow: 'auto',
                            })}
                        >
                            <Box
                                component="pre"
                                sx={{
                                    m: 0,
                                    fontSize: '0.75rem',
                                    fontFamily: MONO_FONT,
                                    color: 'error.main',
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                }}
                            >
                                {message.exceptionStack || 'No stacktrace available'}
                            </Box>
                        </Paper>
                    </Box>
                    <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle2" color="primary" gutterBottom>
                            Message Payload
                        </Typography>
                        <Paper
                            variant="outlined"
                            sx={(theme) => ({
                                p: 2,
                                bgcolor: theme.palette.surface.codeBg,
                                maxHeight: 400,
                                overflow: 'auto',
                            })}
                        >
                            <Box
                                component="pre"
                                sx={(theme) => ({
                                    m: 0,
                                    fontSize: '0.75rem',
                                    fontFamily: MONO_FONT,
                                    color: theme.palette.surface.codeText,
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-all',
                                })}
                            >
                                {prettyPayload}
                            </Box>
                        </Paper>
                    </Box>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>关闭</Button>
            </DialogActions>
        </Dialog>
    );
}

export default DlqDetailDialog;
