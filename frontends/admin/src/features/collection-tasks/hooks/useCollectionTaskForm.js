import { useState, useEffect } from 'react';

const DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const MIN_SAFE_DELAY = 3000;

const LOCAL_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai';

const emptyTask = {
    name: '',
    enabled: true,
    cronExpression: '0 0 3 * * ?',
    timeZone: LOCAL_TIMEZONE,
    taskType: 'BY_PAGE',
    startPage: 1,
    endPage: 0,
    filterType: '',
    filterHours: '',
    filterKeywords: '',
    requestDelayType: 'RANDOM',
    fixedDelayMs: MIN_SAFE_DELAY,
    randomDelayMinMs: MIN_SAFE_DELAY,
    randomDelayMaxMs: MIN_SAFE_DELAY * 2,
    timeoutMs: 15000,
    maxRetries: 3,
    userAgent: DEFAULT_USER_AGENT,
    enableRandomUa: true,
    headers: '{}',
    dataSourceId: '',
    useCustomProxy: false,
    proxyType: 'HTTP',
    proxyHost: '',
    proxyPort: '',
    proxyUsername: '',
    proxyPassword: ''
};

export function useCollectionTaskForm(initialTask, onSubmit) {
    const [formData, setFormData] = useState(emptyTask);
    const [errors, setErrors] = useState({});
    const [isAutoPage, setIsAutoPage] = useState(true);

    useEffect(() => {
        if (initialTask) {
            setFormData({ ...emptyTask, ...initialTask });
            const endPageVal = initialTask.endPage;
            setIsAutoPage(!endPageVal || endPageVal <= 0);
        } else {
            setFormData(emptyTask);
            setIsAutoPage(true);
        }
        setErrors({});
    }, [initialTask]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: null }));
    };

    const handleNumberChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value === '' ? '' : parseInt(value, 10) || 0 }));
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: null }));
    };

    const handleHeadersChange = (newJson) => {
        setFormData(prev => ({ ...prev, headers: newJson }));
    };

    const handleResetUserAgent = () => {
        setFormData(prev => ({ ...prev, userAgent: DEFAULT_USER_AGENT }));
    };

    const handleProxyTypeChange = (e) => {
        setFormData(prev => ({ ...prev, useCustomProxy: e.target.value === 'custom' }));
    }

    const handleAutoPageChange = (checked) => {
        setIsAutoPage(checked);
        if (checked) {
            setFormData(prev => ({ ...prev, endPage: 0 }));
            if (errors.endPage) setErrors(prev => ({ ...prev, endPage: null }));
        } else {
            if (!formData.endPage || formData.endPage <= 0) {
                setFormData(prev => ({ ...prev, endPage: (prev.startPage || 1) + 9 }));
            }
        }
    };

    const validate = () => {
        const tempErrors = {};
        if (!formData.name.trim()) tempErrors.name = "任务名称不能为空";
        if (!formData.dataSourceId) tempErrors.dataSourceId = "请选择一个数据源";
        if (!formData.timeZone) tempErrors.timeZone = "请选择执行时区";

        if (formData.startPage < 1) tempErrors.startPage = "起始页码必须大于0";

        if (!isAutoPage && formData.endPage > 0) {
            if (Number(formData.endPage) < Number(formData.startPage)) {
                tempErrors.endPage = "结束页码不能小于起始页码";
            }
        }

        if (formData.requestDelayType === 'RANDOM') {
            if (Number(formData.randomDelayMinMs) < MIN_SAFE_DELAY) {
                tempErrors.randomDelayMinMs = `为了安全，建议最小延时不低于 ${MIN_SAFE_DELAY}ms`;
            }
            if (Number(formData.randomDelayMinMs) > Number(formData.randomDelayMaxMs)) {
                tempErrors.randomDelayMinMs = "最小值不能大于最大值";
            }
        } else {
            if (Number(formData.fixedDelayMs) < MIN_SAFE_DELAY) {
                tempErrors.fixedDelayMs = `为了安全，建议固定延时不低于 ${MIN_SAFE_DELAY}ms`;
            }
        }

        if (formData.useCustomProxy) {
            if (!formData.proxyHost) tempErrors.proxyHost = "代理主机地址不能为空";
            if (!formData.proxyPort) tempErrors.proxyPort = "代理端口不能为空";
        }

        setErrors(tempErrors);
        return Object.keys(tempErrors).length === 0;
    };

    const handleSubmit = () => {
        if (!validate()) return;

        const submissionData = { ...formData };

        if (isAutoPage) {
            submissionData.endPage = 0;
        }

        ['startPage', 'endPage', 'fixedDelayMs', 'randomDelayMinMs', 'randomDelayMaxMs', 'timeoutMs', 'maxRetries', 'proxyPort', 'filterHours'].forEach(key => {
            const val = submissionData[key];
            if (key === 'endPage' && val === 0) {
                submissionData[key] = 0;
            } else {
                submissionData[key] = (val === '' || val === null) ? null : parseInt(val, 10);
            }
        });
        onSubmit(submissionData);
    };

    return {
        formData,
        setFormData,
        errors,
        handleChange,
        handleNumberChange,
        handleHeadersChange,
        handleResetUserAgent,
        handleProxyTypeChange,
        handleSubmit,
        isAutoPage,
        handleAutoPageChange
    };
}
