package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class AuthorityIdPlaceholderChangelogTest {
    private static final String RESOURCE = "db/changelog/2026/11/16-01-clear-authoritative-id-placeholders.sql";

    @Test
    void registersTransactionalForwardRepairAndBusinessVersionTag() throws Exception {
        try (var master = getClass().getClassLoader().getResourceAsStream("db/changelog/db.changelog-master.yaml");
                var accessor = new ClassLoaderResourceAccessor()) {
            assertThat(master).isNotNull();
            assertThat(new String(master.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("file: " + RESOURCE, "1785900000006-tag-3.5.2", "tagDatabase:", "tag: 3.5.2");
            var parser = ChangeLogParserFactory.getInstance().getParser(RESOURCE, accessor);
            var changes = parser.parse(RESOURCE, new ChangeLogParameters(), accessor).getChangeSets();
            assertThat(changes).hasSize(1);
            var change = changes.getFirst();
            assertThat(change.getId()).isEqualTo("1785900000005-clear-authoritative-id-placeholders");
            assertThat(change.isRunInTransaction()).isTrue();
            assertThat(change.getChanges()).hasSize(1);
            assertThat(change.getChanges().getFirst()).isInstanceOf(RawSQLChange.class);
            assertThat(((RawSQLChange) change.getChanges().getFirst()).getSql())
                    .contains("SET LOCAL lock_timeout = '30s'", "SET LOCAL statement_timeout = '120s'");
        }
    }
}
