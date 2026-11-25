// File: frontends/admin/src/features/categories/CategoryList.jsx
import React from 'react';
import { Box } from '@mui/material';
import StandardCategoryTable from './StandardCategoryTable';

function CategoryList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <StandardCategoryTable />
        </Box>
    );
}

export default CategoryList;
