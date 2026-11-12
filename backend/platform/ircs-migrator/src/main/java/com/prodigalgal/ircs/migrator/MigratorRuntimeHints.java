package com.prodigalgal.ircs.migrator;

import liquibase.change.core.TagDatabaseChange;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public final class MigratorRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("db/changelog/**");
        hints.resources().registerPattern("META-INF/services/*");
        // Liquibase introspects every change property, including setters unused by our release marker.
        hints.reflection().registerType(TagDatabaseChange.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
