import { stableRate } from './systemMonitorFormatters';

export const blockedReasonLabels = {
    NO_CONSUMER_IMPL: '无消费者实现',
    DISABLED_BY_CONFIG: '配置已关闭',
    NO_ACTIVE_CONSUMER: '无活跃消费者',
    CONSUMER_STALE: '无活跃消费者',
    CONSUMER_NO_PROGRESS: '无进展',
    PROCESSING: '处理中',
    CONSUMER_PROCESSING_STUCK: '处理卡住',
    RABBIT_RATE_UNAVAILABLE: 'Rabbit 速率不可用',
    UNKNOWN: '原因未知'
};

export const rateKindLabels = {
    CLAIM: '领取',
    COMPLETE: '完成',
    FAIL: '失败',
    PUBLISH: '发布',
    DELIVER: '投递',
    ACK: '确认',
    REDELIVER: '重投递',
    NONE: 'NONE'
};

const VALKEY_RUNTIME_GROUP_TITLE = 'Valkey Runtime Work';
const LEGACY_VALKEY_GROUP_TITLES = new Set(['Valkey Pipeline']);

function queueBacklog(groups) {
    return groups
        .flatMap(group => group.queues || [])
        .reduce((sum, queue) => sum + (queue.messageCount || 0), 0);
}

function buildChartData(metrics) {
    return (metrics?.history?.data || []).map((metric, idx) => ({
        time: idx,
        pageDiscovery: stableRate(metric.pageDiscoveryRate),
        detailCollection: stableRate(metric.detailCollectionRate),
        normalization: stableRate(metric.normalizationRate),
        metadata: stableRate(metric.metadataRate),
        backlog: metric.totalQueueBacklog || 0,
        redisMem: Math.round(metric.redisMetric?.usedMemoryBytes / 1024 / 1024) || 0,
        cpuThreads: metric.activeThreads
    }));
}

function splitQueueGroups(metrics) {
    const queueGroups = metrics?.queueGroups || [];
    const valkeyGroups = queueGroups.filter(group => group.title === VALKEY_RUNTIME_GROUP_TITLE);
    const dlqGroups = queueGroups.filter(group => group.dlq);
    const rabbitGroups = queueGroups.filter(group =>
        !group.dlq
        && group.title !== VALKEY_RUNTIME_GROUP_TITLE
        && !LEGACY_VALKEY_GROUP_TITLES.has(group.title)
    );

    return {
        valkeyGroups,
        rabbitGroups,
        dlqGroups
    };
}

export function createSystemMonitorViewModel({ metrics, searchOps, aggregationOps }) {
    const groups = splitQueueGroups(metrics);
    const aggregationWorkerState = aggregationOps?.worker || null;

    const searchLaneState = (taskType) => searchOps?.worker?.lanes?.[taskType] || null;
    const queueLaneState = (queue) => {
        if (queue?.key?.includes('SEARCH_RAW')) return searchLaneState('search.sync.raw');
        if (queue?.key?.includes('SEARCH_UNIFIED')) return searchLaneState('search.sync.unified');
        if (queue?.key?.includes('AGGREGATION')) return aggregationWorkerState;
        return null;
    };
    const laneSummary = (lane) => {
        if (!lane) return null;
        const stage = lane.currentStage || lane.lastRunState || '-';
        const entity = lane.currentEntityId || lane.currentRawVideoId || lane.currentTaskId || '-';
        const seconds = lane.runningForSeconds || 0;
        const taskType = lane.taskType || 'aggregation.raw-video';
        const batch = lane.currentBatchSize ? ` · batch ${lane.currentBatchSize}` : '';
        return `${taskType}: ${stage} · ${entity}${batch} · ${seconds}s`;
    };

    return {
        ...groups,
        chartData: buildChartData(metrics),
        valkeyBacklog: queueBacklog(groups.valkeyGroups),
        rabbitBacklog: queueBacklog(groups.rabbitGroups),
        dlqBacklog: queueBacklog(groups.dlqGroups),
        searchLaneState,
        aggregationWorkerState,
        queueLaneState,
        laneSummary
    };
}
