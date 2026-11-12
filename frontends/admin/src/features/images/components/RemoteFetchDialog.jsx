// ircs-frontend/src/features/images/components/RemoteFetchDialog.jsx
import React, { useState } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, LinearProgress } from '@mui/material';
import { useFetchCoverImageMutation } from '../coverImagesApi';

function RemoteFetchDialog({ open, onClose }) {
    const [url, setUrl] = useState('');
    const [fetchImage, { isLoading }] = useFetchCoverImageMutation();

    const handleSubmit = async () => {
        if (!url.trim()) return;
        try {
            await fetchImage(url).unwrap();
            handleClose();
        } catch (err) {
            console.error(err);
            alert("Request failed: " + (err.data?.detail || err.message));
        }
    };

    const handleClose = () => {
        setUrl('');
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>拉取远程图片</DialogTitle>
            <DialogContent dividers>
                <TextField
                    autoFocus
                    margin="dense"
                    label="图片 URL"
                    type="url"
                    fullWidth
                    variant="outlined"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://example.com/image.jpg"
                />
                {isLoading && <LinearProgress sx={{ mt: 2 }} />}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>取消</Button>
                <Button variant="contained" onClick={handleSubmit} disabled={!url || isLoading}>提交任务</Button>
            </DialogActions>
        </Dialog>
    );
}

export default RemoteFetchDialog;