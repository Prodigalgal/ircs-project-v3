// File: ircs-frontend/src/features/playlists/components/PlaylistManagerDialog.jsx
import React, { useState, useEffect } from 'react';
import {
    Dialog, DialogTitle, DialogContent, DialogActions,
    Button, TextField, Box, Tabs, Tab, IconButton,
    Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
    Paper, Typography, Alert, TablePagination
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import ContentPasteIcon from '@mui/icons-material/ContentPaste';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';

function PlaylistManagerDialog({ open, onClose, onSubmit, playlist, videoId, videoTitle }) {
    const [name, setName] = useState('');
    const [episodes, setEpisodes] = useState([]);
    const [tabIndex, setTabIndex] = useState(0);
    const [rawText, setRawText] = useState('');

    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(100);

    useEffect(() => {
        if (open) {
            if (playlist) {
                setName(playlist.name || '默认');
                setEpisodes(playlist.episodes || []);
            } else {
                setName('默认');
                setEpisodes([]);
            }
            setRawText('');
            setTabIndex(0);
            setPage(0);
        }
    }, [open, playlist]);

    const handleAddRow = () => {
        setEpisodes([...episodes, { name: `第${episodes.length + 1}集`, url: '' }]);
    };

    const handleRowChange = (index, field, value) => {
        const newEpisodes = [...episodes];
        newEpisodes[index] = { ...newEpisodes[index], [field]: value };
        setEpisodes(newEpisodes);
    };

    const handleDeleteRow = (index) => {
        setEpisodes(episodes.filter((_, i) => i !== index));
    };

    const handleParse = () => {
        if (!rawText.trim()) return;

        const lines = rawText.split(/[\n#]+/).map(l => l.trim()).filter(Boolean);
        const parsed = lines.map(line => {
            const parts = line.split('$');
            if (parts.length >= 2) {
                return { name: parts[0].trim(), url: parts[1].trim() };
            }
            if (line.startsWith('http')) {
                return { name: `集`, url: line };
            }
            return null;
        }).filter(Boolean);

        if (parsed.length > 0) {
            if (episodes.length === 0) {
                setEpisodes(parsed);
            } else {
                if (window.confirm(`解析到 ${parsed.length} 集。覆盖现有列表吗？(点击"取消"则追加到末尾)`)) {
                    setEpisodes(parsed);
                } else {
                    setEpisodes([...episodes, ...parsed]);
                }
            }
            setTabIndex(0);
            setRawText('');
            setPage(0);
        } else {
            alert('未能解析有效数据。请使用 "名称$URL" 格式。');
        }
    };

    const handleSubmit = () => {
        if (!name.trim()) return alert('列表名称不能为空');

        const validEpisodes = episodes.filter(e => e.url && e.url.trim() !== '');

        onSubmit({
            id: playlist?.id,
            videoId: videoId || playlist?.videoId,
            name,
            episodes: validEpisodes
        });
    };

    const displayedEpisodes = episodes.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);
    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };
    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(parseInt(event.target.value, 10));
        setPage(0);
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle>
                {playlist ? '编辑播放列表' : '新建播放列表'}
                <Typography
                    variant="caption"
                    sx={{
                        display: "block",
                        color: "text.secondary"
                    }}>
                    所属视频: {videoTitle || playlist?.videoTitle || '未知'}
                </Typography>
            </DialogTitle>

            <DialogContent dividers sx={{ p: 0 }}>
                <Box sx={{ px: 3, pt: 2, pb: 1, bgcolor: 'background.paper' }}>
                    <TextField
                        label="列表名称 (如: 腾讯视频, M3U8)"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        fullWidth
                        size="small"
                        margin="dense"
                    />
                </Box>

                <Box sx={{ borderBottom: 1, borderColor: 'divider', px: 2 }}>
                    <Tabs value={tabIndex} onChange={(e, v) => setTabIndex(v)}>
                        <Tab label={`剧集列表 (${episodes.length})`} />
                        <Tab label="批量解析 / 文本导入" icon={<AutoFixHighIcon fontSize="small" />} iconPosition="start" />
                    </Tabs>
                </Box>

                {tabIndex === 0 && (
                    <Box sx={{ p: 2, height: 500, display: 'flex', flexDirection: 'column' }}>
                        <Box sx={{ mb: 1, display: 'flex', justifyContent: 'flex-end' }}>
                            <Button startIcon={<AddIcon />} size="small" onClick={handleAddRow}>
                                添加一集
                            </Button>
                        </Box>
                        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1 }}>
                            <Table size="small" stickyHeader>
                                <TableHead>
                                    <TableRow>
                                        <TableCell width="60">序号</TableCell>
                                        <TableCell width="25%">名称</TableCell>
                                        <TableCell>播放地址 (URL)</TableCell>
                                        <TableCell width="60" align="center">操作</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {displayedEpisodes.map((ep, idx) => {
                                        const realIndex = page * rowsPerPage + idx;
                                        return (
                                            <TableRow key={realIndex}>
                                                <TableCell sx={{ color: 'text.secondary' }}>{realIndex + 1}</TableCell>
                                                <TableCell>
                                                    <TextField
                                                        value={ep.name}
                                                        onChange={(e) => handleRowChange(realIndex, 'name', e.target.value)}
                                                        size="small"
                                                        variant="standard"
                                                        fullWidth
                                                        placeholder="集数"
                                                    />
                                                </TableCell>
                                                <TableCell>
                                                    <TextField
                                                        value={ep.url}
                                                        onChange={(e) => handleRowChange(realIndex, 'url', e.target.value)}
                                                        size="small"
                                                        variant="standard"
                                                        fullWidth
                                                        placeholder="http://..."
                                                    />
                                                </TableCell>
                                                <TableCell align="center">
                                                    <IconButton size="small" color="error" onClick={() => handleDeleteRow(realIndex)} tabIndex={-1}>
                                                        <DeleteIcon fontSize="small" />
                                                    </IconButton>
                                                </TableCell>
                                            </TableRow>
                                        );
                                    })}
                                    {episodes.length === 0 && (
                                        <TableRow>
                                            <TableCell colSpan={4} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                                                暂无数据，请添加或使用批量解析
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </TableContainer>
                        <TablePagination
                            component="div"
                            count={episodes.length}
                            page={page}
                            onPageChange={handleChangePage}
                            rowsPerPage={rowsPerPage}
                            onRowsPerPageChange={handleChangeRowsPerPage}
                            rowsPerPageOptions={[50, 100, 200]}
                            labelRowsPerPage="每页行数:"
                        />
                    </Box>
                )}

                {tabIndex === 1 && (
                    <Box sx={{ p: 3, height: 400, display: 'flex', flexDirection: 'column' }}>
                        <Alert severity="info" sx={{ mb: 2 }}>
                            支持格式：<code>名称$地址</code> (每行一个，或用 # 分隔)。<br/>
                            例如：<code>第01集$https://example.com/1.m3u8</code>
                        </Alert>
                        <TextField
                            multiline
                            rows={10}
                            fullWidth
                            value={rawText}
                            onChange={(e) => setRawText(e.target.value)}
                            placeholder={`第01集$http://url1.m3u8\n第02集$http://url2.m3u8`}
                            variant="outlined"
                            sx={{ flex: 1 }}
                        />
                        <Box sx={{ mt: 2, display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
                            <Button onClick={() => setRawText('')}>清空</Button>
                            <Button variant="contained" startIcon={<ContentPasteIcon />} onClick={handleParse}>
                                解析并导入
                            </Button>
                        </Box>
                    </Box>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>取消</Button>
                <Button onClick={handleSubmit} variant="contained" color="primary">保存更改</Button>
            </DialogActions>
        </Dialog>
    );
}

export default PlaylistManagerDialog;