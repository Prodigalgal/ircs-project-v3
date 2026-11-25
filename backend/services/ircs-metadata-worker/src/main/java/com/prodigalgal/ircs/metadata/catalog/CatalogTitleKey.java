package com.prodigalgal.ircs.metadata.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class CatalogTitleKey {
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{P}\\p{Z}\\s]+");

    private CatalogTitleKey() { }

    static String of(String title) {
        return title == null ? "" : SEPARATORS.matcher(
                Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
