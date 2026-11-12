import React, { useEffect } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, Box, Typography, IconButton, CircularProgress
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ScraperConfigForm from './components/ScraperConfigForm';
import ScraperRunningView from './components/ScraperRunningView';
import { useManualScraper } from './hooks/useManualScraper';

export default function ManualSearchDialog({ open, onClose }) {
    const {
        step,
        formData,
        setFormData,
        logs,
        cards,
        isFinished,
        isInitializing,
        startScraping,
        reset,
        clearLogs
    } = useManualScraper();

    useEffect(() => {
        if (!open) {
            reset();
        }
    }, [open, reset]);

    const handleStart = async () => {
        try {
            await startScraping();
        } catch (e) {
            alert("启动失败: " + e.message);
        }
    };

    const handleClose = () => {
        if (step === 2 && !isFinished) {
            if (!window.confirm("任务正在运行，确定要关闭吗？(后台将继续运行)")) return;
        }
        onClose();
        // reset() is called by useEffect when open becomes false
    };

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="xl" fullWidth>
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', pb: 1 }}>
                <Box>
                    全网即时搜索采集
                    {step === 2 && (
                        <Typography variant="caption" sx={{ ml: 2, color: 'text.secondary' }}>
                            {isFinished ? '采集完成' : '正在并行搜索所有数据源...'}
                        </Typography>
                    )}
                </Box>
                <IconButton onClick={handleClose}><CloseIcon /></IconButton>
            </DialogTitle>

            <DialogContent dividers sx={{ p: 0, height: '75vh', overflow: 'hidden' }}>
                {step === 1 ? (
                    <ScraperConfigForm
                        formData={formData}
                        setFormData={setFormData}
                    />
                ) : (
                    <ScraperRunningView
                        logs={logs}
                        cards={cards}
                        isFinished={isFinished}
                        onClearLogs={clearLogs}
                    />
                )}
            </DialogContent>

            <DialogActions sx={{ px: 3, py: 2 }}>
                {step === 1 ? (
                    <>
                        <Button onClick={handleClose} color="inherit">取消</Button>
                        <Button
                            variant="contained"
                            onClick={handleStart}
                            startIcon={isInitializing ? <CircularProgress size={20} color="inherit" /> : <PlayArrowIcon />}
                            disabled={!formData.keyword || isInitializing}
                        >
                            开始全网搜索
                        </Button>
                    </>
                ) : (
                    <Button
                        onClick={isFinished ? handleClose : undefined}
                        disabled={!isFinished}
                        variant="contained"
                        color={isFinished ? "primary" : "inherit"}
                    >
                        {isFinished ? "完成并关闭" : "正在运行..."}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    );
}