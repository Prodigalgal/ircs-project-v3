// File: frontends/admin/src/components/common/ListHeader.jsx
import React from 'react';
import { Box, Typography, Button, Tooltip, Stack } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import PropTypes from 'prop-types';

function ListHeader({
    title,
    onRefresh,
    onAdd,
    addLabel = '新建',
    loading = false,
    subtitle,
    extraActions,
}) {
    return (
        <Box
            sx={{
                display: 'flex',
                flexWrap: 'wrap',
                justifyContent: 'space-between',
                alignItems: { xs: 'flex-start', sm: 'center' },
                gap: 1.5,
                mb: 2,
            }}
        >
            <Box sx={{ minWidth: 0 }}>
                <Typography
                    variant="h5"
                    sx={{
                        fontSize: { xs: '1.1rem', sm: '1.2rem' },
                        fontWeight: 700,
                        letterSpacing: '-0.015em',
                        lineHeight: 1.25,
                        color: 'text.primary',
                    }}
                >
                    {title}
                </Typography>
                {subtitle && (
                    <Typography
                        variant="caption"
                        sx={{
                            color: 'text.secondary',
                            fontSize: '0.78rem',
                            lineHeight: 1.4,
                            mt: 0.35,
                            display: 'block',
                        }}
                    >
                        {subtitle}
                    </Typography>
                )}
            </Box>

            <Stack
                direction="row"
                spacing={1}
                useFlexGap
                sx={{
                    alignItems: 'center',
                    flexWrap: 'wrap',
                }}
            >
                {extraActions}

                {onRefresh && (
                    <Tooltip title="刷新数据">
                        <span>
                            <Button
                                variant="outlined"
                                color="inherit"
                                startIcon={<RefreshIcon sx={{ fontSize: '17px !important' }} />}
                                onClick={onRefresh}
                                disabled={loading}
                                size="small"
                                sx={{
                                    borderColor: 'divider',
                                    bgcolor: 'background.paper',
                                    color: 'text.secondary',
                                    '&:hover': {
                                        color: 'primary.main',
                                        borderColor: 'primary.main',
                                    },
                                }}
                            >
                                刷新
                            </Button>
                        </span>
                    </Tooltip>
                )}

                {onAdd && (
                    <Button
                        variant="contained"
                        color="primary"
                        startIcon={<AddIcon sx={{ fontSize: '17px !important' }} />}
                        onClick={onAdd}
                        disabled={loading}
                        size="small"
                    >
                        {addLabel}
                    </Button>
                )}
            </Stack>
        </Box>
    );
}

ListHeader.propTypes = {
    title: PropTypes.string.isRequired,
    subtitle: PropTypes.string,
    onRefresh: PropTypes.func,
    onAdd: PropTypes.func,
    addLabel: PropTypes.string,
    loading: PropTypes.bool,
    extraActions: PropTypes.node,
};

export default ListHeader;
