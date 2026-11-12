// File: ircs-frontend/src/shared/hooks/useEntityLocking.js
import { useState, useCallback } from 'react';

/**
 * 管理实体字段锁定状态的 Hook
 * @param {Array<string>} availableFields - 支持锁定的字段列表
 * @param {Array<string>} initialLockedFields - 初始锁定的字段
 * @returns {Object} 锁定状态与操作函数
 */
export function useEntityLocking(availableFields = [], initialLockedFields = []) {
    const [lockedFields, setLockedFields] = useState(initialLockedFields);

    const isLocked = useCallback((fieldName) => {
        return lockedFields.includes(fieldName);
    }, [lockedFields]);

    const toggleLock = useCallback((fieldName, shouldLock) => {
        setLockedFields(prev => {
            const currentLocks = new Set(prev);
            if (shouldLock) {
                currentLocks.add(fieldName);
            } else {
                currentLocks.delete(fieldName);
            }
            return Array.from(currentLocks);
        });
    }, []);

    const lockAll = useCallback(() => {
        setLockedFields([...availableFields]);
    }, [availableFields]);

    const unlockAll = useCallback(() => {
        setLockedFields([]);
    }, []);

    // 用于在表单重置时覆盖状态
    const setLocks = useCallback((newLocks) => {
        setLockedFields(newLocks || []);
    }, []);

    return {
        lockedFields,
        isLocked,
        toggleLock,
        lockAll,
        unlockAll,
        setLocks,
        lockedCount: lockedFields.length,
        totalLockableCount: availableFields.length
    };
}