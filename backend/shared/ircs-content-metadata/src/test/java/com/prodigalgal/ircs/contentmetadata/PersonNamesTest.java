package com.prodigalgal.ircs.contentmetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonNamesTest {
    @Test
    void spellingAndRolesAreNotRewrittenByLanguageDictionaries() {
        assertEquals(Set.of("罗予彤", "孙乾钊", "Robert Downey Jr.", "นภัสกร ปิงเมือง", "Álvaro Morte"),
                PersonNames.normalize(List.of("罗予彤＆孙乾钊", "Robert Downey Jr.", "นภัสกร ปิงเมือง", "Álvaro Morte")));
    }

    @Test
    void contextualChineseSeparatorsDoNotSplitEnglishNamesAndOrganizations() {
        assertEquals(Set.of("张三", "李四", "王五", "R&D", "Simon & Garfunkel"),
                PersonNames.normalize(List.of("张三&amp;李四＆王五", "R&D", "Simon & Garfunkel")));
        assertEquals(Set.of("A&B"), PersonNames.normalize(List.of("Ａ＆Ｂ")));
    }

    @Test
    void placeholdersAreRemovedBeforeSplittingAndWithoutTruncatingNames() {
        assertTrue(PersonNames.normalize(Arrays.asList(null, "N/A", "未知", "内详", "暂无", "0", "000", "none")).isEmpty());
        assertEquals(Set.of("张三", "李四"), PersonNames.normalize(List.of("张三,李四;张三", "n/a")));
        assertTrue(PersonNames.normalize(List.of("A".repeat(256))).isEmpty());
    }

    @Test
    void markupAndWhitespaceAreCleanedWithoutChangingUnicodeNames() {
        assertEquals("张三 李四", PersonNames.normalizeText("<b>张三</b>&nbsp;  李四"));
        assertEquals("罗予彤", PersonNames.normalizeText("&#x7f57;&#20104;彤"));
        assertEquals("张三", PersonNames.normalizeText("&lt;script&gt;bad()&lt;/script&gt;张三"));
        assertEquals("", PersonNames.normalizeText(null));
    }
}
