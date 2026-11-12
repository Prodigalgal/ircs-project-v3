// File: ircs-frontend/src/shared/hooks/useTaskProgress.js
import { useEffect, useMemo, useState } from 'react';
import { formatDuration } from '../../utils/formatUtils';

export function useTaskProgress(task) {
    const [now, setNow] = useState(Date.now);
    const status = task?.status;
    useEffect(() => {
        if (!['RUNNING', 'PAUSED', 'STOPPING'].includes(status)) return;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [status]);
    return useMemo(() => {
        if (!task) return null;

        const getVal = (...keys) => {
            for (const k of keys) {
                if (task[k] !== undefined && task[k] !== null) {
                    return Number(task[k]);
                }
            }
            return 0;
        };

        const getStr = (...keys) => {
            for (const k of keys) {
                if (task[k]) return task[k];
            }
            return null;
        };

        const processed = getVal('statProcessed', 'processed', 'stat_processed', 'processedCount');

        const inserted = getVal('statInserted', 'inserted', 'stat_inserted', 'insertCount');
        const updated = getVal('statUpdated', 'updated', 'stat_updated', 'updateCount');
        const ignored = getVal('statIgnored', 'ignored', 'stat_ignored', 'ignoreCount');
        const failed = getVal('statFailed', 'failed', 'stat_failed', 'failCount', 'errorCount', 'errors');
        const rawSuccess = getVal('statSuccess', 'success', 'stat_success', 'successCount');

        const totalFound = getVal('statTotalFound', 'totalFound', 'stat_total_found', 'total');
        const startTimeStr = getStr('statStartTime', 'startTime', 'stat_start_time');
        const endTimeStr = getStr('statEndTime', 'endTime', 'stat_end_time');

        const startPage = task.startPage || 1;
        const configEndPage = task.endPage || 0;
        const currentPage = task.currentPage || startPage;

        const computedEndPage = configEndPage > 0
            ? configEndPage
            : (totalFound > 0 ? Math.ceil(totalFound / 20) : 1);

        const totalPages = Math.max(1, computedEndPage - startPage + 1);
        const pagesDone = Math.max(0, currentPage - startPage);
        const hasPageBound = configEndPage > 0 || totalFound > 0;
        const pageProgressPercent = Math.min(100, Math.max(0, (pagesDone / totalPages) * 100));
        const itemProgressPercent = totalFound > 0
            ? Math.min(100, Math.max(0, (processed / totalFound) * 100))
            : 0;
        const estimatedItemsPerPage = totalFound > 0 && computedEndPage > 0
            ? Math.max(1, Math.round(totalFound / computedEndPage))
            : 20;
        const estimatedDiscovered = pagesDone * estimatedItemsPerPage;
        const discovered = Math.min(
            totalFound > 0 ? totalFound : Number.MAX_SAFE_INTEGER,
            Math.max(processed, estimatedDiscovered)
        );

        let progressPercent = 0;
        let isIndeterminate = false;

        if (task.status === 'RUNNING' || task.status === 'PAUSED' || task.status === 'STOPPING') {
            progressPercent = pageProgressPercent;

            if (task.status === 'RUNNING' && !hasPageBound) {
                isIndeterminate = true;
            }

            progressPercent = Math.min(100, Math.max(0, progressPercent));
        } else if (task.status === 'COMPLETED') {
            progressPercent = 100;
        } else if (task.status === 'FAILED') {
            progressPercent = pageProgressPercent;
        }

        let speed = 0;
        let duration = 0;
        let etaText = '';
        let etaMs = 0;

        if (startTimeStr) {
            const endTime = endTimeStr ? new Date(endTimeStr).getTime() : now;
            const startTime = new Date(startTimeStr).getTime();
            duration = endTime - startTime;

            if (duration > 1000 && processed > 0) {
                speed = processed / (duration / 1000);
            }

            if (task.status === 'RUNNING' && processed > 0 && duration > 3000) {
                let remainingItems = 0;
                if (totalFound > 0) {
                    remainingItems = Math.max(0, totalFound - processed);
                } else {
                    const totalEstimatedItems = (computedEndPage - startPage + 1) * 20;
                    remainingItems = Math.max(0, totalEstimatedItems - processed);
                }

                const msPerItem = duration / processed;
                etaMs = msPerItem * remainingItems;
                etaText = formatDuration(etaMs);
            }
        }

        const totalStats = inserted + updated + ignored + failed;
        const hasBreakdownStats = processed === 0 || totalStats > 0;

        const safeTotal = totalStats === 0 ? 1 : totalStats;

        const derivedSuccess = rawSuccess > 0 ? rawSuccess : Math.max(0, processed - failed);
        const successRate = processed > 0 ? ((derivedSuccess / processed) * 100).toFixed(1) : '0.0';

        return {
            processed, inserted, updated, ignored, failed, success: derivedSuccess,
            discovered,
            statTotalFound: totalFound,
            currentPage, startPage, computedEndPage, configEndPage,

            progressPercent: Math.round(progressPercent),
            pageProgressPercent: Math.round(progressPercent),
            itemProgressPercent: Math.round(itemProgressPercent),
            isIndeterminate,
            speed: speed.toFixed(1),
            duration,
            durationFormatted: formatDuration(duration),
            etaText,
            etaMs,
            successRate,

            hasBreakdownStats,

            distribution: {
                ins: (inserted / safeTotal) * 100,
                upd: (updated / safeTotal) * 100,
                ign: (ignored / safeTotal) * 100,
                fail: (failed / safeTotal) * 100
            }
        };

    }, [task, now]);
}
