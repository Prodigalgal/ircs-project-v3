// ircs-frontend/src/features/images/components/ImageUploadDialog.jsx
import React, { useState } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Box, Typography, LinearProgress } from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import { useUploadCoverImageMutation } from '../coverImagesApi';

function ImageUploadDialog({ open, onClose }) {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [upload, { isLoading }] = useUploadCoverImageMutation();

    const handleFileChange = (e) => {
        const selected = e.target.files[0];
        if (selected) {
            setFile(selected);
            setPreview(URL.createObjectURL(selected));
        }
    };

    const handleUpload = async () => {
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);
        try {
            await upload(formData).unwrap();
            handleClose();
        } catch (err) {
            console.error(err);
            alert("Upload failed: " + (err.data?.detail || err.message));
        }
    };

    const handleClose = () => {
        setFile(null);
        setPreview(null);
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
            <DialogTitle>手动上传图片</DialogTitle>
            <DialogContent dividers>
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 2 }}>
                    <Button
                        variant="outlined"
                        component="label"
                        startIcon={<CloudUploadIcon />}
                    >
                        选择文件
                        <input type="file" hidden accept="image/*" onChange={handleFileChange} />
                    </Button>
                    {file && (
                        <Typography variant="body2">{file.name} ({(file.size / 1024).toFixed(1)} KB)</Typography>
                    )}
                    {preview && (
                        <Box
                            component="img"
                            src={preview}
                            alt="preview"
                            sx={{ maxHeight: 200, maxWidth: '100%', borderRadius: 1, border: '1px solid #444' }}
                        />
                    )}
                    {isLoading && <LinearProgress sx={{ width: '100%' }} />}
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>取消</Button>
                <Button variant="contained" onClick={handleUpload} disabled={!file || isLoading}>上传</Button>
            </DialogActions>
        </Dialog>
    );
}

export default ImageUploadDialog;