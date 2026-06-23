package com.prodigalgal.ircs.content.video.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VideoAdminQuerySqlPolicyTest {

    @Test
    void rawDetailQueryExposesResolvedCategoryAliasUsedByMapper() throws IOException {
        String source = Files.readString(sourceFile());

        assertThat(source).contains("sc.id as resolved_category_id");
        assertThat(source).contains("rs.getObject(\"resolved_category_id\", UUID.class)");
        assertThat(source).doesNotContain("select rv.*, sc.id as category_id");
    }

    private static Path sourceFile() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path relative = Path.of(
                "services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/video/infrastructure/VideoAdminQueryRepository.java");
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path file = candidate.resolve(relative);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        Path moduleFile = current.resolve(
                "src/main/java/com/prodigalgal/ircs/content/video/infrastructure/VideoAdminQueryRepository.java");
        if (Files.isRegularFile(moduleFile)) {
            return moduleFile;
        }
        return relative;
    }
}
