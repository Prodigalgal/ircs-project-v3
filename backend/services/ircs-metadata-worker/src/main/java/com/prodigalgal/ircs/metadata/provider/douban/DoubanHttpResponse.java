package com.prodigalgal.ircs.metadata.provider.douban;

/** HTTP status and text body from a bounded public Douban request. */
public record DoubanHttpResponse(int statusCode, String body) {
}
