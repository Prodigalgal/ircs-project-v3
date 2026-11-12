import React from 'react';
import { Box, Typography } from '@mui/material';
import StandardAreaTable from './StandardAreaTable';

function AreaList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ mb: 2 }}>
                <Typography variant="h5" gutterBottom>地区管理 (Areas)</Typography>
            </Box>
            <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <StandardAreaTable />
            </Box>
        </Box>
    );
}

export default AreaList;
