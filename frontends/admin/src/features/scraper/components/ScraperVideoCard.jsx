import React from 'react';
import { Box, Chip, Link, Tooltip, Typography } from '@mui/material';
import ApiIcon from '@mui/icons-material/Api';

const STATUS_COLORS = {
    INSERTED: 'success',
    UPDATED: 'info',
    IGNORED: 'warning',
    FAILED: 'error'
};

const ScraperVideoCard = ({ data }) => (
    <Box
        sx={{
            display: 'flex',
            p: 1,
            bgcolor: '#1e1e1e',
            borderRadius: 1,
            borderLeft: `4px solid`,
            borderColor: data.errorMessage ? 'error.main' : (data.status === 'INSERTED' ? 'success.main' : 'info.main'),
            boxShadow: 1,
            transition: 'transform 0.2s',
            '&:hover': {
                transform: 'translateY(-2px)',
                boxShadow: 3
            }
        }}
    >
        <Box
            component="img"
            src={data.coverImageUrl}
            loading="lazy"
            decoding="async"
            sx={{ width: 70, height: 105, objectFit: 'cover', borderRadius: 1, bgcolor: '#000', flexShrink: 0 }}
            alt="cover"
            onError={(e) => e.target.src = 'https://via.placeholder.com/70x105?text=No+Img'}
            referrerPolicy="no-referrer"
        />
        <Box sx={{ ml: 1.5, flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
                    <Tooltip title={data.title}>
                        <Typography
                            variant="subtitle2"
                            noWrap
                            sx={{
                                fontWeight: "bold",
                                fontSize: '0.9rem',
                                maxWidth: '70%'
                            }}>
                            {data.title}
                        </Typography>
                    </Tooltip>
                    <Chip
                        label={data.errorMessage ? 'ERR' : data.status}
                        color={data.errorMessage ? 'error' : STATUS_COLORS[data.status] || 'default'}
                        size="small"
                        sx={{ height: 20, fontSize: '0.65rem', fontWeight: 'bold' }}
                    />
                </Box>
                <Typography
                    variant="caption"
                    noWrap
                    sx={{
                        color: "text.secondary",
                        display: "block"
                    }}>
                    {data.year || '-'} | {data.category || '未分类'}
                </Typography>

                <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5, gap: 1 }}>
                    <Chip
                        label={data.sourceName}
                        size="small"
                        variant="outlined"
                        sx={{ height: 18, fontSize: '0.6rem', borderColor: 'rgba(255,255,255,0.2)' }}
                    />
                    {data.detailUrl && (
                        <Tooltip title="查看源站 API 原始响应">
                            <Link href={data.detailUrl} target="_blank" rel="noopener" color="inherit" sx={{ display: 'flex' }}>
                                <ApiIcon sx={{ fontSize: 16, color: 'text.secondary', '&:hover': { color: 'primary.main' } }} />
                            </Link>
                        </Tooltip>
                    )}
                </Box>
            </Box>

            {data.errorMessage && (
                <Tooltip title={data.errorMessage}>
                    <Typography
                        variant="caption"
                        color="error"
                        noWrap
                        sx={{
                            display: "block",
                            fontSize: '0.7rem'
                        }}>
                        {data.errorMessage}
                    </Typography>
                </Tooltip>
            )}
        </Box>
    </Box>
);

export default ScraperVideoCard;
