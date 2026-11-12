// File: ircs-frontend/src/components/common/LockControlPanel.jsx
import React from 'react';
import { Box, Button, ButtonGroup, Typography, Tooltip } from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import RuleIcon from '@mui/icons-material/Rule';

function LockControlPanel({ lockedCount, totalCount, onLockAll, onUnlockAll }) {
    return (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2, p: 1, bgcolor: 'action.hover', borderRadius: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <RuleIcon color="primary" fontSize="small" />
                <Typography variant="body2" sx={{
                    fontWeight: "bold"
                }}>
                    字段保护策略:
                </Typography>
                <Typography variant="caption" sx={{
                    color: "text.secondary"
                }}>
                    已锁定 {lockedCount} / {totalCount} 个字段 (采集时将跳过更新)
                </Typography>
            </Box>
            <ButtonGroup size="small" variant="outlined">
                <Tooltip title="锁定所有字段，防止采集覆盖">
                    <Button onClick={onLockAll} startIcon={<LockIcon />}>
                        全部锁定
                    </Button>
                </Tooltip>
                <Tooltip title="解锁所有字段，允许采集覆盖">
                    <Button onClick={onUnlockAll} startIcon={<LockOpenIcon />}>
                        全部解锁
                    </Button>
                </Tooltip>
            </ButtonGroup>
        </Box>
    );
}

export default LockControlPanel;