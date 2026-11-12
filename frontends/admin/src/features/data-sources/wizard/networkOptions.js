function trimToNull(value) {
    if (typeof value !== 'string') {
        return value ?? null;
    }
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
}

function timeoutToNumber(value) {
    if (value === undefined || value === null || value === '') {
        return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
}

export function toSourceNetworkPayload(formData) {
    return {
        transportMode: formData.transportMode || 'AUTO',
        httpProtocol: formData.httpProtocol || 'AUTO',
        ipVersionPolicy: formData.ipVersionPolicy || 'AUTO',
        dnsResolverType: formData.dnsResolverType || 'SYSTEM',
        dnsResolverEndpoint: trimToNull(formData.dnsResolverEndpoint),
        connectTimeoutMs: timeoutToNumber(formData.connectTimeoutMs),
        readTimeoutMs: timeoutToNumber(formData.readTimeoutMs),
        userAgent: trimToNull(formData.userAgent),
    };
}
