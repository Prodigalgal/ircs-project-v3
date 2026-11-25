package com.prodigalgal.ircs.metadata.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record DoubanCatalogObservation(Facts facts, BigDecimal rating, Long voteCount) {
    record Facts(String doubanId, String imdbId, String title, String originalTitle, String year,
                 List<String> aliases, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate mainlandReleaseDate) {
        Facts {
            aliases = List.copyOf(aliases);
        }
    }
}
