// File: ircs-frontend/src/utils/constants.js
export const ENRICH_STATUS = {
    PENDING: { label: '等待中', color: 'default' },
    PARTIAL_SUCCESS: { label: '部分成功', color: 'info' },
    SUCCESS: { label: '已完善', color: 'success' },
    FAILED: { label: '重试中', color: 'warning' },
    PERMANENT_FAILURE: { label: '失败', color: 'error' }
};

export const NORMALIZATION_STATUS = {
    PENDING: { label: '待清洗', color: 'warning' },
    PROCESSING: { label: '处理中', color: 'info' },
    READY: { label: '已就绪', color: 'success' },
    FAILED: { label: '重试中', color: 'warning' },
    PERMANENT_FAILURE: { label: '永久失败', color: 'error' }
};

export const AGGREGATION_STATUS = {
    PENDING: { label: '待聚合', color: 'warning' },
    PROCESSING: { label: '聚合中', color: 'info' },
    BOUND: { label: '已绑定', color: 'success' },
    ORPHANED: { label: '孤儿', color: 'error' }
};

export const METADATA_STATUS = {
    SYNCED: { label: '已同步', color: 'success' },
    DIRTY: { label: '待重算', color: 'warning' }
};

export const IMAGE_STATUS = {
    UNPROCESSED: { label: '未处理', color: 'default' },
    FETCHING: { label: '下载中', color: 'info' },
    LOCAL_STORED: { label: '本地存储', color: 'success' },
    UPLOADING: { label: '上传中', color: 'info' },
    REMOTE_STORED: { label: 'R2 托管', color: 'success' },
    FAILED: { label: '失败', color: 'error' },
    DEAD: { label: '已死链', color: 'error' },
    PENDING_DELETE: { label: '待删除', color: 'warning' }
};

export const TASK_STATUS = {
    IDLE: { label: '空闲', color: 'default' },
    QUEUED: { label: '排队中', color: 'warning' },
    RUNNING: { label: '运行中', color: 'primary' },
    PAUSED: { label: '已暂停', color: 'warning' },
    STOPPING: { label: '停止中', color: 'error' },
    COMPLETED: { label: '已完成', color: 'success' },
    FAILED: { label: '失败', color: 'error' }
};

export const STORAGE_TYPE = {
    EXTERNAL: 'External',
    LOCAL: 'Local',
    R2: 'R2'
};

export const getStatusConfig = (map, status) => {
    return map[status] || { label: status || '未知', color: 'default' };
};