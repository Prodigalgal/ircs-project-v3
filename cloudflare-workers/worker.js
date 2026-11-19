const BYPASS_COOKIES = ["huawai_token", "next-auth.session-token"];
const NEXT_ROUTER_HEADERS = [
  "RSC",
  "Next-Router-State-Tree",
  "Next-Router-Prefetch",
  "Next-Router-Segment-Prefetch",
  "Next-Url",
];
const IGNORED_SEARCH_PARAMS = [
  "utm_source",
  "utm_medium",
  "utm_campaign",
  "utm_term",
  "utm_content",
  "fbclid",
  "gclid",
  "msclkid",
  "igshid",
  "mc_cid",
  "mc_eid",
  "_ga",
  "_gl",
  "miss",
  "edgeprobe",
  "perf",
  "perf2",
  "_",
];
const EXPLORE_CACHE_PARAMS = ["keyword", "type", "category", "genre", "area", "year", "language", "sort", "page", "size"];
const SUGGEST_CACHE_PARAMS = ["keyword"];
const FEEDBACK_WALL_CACHE_PARAMS = ["page", "size"];
const PORTAL_API_PREFIXES = ["/api/portal"];
const REMOVED_API_PREFIXES = ["/api/backend"];
const PUBLIC_HTML_PATHS = new Set(["/search", "/login", "/signup", "/forgot-password"]);
const INFLIGHT_CACHE_MISSES = new Map();
const STATIC_ASSET_PREFIXES = ["/_next/static/", "/assets/", "/images/"];
const STATIC_ASSET_EXTENSIONS = [
  ".css",
  ".js",
  ".mjs",
  ".map",
  ".json",
  ".svg",
  ".png",
  ".jpg",
  ".jpeg",
  ".webp",
  ".avif",
  ".ico",
  ".woff",
  ".woff2",
  ".ttf",
  ".otf",
];

const CACHE_POLICIES = {
  html: {
    name: "anonymous-html",
    keyVersion: "html-v2",
    edgeTtl: 600,
    browserTtl: 60,
    staleWhileRevalidate: 600,
    staleIfError: 1800,
    contentTypeIncludes: "text/html",
  },
  homeApi: {
    name: "portal-home-api",
    edgeTtl: 180,
    browserTtl: 0,
    staleWhileRevalidate: 300,
    staleIfError: 1800,
    contentTypeIncludes: "application/json",
  },
  metadataApi: {
    name: "portal-metadata-api",
    edgeTtl: 3600,
    browserTtl: 0,
    staleWhileRevalidate: 3600,
    staleIfError: 86400,
    contentTypeIncludes: "application/json",
  },
  movieDetailApi: {
    name: "portal-movie-detail-api",
    edgeTtl: 900,
    browserTtl: 0,
    staleWhileRevalidate: 900,
    staleIfError: 3600,
    contentTypeIncludes: "application/json",
  },
  exploreApi: {
    name: "portal-explore-api",
    edgeTtl: 180,
    browserTtl: 0,
    staleWhileRevalidate: 300,
    staleIfError: 1800,
    contentTypeIncludes: "application/json",
  },
  suggestApi: {
    name: "portal-search-suggest-api",
    edgeTtl: 300,
    browserTtl: 30,
    staleWhileRevalidate: 300,
    staleIfError: 1800,
    contentTypeIncludes: "application/json",
  },
  feedbackWallApi: {
    name: "portal-feedback-wall-api",
    edgeTtl: 60,
    browserTtl: 0,
    staleWhileRevalidate: 300,
    staleIfError: 1800,
    contentTypeIncludes: "application/json",
  },
  sitemap: {
    name: "portal-sitemap-feed",
    keyVersion: "v2",
    edgeTtl: 300,
    browserTtl: 0,
    staleWhileRevalidate: 300,
    staleIfError: 86400,
    contentTypeIncludes: "xml",
  },
  staticAsset: {
    name: "static-asset",
    edgeTtl: 86400,
    browserTtl: 31536000,
    staleWhileRevalidate: 86400,
    staleIfError: 604800,
    contentTypeIncludes: "",
  },
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (isRemovedApiPath(url)) {
      return legacyApiRemovedResponse();
    }

    const bypassReason = getRequestBypassReason(request, url);

    if (bypassReason) {
      return fetchBypass(request, bypassReason);
    }

    const policy = resolveCachePolicy(url);

    if (!policy) {
      return fetch(request);
    }

    const cacheKeyUrl = normalizeCacheKeyUrl(url, policy);
    const cacheKey = new Request(cacheKeyUrl.toString(), { method: "GET" });
    const cache = caches.default;
    const cachedResponse = await cache.match(cacheKey);

    if (cachedResponse) {
      return withHitHeaders(cachedResponse, policy, cacheKeyUrl, request.method);
    }

    const response = await fetchCacheMiss(request, policy, cache, cacheKey, cacheKeyUrl, ctx);
    return toClientResponse(response, request.method);
  },
};

function isRemovedApiPath(url) {
  return REMOVED_API_PREFIXES.some((prefix) => {
    return url.pathname === prefix || url.pathname.startsWith(`${prefix}/`);
  });
}

function legacyApiRemovedResponse() {
  return new Response(
    JSON.stringify({
      code: "legacy.api.removed",
      message: "Legacy portal API prefix has been removed.",
    }),
    {
      status: 410,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store",
        "X-Edge-Cache": "BYPASS",
        "X-Edge-Cache-Reason": "legacy-api-removed",
      },
    },
  );
}

function getRequestBypassReason(request, url) {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return "method";
  }

  if (request.headers.has("Authorization")) {
    return "authorization";
  }

  const cookieHeader = request.headers.get("Cookie") || "";
  const hasLoginCookie = BYPASS_COOKIES.some((name) => {
    return new RegExp(`(^|;\\s*)${name}=`).test(cookieHeader);
  });

  if (hasLoginCookie) {
    return "login-cookie";
  }

  // App Router navigation and prefetch responses vary independently from the shared HTML.
  if (url.searchParams.has("_rsc") || url.pathname.endsWith(".rsc")
    || NEXT_ROUTER_HEADERS.some((name) => request.headers.has(name))) {
    return "next-router";
  }

  return "";
}

function resolveCachePolicy(url) {
  if (isStaticAssetCacheable(url)) {
    return CACHE_POLICIES.staticAsset;
  }

  if (isSitemapCacheable(url)) {
    return CACHE_POLICIES.sitemap;
  }

  if (isAnonymousHtmlCacheable(url)) {
    return CACHE_POLICIES.html;
  }

  if (isPortalApiPath(url, "/home")) {
    return CACHE_POLICIES.homeApi;
  }

  if (isPortalApiPath(url, "/metadata")) {
    return CACHE_POLICIES.metadataApi;
  }

  if (isPortalMovieDetailApi(url)) {
    return CACHE_POLICIES.movieDetailApi;
  }

  if (isExploreApiCacheable(url)) {
    return CACHE_POLICIES.exploreApi;
  }

  if (isSuggestApiCacheable(url)) {
    return CACHE_POLICIES.suggestApi;
  }

  if (isFeedbackWallApiCacheable(url)) {
    return CACHE_POLICIES.feedbackWallApi;
  }

  return null;
}

function isStaticAssetCacheable(url) {
  if (STATIC_ASSET_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) {
    return true;
  }

  const pathname = url.pathname.toLowerCase();
  return STATIC_ASSET_EXTENSIONS.some((extension) => pathname.endsWith(extension));
}

function isSitemapCacheable(url) {
  if (url.search) {
    return false;
  }

  return url.pathname === "/sitemap.xml"
    || url.pathname === "/feed.xml"
    || url.pathname.startsWith("/sitemaps/");
}

function isAnonymousHtmlCacheable(url) {
  if (url.pathname === "/") {
    return true;
  }

  if (PUBLIC_HTML_PATHS.has(url.pathname)) {
    return true;
  }

  if (/^\/movie\/[0-9a-f-]{36}$/i.test(url.pathname)) {
    return true;
  }

  if (url.pathname.startsWith("/topic/")) {
    return true;
  }

  if (url.pathname === "/explore") {
    return isExploreQueryCacheable(url);
  }

  return false;
}

function isExploreApiCacheable(url) {
  if (!isPortalApiPath(url, "/explore")) {
    return false;
  }

  return isExploreQueryCacheable(url);
}

function isSuggestApiCacheable(url) {
  if (!isPortalApiPath(url, "/search/suggest")) {
    return false;
  }

  const keyword = (url.searchParams.get("keyword") || "").trim();
  return keyword.length >= 2 || containsCjk(keyword);
}

function isFeedbackWallApiCacheable(url) {
  if (!isPortalApiPath(url, "/feedback/wall")) {
    return false;
  }

  return isFirstPage(url);
}

function isExploreQueryCacheable(url) {
  const keyword = (url.searchParams.get("keyword") || "").trim();

  if (keyword.length > 50) {
    return false;
  }

  return isFirstPage(url);
}

function isFirstPage(url) {
  const page = url.searchParams.get("page");

  if (page && page !== "0") {
    return false;
  }

  return true;
}

function containsCjk(value) {
  return /[\u3400-\u9fff\uf900-\ufaff]/u.test(value);
}

function isPortalApiPath(url, suffix) {
  return getPortalApiSuffix(url) === suffix;
}

function isPortalMovieDetailApi(url) {
  return /^\/movies\/[0-9a-f-]{36}$/i.test(getPortalApiSuffix(url));
}

function getPortalApiSuffix(url) {
  const prefix = PORTAL_API_PREFIXES.find((value) => {
    return url.pathname === value || url.pathname.startsWith(`${value}/`);
  });

  if (!prefix) {
    return "";
  }

  return url.pathname.slice(prefix.length) || "/";
}

function normalizeCacheKeyUrl(url, policy) {
  const cacheKeyUrl = new URL(url.toString());

  IGNORED_SEARCH_PARAMS.forEach((param) => cacheKeyUrl.searchParams.delete(param));

  if (shouldStripAllSearchParams(url, policy)) {
    cacheKeyUrl.search = "";
  } else if (policy === CACHE_POLICIES.exploreApi || url.pathname === "/explore") {
    keepOnlySearchParams(cacheKeyUrl, EXPLORE_CACHE_PARAMS);
  } else if (policy === CACHE_POLICIES.suggestApi) {
    keepOnlySearchParams(cacheKeyUrl, SUGGEST_CACHE_PARAMS);
  } else if (policy === CACHE_POLICIES.feedbackWallApi) {
    keepOnlySearchParams(cacheKeyUrl, FEEDBACK_WALL_CACHE_PARAMS);
  }

  if (policy.keyVersion) {
    cacheKeyUrl.searchParams.set("__edge_cache_version", policy.keyVersion);
  }

  cacheKeyUrl.searchParams.sort();

  return cacheKeyUrl;
}

function shouldStripAllSearchParams(url, policy) {
  if (policy === CACHE_POLICIES.staticAsset || policy === CACHE_POLICIES.sitemap) {
    return false;
  }

  if (policy === CACHE_POLICIES.homeApi || policy === CACHE_POLICIES.metadataApi || policy === CACHE_POLICIES.movieDetailApi) {
    return true;
  }

  if (policy === CACHE_POLICIES.html) {
    return url.pathname === "/" || /^\/movie\/[0-9a-f-]{36}$/i.test(url.pathname) || url.pathname.startsWith("/topic/");
  }

  return false;
}

function keepOnlySearchParams(url, allowedParams) {
  const allowed = new Set(allowedParams);
  Array.from(url.searchParams.keys()).forEach((param) => {
    if (!allowed.has(param)) {
      url.searchParams.delete(param);
    }
  });
}

async function fetchBypass(request, reason) {
  if (reason === "method") {
    return fetch(request);
  }

  const response = await fetch(request, { cache: "no-store" });
  const safeResponse = new Response(response.body, response);
  safeResponse.headers.set("Cache-Control", "private, no-store, max-age=0, must-revalidate");
  safeResponse.headers.set("X-Edge-Cache", "BYPASS");
  safeResponse.headers.set("X-Edge-Cache-Reason", reason);

  return safeResponse;
}

function buildCacheableResponse(response, policy, cacheKeyUrl) {
  const result = new Response(response.body, response);
  const contentType = response.headers.get("Content-Type") || "";

  result.headers.set("X-Edge-Cache-Policy", policy.name);
  result.headers.set("X-Edge-Cache-Key", `${cacheKeyUrl.pathname}${cacheKeyUrl.search}`);

  if (response.status !== 200) {
    result.headers.set("X-Edge-Cache", "BYPASS");
    result.headers.set("X-Edge-Cache-Reason", `status-${response.status}`);
    result.headers.set("Cache-Control", "no-store");
    return { cacheable: false, response: result };
  }

  if (response.headers.has("Set-Cookie")) {
    result.headers.set("X-Edge-Cache", "BYPASS");
    result.headers.set("X-Edge-Cache-Reason", "set-cookie");
    result.headers.set("Cache-Control", "private, no-store, max-age=0, must-revalidate");
    return { cacheable: false, response: result };
  }

  if (policy.contentTypeIncludes && !contentType.toLowerCase().includes(policy.contentTypeIncludes)) {
    result.headers.set("X-Edge-Cache", "BYPASS");
    result.headers.set("X-Edge-Cache-Reason", "content-type");
    result.headers.set("Cache-Control", "no-store");
    return { cacheable: false, response: result };
  }

  result.headers.set("X-Edge-Cache", "MISS");
  result.headers.set("Cache-Control", buildCacheControl(policy));

  const cacheResponse = result.clone();
  cacheResponse.headers.set("Cache-Control", `public, max-age=${policy.edgeTtl}`);

  return { cacheable: true, response: result, cacheResponse };
}

async function fetchCacheMiss(request, policy, cache, cacheKey, cacheKeyUrl, ctx) {
  if (request.method !== "GET" || policy === CACHE_POLICIES.staticAsset) {
    return fetchOriginForCache(request, policy, cache, cacheKey, cacheKeyUrl, ctx);
  }

  const inFlightKey = cacheKeyUrl.toString();
  let inFlight = INFLIGHT_CACHE_MISSES.get(inFlightKey);

  if (!inFlight) {
    inFlight = fetchOriginForCache(request, policy, cache, cacheKey, cacheKeyUrl, ctx)
      .then(materializeResponse);
    INFLIGHT_CACHE_MISSES.set(inFlightKey, inFlight);
    inFlight.then(
      () => INFLIGHT_CACHE_MISSES.delete(inFlightKey),
      () => INFLIGHT_CACHE_MISSES.delete(inFlightKey),
    );
  }

  return responseFromMaterialized(await inFlight);
}

async function fetchOriginForCache(request, policy, cache, cacheKey, cacheKeyUrl, ctx) {
  const originResponse = await fetch(request);
  const cacheableResponse = buildCacheableResponse(originResponse, policy, cacheKeyUrl);

  if (request.method === "GET" && cacheableResponse.cacheable) {
    ctx.waitUntil(putCache(cache, cacheKey, cacheableResponse.cacheResponse));
  }

  return cacheableResponse.response;
}

function toClientResponse(response, method) {
  return method === "HEAD" ? new Response(null, response) : response;
}

async function materializeResponse(response) {
  return {
    body: await response.arrayBuffer(),
    status: response.status,
    statusText: response.statusText,
    headers: Array.from(response.headers.entries()),
  };
}

function responseFromMaterialized(materialized) {
  return new Response(materialized.body.slice(0), {
    status: materialized.status,
    statusText: materialized.statusText,
    headers: materialized.headers,
  });
}

async function putCache(cache, cacheKey, response) {
  try {
    await cache.put(cacheKey, response);
  } catch (error) {
    console.warn("[Edge] Cache put failed:", error);
  }
}

function withHitHeaders(response, policy, cacheKeyUrl, method) {
  const result = new Response(method === "HEAD" ? null : response.body, response);

  result.headers.set("X-Edge-Cache", "HIT");
  result.headers.set("X-Edge-Cache-Policy", policy.name);
  result.headers.set("X-Edge-Cache-Key", `${cacheKeyUrl.pathname}${cacheKeyUrl.search}`);
  result.headers.set("Cache-Control", buildCacheControl(policy));

  return result;
}

function buildCacheControl(policy) {
  return [
    "public",
    `max-age=${policy.browserTtl}`,
    `s-maxage=${policy.edgeTtl}`,
    `stale-while-revalidate=${policy.staleWhileRevalidate}`,
    `stale-if-error=${policy.staleIfError}`,
  ].join(", ");
}
