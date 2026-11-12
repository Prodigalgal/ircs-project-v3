package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class AuditPollReadIndexesChangelogTest {

    private static final String RESOURCE = "db/changelog/2026/11/12-01-add-audit-poll-read-indexes.sql";

    @Test
    void registeredIndexChangesParseAsIndependentNonTransactionalChangesWithRollback() throws Exception {
        try (var master = getClass().getClassLoader().getResourceAsStream("db/changelog/db.changelog-master.yaml");
                var accessor = new ClassLoaderResourceAccessor()) {
            assertThat(master).isNotNull();
            assertThat(new String(master.readAllBytes(), StandardCharsets.UTF_8)).contains("file: " + RESOURCE);
            var parser = ChangeLogParserFactory.getInstance().getParser(RESOURCE, accessor);
            var changes = parser.parse(RESOURCE, new ChangeLogParameters(), accessor).getChangeSets();

            assertThat(changes).hasSize(4);
            assertThat(changes).extracting(ChangeSet::getId).doesNotHaveDuplicates();
            for (ChangeSet change : changes) {
                assertThat(change.isRunInTransaction()).isFalse();
                assertThat(change.getChanges()).hasSize(1);
                assertThat(change.getChanges().getFirst()).isInstanceOf(RawSQLChange.class);
                assertThat(((RawSQLChange) change.getChanges().getFirst()).getSql())
                        .startsWith("CREATE INDEX CONCURRENTLY IF NOT EXISTS");
                assertThat(change.getRollback().getChanges()).hasSize(1);
            }
        }
    }
}
