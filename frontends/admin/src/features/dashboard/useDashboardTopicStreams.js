import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AuthenticatedEventStream } from '../../utils/AuthenticatedEventStream';

const TOPICS = [
    { key: 'metrics', stateKey: 'metrics', url: '/api/v1/dashboard/stream/metrics' },
    { key: 'task-runtime', stateKey: 'taskRuntime', url: '/api/v1/dashboard/stream/task-runtime?limit=50' },
    { key: 'search-ops', stateKey: 'searchOps', url: '/api/v1/dashboard/stream/search-ops' },
    { key: 'aggregation-ops', stateKey: 'aggregationOps', url: '/api/v1/dashboard/stream/aggregation-ops' }
];
const TOPIC_CONNECT_STAGGER_MS = 350;

const INITIAL_TOPIC_STATE = {
    connected: false,
    connecting: false,
    fallback: false,
    error: null,
    updatedAt: null
};

const INITIAL_STATE = {
    metrics: null,
    taskRuntime: null,
    searchOps: null,
    aggregationOps: null,
    topics: TOPICS.reduce((acc, topic) => ({ ...acc, [topic.key]: INITIAL_TOPIC_STATE }), {})
};

export function useDashboardTopicStreams(enabled) {
    const [state, setState] = useState(INITIAL_STATE);
    const sourcesRef = useRef(new Map());
    const reconnectTimersRef = useRef(new Map());
    const connectTimersRef = useRef([]);
    const reconnectAttemptsRef = useRef(new Map());
    const [reconnectNonce, setReconnectNonce] = useState(0);

    const disconnect = useCallback(() => {
        connectTimersRef.current.forEach(timer => clearTimeout(timer));
        connectTimersRef.current = [];
        sourcesRef.current.forEach(source => source.close());
        sourcesRef.current.clear();
        reconnectTimersRef.current.forEach(timer => clearTimeout(timer));
        reconnectTimersRef.current.clear();
    }, []);

    const setTopicState = useCallback((topic, updater) => {
        setState(prev => ({
            ...prev,
            topics: {
                ...prev.topics,
                [topic]: updater(prev.topics[topic] || INITIAL_TOPIC_STATE)
            }
        }));
    }, []);

    const reconnect = useCallback(() => {
        disconnect();
        reconnectAttemptsRef.current.clear();
        setReconnectNonce(value => value + 1);
        setState(INITIAL_STATE);
    }, [disconnect]);

    useEffect(() => {
        if (!enabled) {
            disconnect();
            setState(INITIAL_STATE);
            return undefined;
        }

        let disposed = false;
        const connectTopic = (topicConfig) => {
            if (disposed) return;
            const { key, stateKey, url } = topicConfig;
            setTopicState(key, prev => ({ ...prev, connecting: true, error: null }));
            const source = new AuthenticatedEventStream(url, { reconnect: false });
            sourcesRef.current.set(key, source);

            source.addEventListener(key, event => {
                try {
                    const data = JSON.parse(event.data);
                    reconnectAttemptsRef.current.set(key, 0);
                    setState(prev => ({
                        ...prev,
                        [stateKey]: data,
                        topics: {
                            ...prev.topics,
                            [key]: {
                                connected: true,
                                connecting: false,
                                fallback: false,
                                error: null,
                                updatedAt: new Date().toISOString()
                            }
                        }
                    }));
                } catch (error) {
                    setTopicState(key, prev => ({
                        ...prev,
                        connected: false,
                        connecting: false,
                        fallback: true,
                        error: error.message || 'Topic stream parse failed'
                    }));
                }
            });

            source.onerror = (event) => {
                source.close();
                sourcesRef.current.delete(key);
                if (disposed) return;
                const attempt = (reconnectAttemptsRef.current.get(key) || 0) + 1;
                reconnectAttemptsRef.current.set(key, attempt);
                const delay = Math.min(30000, 1000 * Math.pow(2, Math.min(attempt, 5)));
                setTopicState(key, prev => ({
                    ...prev,
                    connected: false,
                    connecting: false,
                    fallback: !event.error.retryable || attempt >= 2,
                    error: event.error.message
                }));
                if (!event.error.retryable) return;
                const timer = setTimeout(() => connectTopic(topicConfig), delay);
                reconnectTimersRef.current.set(key, timer);
            };
        };

        TOPICS.forEach((topicConfig, index) => {
            const timer = setTimeout(
                () => connectTopic(topicConfig),
                index * TOPIC_CONNECT_STAGGER_MS
            );
            connectTimersRef.current.push(timer);
        });

        return () => {
            disposed = true;
            disconnect();
        };
    }, [enabled, reconnectNonce, disconnect, setTopicState]);

    const streamState = useMemo(() => {
        const topicValues = Object.values(state.topics);
        const connected = topicValues.some(topic => topic.connected);
        const connecting = topicValues.some(topic => topic.connecting);
        const fallback = topicValues.some(topic => topic.fallback);
        const errors = Object.entries(state.topics)
            .filter(([, topic]) => topic.error)
            .reduce((acc, [key, topic]) => ({ ...acc, [key]: topic.error }), {});
        return {
            connected,
            connecting,
            fallback,
            errors,
            topics: state.topics
        };
    }, [state.topics]);

    return {
        ...state,
        streamState,
        reconnect,
        disconnect
    };
}
