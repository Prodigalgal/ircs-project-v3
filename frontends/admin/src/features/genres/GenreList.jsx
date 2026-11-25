// File: frontends/admin/src/features/genres/GenreList.jsx
import React from 'react';
import { Box } from '@mui/material';
import StandardGenreTable from './StandardGenreTable';

function GenreList() {
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <StandardGenreTable />
        </Box>
    );
}

export default GenreList;
