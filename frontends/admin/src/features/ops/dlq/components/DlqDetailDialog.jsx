// File: src/features/ops/dlq/components/DlqDetailDialog.jsx
import React from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, Typography, Box, Paper, Divider
} from '@mui/material';

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
                    <Typography variant="subtitle2" sx={{
                        color: "text.secondary"
                    }}>Routing Info</Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        Queue: {message.queueName} | Exchange: {message.exchange || '(Default)'} | RoutingKey: {message.routingKey}
                    </Typography>
                </Box>
                <Divider sx={{ my: 2 }} />

                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                    <Box>
                        <Typography variant="subtitle2" color="error" gutterBottom>Exception Stacktrace</Typography>
                        <Paper variant="outlined" sx={{ p: 2, bgcolor: '#1e1e1e', maxHeight: 400, overflow: 'auto' }}>
                            <pre style={{ margin: 0, fontSize: '0.75rem', color: '#ff8a80', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                {message.exceptionStack || 'No stacktrace available'}
                            </pre>
                        </Paper>
                    </Box>
                    <Box>
                        <Typography variant="subtitle2" color="primary" gutterBottom>Message Payload</Typography>
                        <Paper variant="outlined" sx={{ p: 2, bgcolor: '#1e1e1e', maxHeight: 400, overflow: 'auto' }}>
                            <pre style={{ margin: 0, fontSize: '0.75rem', color: '#a5d6a7', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                {prettyPayload}
                            </pre>
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
