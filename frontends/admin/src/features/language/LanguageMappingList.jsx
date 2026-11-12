import React from 'react';
import { Box, Typography } from '@mui/material';
import StandardLanguageTable from './StandardLanguageTable';

function LanguageMappingList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ mb: 2 }}>
                <Typography variant="h5" gutterBottom>语言管理</Typography>
            </Box>
            <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <StandardLanguageTable />
            </Box>
        </Box>
    );
}

export default LanguageMappingList;
