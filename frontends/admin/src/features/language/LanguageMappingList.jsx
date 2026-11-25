// File: frontends/admin/src/features/language/LanguageMappingList.jsx
import React from 'react';
import { Box } from '@mui/material';
import StandardLanguageTable from './StandardLanguageTable';

function LanguageMappingList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <StandardLanguageTable />
        </Box>
    );
}

export default LanguageMappingList;
