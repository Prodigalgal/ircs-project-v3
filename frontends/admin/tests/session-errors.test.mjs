import test from 'node:test';
import assert from 'node:assert/strict';
import { rtkQueryErrorLogger } from '../src/app/middleware/errorMiddleware.js';

const TOKEN_KEY = 'ircs_token';
const CURRENT_TOKEN = 'current-session';

function setupMiddleware(t) {
    const storage = new Map([[TOKEN_KEY, CURRENT_TOKEN]]);
    const notifications = [];
    const redirects = [];
    const next = t.mock.fn(action => action);
    const browserGlobals = {
        localStorage: { getItem: key => storage.get(key) ?? null, removeItem: key => storage.delete(key) },
        window: { location: { replace: path => redirects.push(path) } },
    };
    for (const [key, value] of Object.entries(browserGlobals)) {
        const previous = Object.getOwnPropertyDescriptor(globalThis, key);
        Object.defineProperty(globalThis, key, { value, configurable: true });
        t.after(() => {
            if (previous) Object.defineProperty(globalThis, key, previous);
            else delete globalThis[key];
        });
    }
    t.mock.method(console, 'warn', () => {});
    const middleware = rtkQueryErrorLogger({ dispatch: action => notifications.push(action.payload) })(next);
    return { middleware, storage, notifications, redirects, next };
}

function rejectedRequest(status, code, token, path = '/ops-alert/incidents') {
    const headers = new Headers();
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return {
        type: 'api/executeQuery/rejected',
        payload: { status, data: { code, message: '请求被拒绝' } },
        meta: {
            requestId: 'test-request',
            requestStatus: 'rejected',
            rejectedWithValue: true,
            baseQueryMeta: { request: new Request(`https://admin.example.test/api/v1${path}`, { headers }) },
        },
    };
}

for (const code of ['ops-alert.internal.access.denied', 'gateway.identity.invalid', 'auth.token.missing']) {
    test(`keeps the current session when a request returns 401 ${code}`, (t) => {
        const { middleware, storage, notifications, redirects, next } = setupMiddleware(t);
        const rejection = rejectedRequest(401, code, CURRENT_TOKEN);

        assert.equal(middleware(rejection), rejection);
        assert.equal(next.mock.callCount(), 1);
        assert.equal(storage.get(TOKEN_KEY), CURRENT_TOKEN);
        assert.deepEqual(redirects, []);
        assert.deepEqual(notifications, [{ message: '请求被拒绝', severity: 'error' }]);
    });
}

for (const code of ['auth.token.expired', 'auth.token.invalid', 'auth.token.stale']) {
    test(`clears the current session once when its token is rejected with ${code}`, (t) => {
        const { middleware, storage, notifications, redirects, next } = setupMiddleware(t);
        const rejection = rejectedRequest(401, code, CURRENT_TOKEN);

        assert.equal(middleware(rejection), rejection);
        middleware(rejection);

        assert.equal(next.mock.callCount(), 2);
        assert.equal(storage.has(TOKEN_KEY), false);
        assert.deepEqual(redirects, ['/login']);
        assert.deepEqual(notifications, [{ message: '会话已失效，请重新登录', severity: 'warning' }]);
    });
}

test('a delayed rejection of an earlier token leaves the new session active', (t) => {
    const { middleware, storage, notifications, redirects } = setupMiddleware(t);

    middleware(rejectedRequest(401, 'auth.token.expired', 'previous-session'));

    assert.equal(storage.get(TOKEN_KEY), CURRENT_TOKEN);
    assert.deepEqual(redirects, []);
    assert.deepEqual(notifications, []);
});

test('a failed sign-in forwards the error without touching the session', (t) => {
    const { middleware, storage, notifications, redirects, next } = setupMiddleware(t);
    const rejection = rejectedRequest(401, 'auth.failed', null, '/auth/login');

    assert.equal(middleware(rejection), rejection);
    assert.equal(next.mock.callCount(), 1);
    assert.equal(storage.get(TOKEN_KEY), CURRENT_TOKEN);
    assert.deepEqual(redirects, []);
    assert.deepEqual(notifications, [{ message: '请求被拒绝', severity: 'error' }]);
});
