// File: ircs-frontend/src/components/common/ListHeader.jsx
import React from 'react';
import { Box, Typography, Button, IconButton, Tooltip, Stack } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import PropTypes from 'prop-types';

function ListHeader({
                        title,
                        onRefresh,
                        onAdd,
                        addLabel = "新建",
                        loading = false,
                        subtitle,
                        extraActions
                    }) {
    return (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.25 }}>
            <Box>
                <Typography variant="subtitle1" sx={{ fontSize: '1rem', fontWeight: 700, lineHeight: 1.25 }}>
                    {title}
                </Typography>
                {subtitle && (
                    <Typography
                        variant="caption"
                        sx={{
                            color: "text.secondary",
                            fontSize: '0.72rem',
                            lineHeight: 1.2
                        }}>
                        {subtitle}
                    </Typography>
                )}
            </Box>
            <Stack direction="row" spacing={1} sx={{
                alignItems: "center"
            }}>
                {extraActions}

                {onRefresh && (
                    <Tooltip title="刷新列表">
                        <Button
                            variant="outlined"
                            startIcon={<RefreshIcon />}
                            onClick={onRefresh}
                            disabled={loading}
                            size="small"
                        >
                            刷新
                        </Button>
                    </Tooltip>
                )}

                {onAdd && (
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
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
    extraActions: PropTypes.node
};

export default ListHeader;
