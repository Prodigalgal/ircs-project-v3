import { useState, useRef, useCallback, useEffect } from 'react';
import { AuthenticatedEventStream } from '../../../../utils/AuthenticatedEventStream';

export function useTaskStream() {
    const [status, setStatus] = useState('IDLE');
    const [logs, setLogs] = useState([]);
    const [progress, setProgress] = useState({ current: 0, total: 0, percent: 0 });
    const eventSourceRef = useRef(null);
    const reconnectTimerRef = useRef(null);

    const addLog = (msg, type = 'info') => {
        setLogs(prev => [...prev, { time: Date.now(), msg, type }]);
    };

    const disconnect = useCallback(() => {
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }
        if (reconnectTimerRef.current) {
            clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
    }, []);

    const startStream = useCallback((url) => {
        disconnect();

        setStatus('RUNNING');

        const es = new AuthenticatedEventStream(url);
        eventSourceRef.current = es;

        es.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);

                if (data.type === 'LOG') {
                    addLog(data.payload, 'info');
                } else if (data.type === 'WARN') {
                    addLog(data.payload, 'warning');
                } else if (data.type === 'ERROR') {
                    addLog(data.payload, 'error');
                } else if (data.type === 'PROGRESS') {
                    const { current, total } = data.payload;
                    const percent = total > 0 ? Math.round((current / total) * 100) : 0;
                    setProgress({ current, total, percent });
                } else if (data.type === 'DONE') {
                    setStatus('COMPLETED');
                    addLog('任务执行完毕。', 'success');
                    es.close();
                }
            } catch (e) {
                console.error("Stream parse error", e);
            }
        };

        es.onerror = () => {
            if (es.readyState === AuthenticatedEventStream.CONNECTING) {
                addLog('连接中断，正在尝试自动恢复...', 'warning');
                return;
            }

            setStatus(previous => previous === 'RUNNING' ? 'ERROR' : previous);
            addLog('会话超时或任务已过期。', 'error');
            es.close();
        };

    }, [disconnect]);

    useEffect(() => disconnect, [disconnect]);

    const stopStream = useCallback(() => {
        disconnect();
        setStatus('IDLE');
        addLog('用户已断开监控。', 'warning');
    }, [disconnect]);

    const clearLogs = useCallback(() => {
        setLogs([]);
    }, []);

    return {
        status,
        logs,
        progress,
        startStream,
        stopStream,
        disconnect,
        clearLogs
    };
}
