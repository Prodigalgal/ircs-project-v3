import assert from 'node:assert/strict';
import test from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { AuthenticatedEventStream } from '../src/utils/AuthenticatedEventStream.js';

function streamResponse(chunks) {
    return new Response(new ReadableStream({
        start(controller) {
            chunks.forEach(chunk => controller.enqueue(chunk));
            controller.close();
        },
    }), { headers: { 'Content-Type': 'text/event-stream; charset=utf-8' } });
}

function textResponse(text) {
    return streamResponse([new TextEncoder().encode(text)]);
}

function waitFor(source, eventType) {
    return new Promise(resolve => source.addEventListener(eventType, resolve, { once: true }));
}

test('sends Bearer only in headers and preserves non-secret query arguments', { timeout: 2000 }, async t => {
    let request;
    const source = new AuthenticatedEventStream('/api/v1/dashboard/stream/task-runtime?limit=50', {
        getToken: () => 'jwt-secret',
        reconnect: false,
        fetchImpl: async (url, options) => {
            request = { url, ...options };
            return textResponse('data: {"ok":true}\n\n');
        },
    });
    t.after(() => source.close());
    const event = await waitFor(source, 'message');
    assert.equal(event.data, '{"ok":true}');
    assert.equal(request.url, '/api/v1/dashboard/stream/task-runtime?limit=50');
    assert.equal(request.headers.Authorization, 'Bearer jwt-secret');
    assert.equal(request.redirect, 'error');
    assert.equal(request.cache, 'no-store');
});

test('parses split UTF-8, split CRLF, comments, named and multiline events', { timeout: 2000 }, async t => {
    const encoded = new TextEncoder().encode(': heartbeat\r\nevent: metrics\r\nid: 42\r\ndata: 中文\r\ndata: 第二行\r\n\r\n');
    const source = new AuthenticatedEventStream('/stream', {
        getToken: () => 'jwt',
        reconnect: false,
        fetchImpl: async () => streamResponse(Array.from(encoded, byte => new Uint8Array([byte]))),
    });
    t.after(() => source.close());
    const event = await waitFor(source, 'metrics');
    assert.equal(event.data, '中文\n第二行');
    assert.equal(event.lastEventId, '42');
});

test('does not dispatch incomplete data on EOF', { timeout: 2000 }, async t => {
    const source = new AuthenticatedEventStream('/stream', {
        getToken: () => 'jwt', reconnect: false,
        fetchImpl: async () => textResponse('data: incomplete\n'),
    });
    t.after(() => source.close());
    let messages = 0;
    source.onmessage = () => { messages++; };
    const event = await waitFor(source, 'error');
    assert.equal(messages, 0);
    assert.equal(event.error.retryable, true);
});

test('reconnects with fresh token and Last-Event-ID', { timeout: 3000 }, async t => {
    let token = 'before-refresh';
    const requests = [];
    const source = new AuthenticatedEventStream('/stream', {
        getToken: () => token,
        fetchImpl: async (url, options) => {
            requests.push({ url, headers: options.headers });
            return requests.length === 1
                ? textResponse('retry: 250\nid: first-event\ndata: first\n\n')
                : textResponse('data: second\n\n');
        },
    });
    t.after(() => source.close());
    await new Promise(resolve => {
        source.onmessage = event => {
            if (event.data === 'first') token = 'after-refresh';
            if (event.data === 'second') {
                source.close();
                resolve();
            }
        };
    });
    assert.equal(requests.length, 2);
    assert.equal(requests[0].headers.Authorization, 'Bearer before-refresh');
    assert.equal(requests[1].headers.Authorization, 'Bearer after-refresh');
    assert.equal(requests[1].headers['Last-Event-ID'], 'first-event');
});

test('401 is terminal and missing credentials never reach the network', { timeout: 2000 }, async t => {
    let calls = 0;
    const unauthorized = new AuthenticatedEventStream('/stream', {
        getToken: () => 'expired',
        fetchImpl: async () => { calls++; return new Response('', { status: 401 }); },
    });
    t.after(() => unauthorized.close());
    const event = await waitFor(unauthorized, 'error');
    assert.equal(event.error.status, 401);
    assert.equal(event.error.retryable, false);
    assert.equal(unauthorized.readyState, AuthenticatedEventStream.CLOSED);
    const missing = new AuthenticatedEventStream('/stream', {
        getToken: () => null,
        fetchImpl: async () => { calls++; throw new Error('must not fetch'); },
    });
    t.after(() => missing.close());
    assert.equal((await waitFor(missing, 'error')).error.status, 401);
    assert.equal(calls, 1);
});

test('close aborts a pending read without reconnects or late events', { timeout: 2000 }, async t => {
    let aborted = false;
    let errors = 0;
    const source = new AuthenticatedEventStream('/stream', {
        getToken: () => 'jwt',
        fetchImpl: async (url, { signal }) => new Response(new ReadableStream({
            start(controller) {
                signal.addEventListener('abort', () => {
                    aborted = true;
                    controller.error(new DOMException('Aborted', 'AbortError'));
                });
            },
        }), { headers: { 'Content-Type': 'text/event-stream' } }),
    });
    t.after(() => source.close());
    source.onerror = () => { errors++; };
    await waitFor(source, 'open');
    source.close();
    await delay(10);
    assert.equal(aborted, true);
    assert.equal(errors, 0);
    assert.equal(source.readyState, AuthenticatedEventStream.CLOSED);
});

test('rejects foreign origins and URL credentials before fetching', () => {
    assert.throws(() => new AuthenticatedEventStream('https://example.invalid/stream'));
    assert.throws(() => new AuthenticatedEventStream('/stream?token=secret'));
    assert.throws(() => new AuthenticatedEventStream('http://user:pass@localhost/stream'));
});

test('immediate close does not open a request', async () => {
    let calls = 0;
    const source = new AuthenticatedEventStream('/stream', {
        getToken: () => 'jwt',
        fetchImpl: async () => { calls++; return textResponse(''); },
    });
    source.close();
    await Promise.resolve();
    assert.equal(calls, 0);
});
