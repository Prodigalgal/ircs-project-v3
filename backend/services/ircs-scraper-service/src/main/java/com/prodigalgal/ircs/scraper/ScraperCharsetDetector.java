package com.prodigalgal.ircs.scraper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.springframework.stereotype.Component;

@Component
class ScraperCharsetDetector {

    private static final Charset GB18030 = Charset.forName("GB18030");

    private final DefaultEncodingDetector detector = new DefaultEncodingDetector();

    Charset detect(byte[] data) {
        return detect(data, null);
    }

    Charset detect(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            return StandardCharsets.UTF_8;
        }
        Optional<Charset> declared = declaredCharset(contentType);
        if (declared.isPresent()) {
            return declared.get();
        }
        if (decodeStrict(data, StandardCharsets.UTF_8).isPresent()) {
            return StandardCharsets.UTF_8;
        }
        Charset tikaDetected = detectWithTika(data);
        if (tikaDetected != null && "Big5-HKSCS".equalsIgnoreCase(tikaDetected.name())) {
            Charset big5 = Charset.forName("Big5");
            Optional<String> classic = decodeStrict(data, big5);
            if (classic.isPresent() && classic.equals(decodeStrict(data, tikaDetected))) {
                return big5;
            }
        }
        if (isEastAsianCharset(tikaDetected)) {
            return tikaDetected;
        }
        Optional<String> gb18030 = decodeStrict(data, GB18030);
        if (gb18030.filter(this::containsCjkText).isPresent()) {
            return GB18030;
        }
        return tikaDetected == null ? StandardCharsets.UTF_8 : tikaDetected;
    }

    private Optional<Charset> declaredCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(contentType.split(";"))
                .map(String::trim)
                .filter(part -> part.toLowerCase(Locale.ROOT).startsWith("charset="))
                .map(part -> part.substring("charset=".length()).trim())
                .map(value -> value.replace("\"", "").replace("'", ""))
                .filter(Charset::isSupported)
                .map(Charset::forName)
                .findFirst();
    }

    private Charset detectWithTika(byte[] data) {
        try (TikaInputStream input = TikaInputStream.get(data)) {
            var results = detector.detect(input, new Metadata(), new ParseContext());
            return results.isEmpty() ? null : results.getFirst().getCharset();
        } catch (IOException ex) {
            return null;
        }
    }

    private Optional<String> decodeStrict(byte[] data, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(data)).toString());
        } catch (CharacterCodingException ex) {
            return Optional.empty();
        }
    }

    private boolean isEastAsianCharset(Charset charset) {
        if (charset == null) {
            return false;
        }
        String name = charset.name().toLowerCase(Locale.ROOT);
        return name.contains("gb")
                || name.contains("big5")
                || name.contains("shift_jis")
                || name.contains("euc-jp")
                || name.contains("euc-kr")
                || name.contains("ksc");
    }

    private boolean containsCjkText(String text) {
        return text.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }
}
