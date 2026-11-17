package com.prodigalgal.ircs.scraper;

/** Safe to include in source status responses; never holds a credential-bearing URL. */
final class TrendProviderFetchException extends IllegalStateException {

    TrendProviderFetchException(String code) {
        super(code);
    }
}
