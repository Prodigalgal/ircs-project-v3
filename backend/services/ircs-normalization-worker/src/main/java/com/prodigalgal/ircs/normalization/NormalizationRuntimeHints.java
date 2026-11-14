package com.prodigalgal.ircs.normalization;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/** OpenCC's Instances.singleton constructs these dictionary/segment classes reflectively. */
public final class NormalizationRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String type : new String[] {
                "support.data.impl.STCharData", "support.data.impl.TSCharData",
                "support.data.impl.TSPhraseData", "support.data.impl.STPhraseData",
                "support.data.impl.TwSTCharData", "support.data.impl.TwTSCharData",
                "support.data.impl.TwTSPhraseData", "support.data.impl.TwSTPhraseData",
                "support.datamap.impl.DataMapDefault", "support.datamap.impl.DataMapTaiwan",
                "support.segment.impl.CharSegment", "support.segment.impl.HuaBanSegment",
                "support.segment.impl.FastForwardSegment", "support.segment.impl.TwFastForwardSegment"
        }) {
            hints.reflection().registerType(TypeReference.of("com.github.houbb.opencc4j." + type),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        hints.resources().registerPattern("data/dictionary/**", "hanlp.properties");
    }
}
