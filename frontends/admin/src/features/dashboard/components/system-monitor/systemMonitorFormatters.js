export function formatBytes(bytes) {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

export function formatNumber(num) {
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'k';
    return num;
}

export function errorMessage(error) {
    return error?.data?.message || error?.error || error?.message || null;
}

export function stableRate(metric) {
    return metric?.stableTpm || 0;
}

export function instantRate(metric) {
    return metric?.instantTpm || 0;
}

export function rateSubtitle(metric) {
    const seconds = metric?.instantWindowSeconds || 60;
    return `瞬时 ${formatNumber(instantRate(metric))}/分 · ${formatNumber(metric?.instantCount || 0)}/${seconds}s`;
}

export function formatDateTime(value) {
    return value ? new Date(value).toLocaleString() : '-';
}
