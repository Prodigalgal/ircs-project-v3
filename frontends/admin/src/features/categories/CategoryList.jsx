// ircs-frontend/src/features/categories/CategoryList.jsx
import React from 'react';
import { Box, Typography } from '@mui/material';
import StandardCategoryTable from './StandardCategoryTable';

function CategoryList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ mb: 2 }}>
                <Typography variant="h5" gutterBottom>分类管理</Typography>
            </Box>

            <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <StandardCategoryTable />
            </Box>
        </Box>
    );
}

export default CategoryList;
