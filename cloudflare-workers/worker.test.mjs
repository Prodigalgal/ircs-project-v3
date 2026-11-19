import assert from "node:assert/strict";
import { test } from "node:test";
import worker from "./worker.js";

const SITE = "https://huawai.mnnu.eu.org";
const HTML = "<!doctype html><p>Public HuaWai page</p>";

function edgeFixture(t, respond) {
  const cached = new Map();
  const cache = {
    match: t.mock.fn(async (key) => cached.get(key.url)?.clone()),
    put: t.mock.fn(async (key, response) => cached.set(key.url, response.clone())),
  };
  const originalCaches = Object.getOwnPropertyDescriptor(globalThis, "caches");
  Object.defineProperty(globalThis, "caches", {
    configurable: true,
    value: { default: cache },
  });
  t.after(() => {
    if (originalCaches) {
      Object.defineProperty(globalThis, "caches", originalCaches);
    } else {
      delete globalThis.caches;
    }
  });
  const origin = t.mock.method(globalThis, "fetch", async (request, init) => respond(request, init));

  async function send(path, headers = {}) {
    const waits = [];
    const response = await worker.fetch(new Request(new URL(path, SITE), { headers }), {}, {
      waitUntil: (pending) => waits.push(pending),
    });
    await Promise.all(waits);
    return response;
  }

  return { cached, cache, origin, send };
}

function htmlResponse(body = HTML) {
  return new Response(body, { headers: { "Content-Type": "text/html; charset=utf-8" } });
}

test("Next.js navigation and prefetch bypass an already cached homepage", async (t) => {
  const edge = edgeFixture(t, (_request, init) => init?.cache === "no-store"
    ? new Response("Flight payload", { headers: { "Content-Type": "text/x-component" } })
    : htmlResponse());

  assert.equal((await edge.send("/")).headers.get("X-Edge-Cache"), "MISS");
  assert.equal((await edge.send("/")).headers.get("X-Edge-Cache"), "HIT");

  for (const [path, headers] of [
    ["/?_rsc=hash", { RSC: "1" }],
    ["/", { rsc: "1" }],
    ["/", { "Next-Router-Prefetch": "1" }],
    ["/", { "Next-Router-Segment-Prefetch": "/_tree" }],
    ["/", { "Next-Router-State-Tree": "router-state" }],
    ["/", { "Next-Url": "/explore" }],
    ["/?_rsc=", {}],
    ["/topic/sample.segments/_tree.segment.rsc", {}],
  ]) {
    const response = await edge.send(path, headers);
    assert.equal(response.headers.get("X-Edge-Cache"), "BYPASS");
    assert.equal(response.headers.get("X-Edge-Cache-Reason"), "next-router");
    assert.equal(response.headers.get("Content-Type"), "text/x-component");
    assert.match(response.headers.get("Cache-Control"), /\bno-store\b/);
    assert.equal(await response.text(), "Flight payload");
    const [forwarded, init] = edge.origin.mock.calls.at(-1).arguments;
    assert.equal(forwarded.url, new URL(path, SITE).href);
    assert.deepEqual([...forwarded.headers], [...new Headers(headers)]);
    assert.equal(init.cache, "no-store");
  }

  assert.equal(edge.cache.match.mock.callCount(), 2);
  assert.equal(edge.cache.put.mock.callCount(), 1);
  const homepage = await edge.send("/");
  assert.equal(homepage.headers.get("X-Edge-Cache"), "HIT");
  assert.equal(await homepage.text(), HTML);
});

test("the HTML key version skips existing entries and still normalizes tracking parameters", async (t) => {
  const edge = edgeFixture(t, () => htmlResponse());
  edge.cached.set(`${SITE}/`, htmlResponse("old page"));

  const response = await edge.send("/?utm_source=probe");
  assert.equal(response.headers.get("X-Edge-Cache"), "MISS");
  assert.equal(response.headers.get("X-Edge-Cache-Key"), "/?__edge_cache_version=html-v2");
  assert.equal(await response.text(), HTML);
  assert.equal((await edge.send("/")).headers.get("X-Edge-Cache"), "HIT");
  assert.equal(edge.origin.mock.callCount(), 1);
});

test("anonymous API entries remain reusable under the existing cache key", async (t) => {
  const edge = edgeFixture(t, () => Response.json({ scoped: "anonymous" }));

  const first = await edge.send("/api/portal/home");
  const second = await edge.send("/api/portal/home?utm_source=probe");
  assert.equal(first.headers.get("X-Edge-Cache"), "MISS");
  assert.equal(second.headers.get("X-Edge-Cache"), "HIT");
  assert.equal(second.headers.get("X-Edge-Cache-Key"), "/api/portal/home");
  assert.deepEqual(await second.json(), { scoped: "anonymous" });
  assert.equal(edge.origin.mock.callCount(), 1);
});

test("login cookie and authorization keep their bypass decisions after an anonymous cache fill", async (t) => {
  const edge = edgeFixture(t, (_request, init) => htmlResponse(init?.cache === "no-store" ? "private page" : HTML));

  assert.equal((await edge.send("/")).headers.get("X-Edge-Cache"), "MISS");
  for (const [headers, reason] of [
    [{ Cookie: "huawai_token=test-session" }, "login-cookie"],
    [{ Authorization: "Bearer test-session" }, "authorization"],
  ]) {
    const response = await edge.send("/", headers);
    assert.equal(response.headers.get("X-Edge-Cache"), "BYPASS");
    assert.equal(response.headers.get("X-Edge-Cache-Reason"), reason);
    assert.equal(response.headers.get("Cache-Control"), "private, no-store, max-age=0, must-revalidate");
    assert.equal(await response.text(), "private page");
  }
  assert.equal(edge.cache.match.mock.callCount(), 1);
  assert.equal(edge.cache.put.mock.callCount(), 1);
});
