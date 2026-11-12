import React from 'react';
import { Box, Typography } from '@mui/material';
import StandardGenreTable from './StandardGenreTable';

function GenreList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ mb: 2 }}>
                <Typography variant="h5" gutterBottom>题材管理 (Genres)</Typography>
            </Box>
            <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <StandardGenreTable />
            </Box>
        </Box>
    );
}

export default GenreList;
