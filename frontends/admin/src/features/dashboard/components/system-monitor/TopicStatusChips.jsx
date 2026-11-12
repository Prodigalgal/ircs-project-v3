import React from 'react';
import { Chip, Stack } from '@mui/material';

const topicLabel = {
    metrics: 'Metrics',
    'task-runtime': 'Task Runtime',
    'search-ops': 'Search Ops',
    'aggregation-ops': 'Aggregation Ops'
};

function TopicStatusChips({ streamState }) {
    if (!streamState) {
        return null;
    }

    return (
        <Stack
            direction="row"
            spacing={1}
            useFlexGap
            sx={{
                flexWrap: "wrap",
                mb: 2
            }}>
            {Object.entries(streamState.topics || {}).map(([topic, state]) => (
                <Chip
                    key={topic}
                    size="small"
                    variant={state?.connected ? 'filled' : 'outlined'}
                    color={state?.connected ? 'success' : (state?.connecting ? 'info' : (state?.error ? 'warning' : 'default'))}
                    label={`${topicLabel[topic] || topic}: ${state?.connected ? 'SSE' : (state?.connecting ? '连接中' : 'REST')}`}
                />
            ))}
        </Stack>
    );
}

export default TopicStatusChips;
