package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

final class TrendSourceValues {

    private TrendSourceValues() {
    }

    static LocalDate date(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : decimal(node.asText());
    }

    static String positiveId(String value) {
        return value != null && value.matches("[1-9]\\d{0,15}") ? value : null;
    }

    static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    static TrendBoardPayload unavailable(String provider, String board, String mediaType,
            String window, Instant observedAt, String status, String errorCode) {
        return new TrendBoardPayload(provider, board, mediaType, window, observedAt, status, errorCode, List.of());
    }

    static String failureCode(RuntimeException failure) {
        return failure instanceof TrendProviderFetchException ? failure.getMessage() : "INVALID_RESPONSE";
    }
}
