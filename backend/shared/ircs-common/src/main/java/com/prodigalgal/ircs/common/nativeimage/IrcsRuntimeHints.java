package com.prodigalgal.ircs.common.nativeimage;

import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;

/** Registers queue/cache DTOs that MVC's controller signature analysis cannot discover. */
public final class IrcsRuntimeHints implements RuntimeHintsRegistrar {

    private static final Pattern BINDING_TYPE = Pattern.compile(
            ".*(DTO|Dto|Request|Response|Result|Payload|Message|Event|Document|Snapshot|Record|Options)$");
    private static final String[] JWT_TYPES = {
            "io.jsonwebtoken.impl.DefaultClaimsBuilder",
            "io.jsonwebtoken.impl.DefaultJwtBuilder",
            "io.jsonwebtoken.impl.DefaultJwtHeaderBuilder",
            "io.jsonwebtoken.impl.DefaultJwtParserBuilder",
            "io.jsonwebtoken.impl.security.KeysBridge",
            "io.jsonwebtoken.impl.io.StandardCompressionAlgorithms",
            "io.jsonwebtoken.impl.compression.DeflateCompressionAlgorithm",
            "io.jsonwebtoken.impl.compression.GzipCompressionAlgorithm",
            "io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms",
            "io.jsonwebtoken.impl.security.StandardHashAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyAlgorithms",
            "io.jsonwebtoken.impl.security.StandardKeyOperations",
            "io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms"
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String pattern : new String[] {
                "*.properties", "*.yml", "*.yaml", "log4j2*.xml", "META-INF/services/*",
                "**/Log4j2Plugins.dat", "**/*.sql", "**/*.lua", "data/dictionary/**",
                "**/*.txt", "templates/**", "static/**", "elasticsearch/*.json", "presets/*.json",
                "org/apache/tika/**", "org/apache/pdfbox/**"
        }) {
            hints.resources().registerPattern(pattern);
        }
        registerBindingTypes(hints, classLoader);
        for (String typeName : JWT_TYPES) {
            if (ClassUtils.isPresent(typeName, classLoader)) {
                hints.reflection().registerType(ClassUtils.resolveClassName(typeName, classLoader),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
            }
        }
    }

    private void registerBindingTypes(RuntimeHints hints, ClassLoader classLoader) {
        var resolver = new PathMatchingResourcePatternResolver(classLoader);
        var readers = new CachingMetadataReaderFactory(resolver);
        var bindings = new BindingReflectionHintsRegistrar();
        try {
            for (var resource : resolver.getResources("classpath*:com/prodigalgal/ircs/**/*.class")) {
                var metadata = readers.getMetadataReader(resource).getClassMetadata();
                String name = metadata.getClassName();
                if ("java.lang.Record".equals(metadata.getSuperClassName())
                        || "java.lang.Enum".equals(metadata.getSuperClassName())
                        || BINDING_TYPE.matcher(name).matches()) {
                    Class<?> type = ClassUtils.resolveClassName(name, classLoader);
                    bindings.registerReflectionHints(hints.reflection(), type);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to discover IRCS native JSON binding types", ex);
        }
    }
}
