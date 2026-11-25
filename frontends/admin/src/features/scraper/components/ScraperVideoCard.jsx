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
            p: 1.25,
            bgcolor: 'background.paper',
            borderRadius: 2,
            border: '1px solid',
            borderColor: 'divider',
            borderLeftWidth: 3,
            borderLeftColor: data.errorMessage ? 'error.main' : (data.status === 'INSERTED' ? 'success.main' : 'primary.main'),
            transition: 'border-color 160ms ease, transform 160ms ease',
            '&:hover': {
                transform: 'translateY(-1px)',
                borderColor: 'surface.borderStrong',
                borderLeftColor: data.errorMessage ? 'error.main' : (data.status === 'INSERTED' ? 'success.main' : 'primary.main'),
            },
        }}
    >
        <Box
            component="img"
            src={data.coverImageUrl}
            loading="lazy"
            decoding="async"
            sx={{ width: 68, height: 102, objectFit: 'cover', borderRadius: 1.5, bgcolor: 'surface.subtle', border: '1px solid', borderColor: 'divider', flexShrink: 0 }}
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
                        sx={{ height: 20, fontSize: '0.65rem' }}
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
