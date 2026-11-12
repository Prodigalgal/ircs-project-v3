import { useMemo } from 'react';

export function useSystemConfigMaps(configsData) {
    return useMemo(() => {
        const configsMap = {};
        const configMetaMap = {};

        configsData?.content?.forEach(config => {
            configsMap[config.key] = config.effectiveValue ?? config.value ?? '';
            configMetaMap[config.key] = {
                activationMode: config.activationMode || 'HOT',
                restartServices: config.restartServices || []
            };
        });

        return { configsMap, configMetaMap };
    }, [configsData]);
}
