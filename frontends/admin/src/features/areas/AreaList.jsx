// File: frontends/admin/src/features/areas/AreaList.jsx
import React from 'react';
import { Box } from '@mui/material';
import StandardAreaTable from './StandardAreaTable';

function AreaList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <StandardAreaTable />
        </Box>
    );
}

export default AreaList;
