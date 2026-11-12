const CONNECTING = 0;
const OPEN = 1;
const CLOSED = 2;

class StreamConnectionError extends Error {
    constructor(message, retryable, status = 0) {
        super(message);
        this.retryable = retryable;
        this.status = status;
    }
}

// Parse lines across arbitrary UTF-8/chunk/CRLF boundaries; only a blank line dispatches an event.
class EventStreamParser {
    constructor(onEvent, onId, onRetry) {
        this.onEvent = onEvent;
        this.onId = onId;
        this.onRetry = onRetry;
        this.buffer = '';
        this.lines = [];
        this.eventType = '';
        this.lastEventId = null;
    }

    push(chunk) {
        this.buffer += chunk;
        let delimiter;
        while ((delimiter = /\r\n|\r|\n/.exec(this.buffer))) {
            if (delimiter[0] === '\r' && delimiter.index === this.buffer.length - 1) return;
            const line = this.buffer.slice(0, delimiter.index);
            this.buffer = this.buffer.slice(delimiter.index + delimiter[0].length);
            this.consumeLine(line);
        }
    }

    consumeLine(line) {
        if (!line) {
            if (this.lastEventId !== null) this.onId(this.lastEventId);
            if (this.lines.length) this.onEvent(this.eventType || 'message', this.lines.join('\n'));
            this.lines = [];
            this.eventType = '';
            return;
        }
        if (line.startsWith(':')) return;
        const separator = line.indexOf(':');
        const field = separator < 0 ? line : line.slice(0, separator);
        const rawValue = separator < 0 ? '' : line.slice(separator + 1);
        const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;
        if (field === 'data') this.lines.push(value);
        else if (field === 'event') this.eventType = value;
        else if (field === 'id' && !value.includes('\0')) this.lastEventId = value;
        else if (field === 'retry' && /^\d+$/.test(value)) this.onRetry(Number(value));
    }
}

export class AuthenticatedEventStream extends EventTarget {
    static CONNECTING = CONNECTING;
    static OPEN = OPEN;
    static CLOSED = CLOSED;

    readyState = CONNECTING;
    onmessage = null;
    onopen = null;
    onerror = null;

    #url;
    #fetch;
    #getToken;
    #reconnect;
    #controller;
    #timer;
    #retryMs = 1000;
    #lastEventId = '';

    constructor(url, {
        reconnect = true,
        fetchImpl = globalThis.fetch,
        getToken = () => globalThis.localStorage.getItem('ircs_token'),
    } = {}) {
        super();
        const origin = globalThis.location?.origin || 'http://localhost';
        const target = new URL(url, origin);
        if (target.origin !== origin || target.username || target.password || target.searchParams.has('token')) {
            throw new Error('事件流必须使用同源地址，登录凭证只允许通过 Authorization 发送');
        }
        this.#url = target.pathname + target.search;
        this.#fetch = fetchImpl;
        this.#getToken = getToken;
        this.#reconnect = reconnect;
        // Allow callers to attach handlers before a synchronous validation/fetch failure.
        queueMicrotask(() => this.#connect());
    }

    close() {
        this.readyState = CLOSED;
        clearTimeout(this.#timer);
        this.#controller?.abort();
    }

    #emit(type, event) {
        this.dispatchEvent(event);
        this[`on${type}`]?.(event);
    }

    async #connect() {
        if (this.readyState === CLOSED) return;
        this.#controller = new AbortController();
        let reader;
        try {
            // Re-read credentials on every reconnect so logout/refresh cannot reuse an old token.
            const token = this.#getToken();
            if (!token) throw new StreamConnectionError('缺少登录凭证', false, 401);
            const headers = { Accept: 'text/event-stream', Authorization: `Bearer ${token}` };
            if (this.#lastEventId) headers['Last-Event-ID'] = this.#lastEventId;
            const response = await this.#fetch(this.#url, {
                headers,
                signal: this.#controller.signal,
                credentials: 'same-origin',
                cache: 'no-store',
                redirect: 'error',
            });
            if (this.readyState === CLOSED) {
                await response.body?.cancel();
                return;
            }
            if (!response.ok || response.status === 204) {
                await response.body?.cancel();
                const retryable = response.status >= 500 || response.status === 408 || response.status === 429;
                throw new StreamConnectionError(`事件流请求失败 (${response.status})`, retryable, response.status);
            }
            if (!response.headers.get('content-type')?.toLowerCase().startsWith('text/event-stream') || !response.body) {
                await response.body?.cancel();
                throw new StreamConnectionError('事件流响应格式无效', false);
            }
            this.readyState = OPEN;
            this.#emit('open', new Event('open'));
            const parser = new EventStreamParser(
                (type, data) => {
                    if (this.readyState !== CLOSED) {
                        this.#emit(type, new MessageEvent(type, { data, lastEventId: this.#lastEventId }));
                    }
                },
                id => { this.#lastEventId = id; },
                delay => { this.#retryMs = Math.min(30000, Math.max(250, delay)); },
            );
            reader = response.body.getReader();
            const decoder = new TextDecoder();
            while (this.readyState !== CLOSED) {
                const { value, done } = await reader.read();
                if (done) break;
                parser.push(decoder.decode(value, { stream: true }));
            }
            if (this.readyState !== CLOSED) throw new StreamConnectionError('事件流已断开', true);
        } catch (error) {
            if (this.readyState === CLOSED) return;
            const failure = error instanceof StreamConnectionError
                ? error : new StreamConnectionError('事件流连接中断', true);
            this.readyState = this.#reconnect && failure.retryable ? CONNECTING : CLOSED;
            this.#emit('error', Object.assign(new Event('error'), { error: failure }));
            if (this.readyState === CONNECTING) {
                this.#timer = setTimeout(() => this.#connect(), this.#retryMs);
            }
        } finally {
            if (reader) {
                try { await reader.cancel(); } catch { /* The abort may already have closed the body. */ }
                reader.releaseLock();
            }
        }
    }
}
