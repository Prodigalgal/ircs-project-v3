// File: ircs-frontend/src/features/messages/components/ReplyDialog.jsx
import React, { useState } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, Typography, Box, Divider, Paper
} from '@mui/material';

function ReplyDialog({ open, onClose, message, onSubmit }) {
    const [replyContent, setReplyContent] = useState('');

    React.useEffect(() => {
        if (open) {
            setReplyContent(message?.reply || '');
        }
    }, [open, message]);

    const handleSubmit = () => {
        if (!replyContent.trim()) return alert("回复内容不能为空");
        onSubmit(message.id, replyContent);
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle>回复用户留言</DialogTitle>
            <DialogContent dividers>
                <Box sx={{ mb: 2 }}>
                    <Typography variant="caption" sx={{
                        color: "text.secondary"
                    }}>用户: {message?.memberNickname} ({message?.memberEmail})</Typography>
                    <Paper variant="outlined" sx={{ p: 2, mt: 1, bgcolor: 'action.hover' }}>
                        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{message?.content}</Typography>
                    </Paper>
                </Box>
                <Divider sx={{ my: 2 }} />
                <TextField
                    autoFocus
                    margin="dense"
                    label="回复内容"
                    multiline
                    rows={4}
                    fullWidth
                    value={replyContent}
                    onChange={(e) => setReplyContent(e.target.value)}
                    placeholder="请输入回复..."
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained">发送回复</Button>
            </DialogActions>
        </Dialog>
    );
}

export default ReplyDialog;