import { useState, useRef, useCallback, useEffect } from 'react';
import { useInitManualSessionMutation } from '../scraperApi';
import { AuthenticatedEventStream } from '../../../utils/AuthenticatedEventStream';

const DEFAULT_FORM_DATA = {
    keyword: '',
    startPage: 1,
    endPage: 1,
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    enableRandomUa: false,
    useCustomProxy: false,
    proxyType: 'HTTP',
    proxyHost: '',
    proxyPort: '',
    headers: '{}'
};

export function useManualScraper() {
    const [step, setStep] = useState(1); // 1: Config, 2: Running
    const [formData, setFormData] = useState(DEFAULT_FORM_DATA);
    const [logs, setLogs] = useState([]);
    const [cards, setCards] = useState([]);
    const [isFinished, setIsFinished] = useState(false);

    const eventSourceRef = useRef(null);

    const [initSession, { isLoading: isInitializing }] = useInitManualSessionMutation();

    const connectSSE = useCallback((sessionId) => {
        const url = `/api/v1/scraper/manual/stream/${sessionId}`;

        // 关闭旧连接（如果有）
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
        }

        const eventSource = new AuthenticatedEventStream(url, { reconnect: false });
        eventSourceRef.current = eventSource;

        eventSource.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'LOG') {
                    setLogs(prev => [...prev, { time: data.timestamp, msg: data.payload, type: 'info' }]);
                } else if (data.type === 'CARD') {
                    setCards(prev => [data.payload, ...prev]);
                } else if (data.type === 'DONE') {
                    setIsFinished(true);
                    eventSource.close();
                } else if (data.type === 'ERROR') {
                    setLogs(prev => [...prev, { time: data.timestamp, msg: data.payload, type: 'error' }]);
                }
            } catch (e) {
                console.error("Parse error", e);
            }
        };

        eventSource.onerror = () => {
            setLogs(prev => [...prev, { time: Date.now(), msg: "连接中断或鉴权失败。", type: 'error' }]);
            setIsFinished(true);
            eventSource.close();
        };
    }, []);

    const startScraping = async () => {
        if (!formData.keyword.trim()) throw new Error("请输入关键词");
        if (Number(formData.startPage) < 1 || Number(formData.endPage) < Number(formData.startPage)) {
            throw new Error("页码范围无效");
        }

        // 1. 发起请求
        const result = await initSession(formData).unwrap();
        const sessionId = result.sessionId;

        // 2. 状态跳转 (必须在 connectSSE 之前)
        setStep(2);
        setLogs([]);
        setCards([]);
        setIsFinished(false);

        // 3. 建立连接
        connectSSE(sessionId);
    };

    const stopScraping = useCallback(() => {
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }
    }, []);

    useEffect(() => stopScraping, [stopScraping]);

    const reset = useCallback(() => {
        stopScraping();
        setStep(1);
        setIsFinished(false);
        setLogs([]);
        setCards([]);
    }, [stopScraping]);

    const clearLogs = useCallback(() => setLogs([]), []);

    return {
        step,
        formData,
        setFormData,
        logs,
        cards,
        isFinished,
        isInitializing,
        startScraping,
        stopScraping,
        reset,
        clearLogs
    };
}
