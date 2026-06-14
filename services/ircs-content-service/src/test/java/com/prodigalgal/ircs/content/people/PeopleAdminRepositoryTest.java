package com.prodigalgal.ircs.content.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.content.video.ContentApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PeopleAdminRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void deleteUnreferencedRejectsLinkedActorBeforeDeleting() {
        UUID id = UUID.randomUUID();
        PeopleAdminRepository repository = new PeopleAdminRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(startsWith("select exists"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                startsWith("select (select count(*) from video_actors"),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> repository.deleteUnreferenced(PeopleResource.ACTORS, id))
                .isInstanceOfSatisfying(ContentApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getMessage()).contains("linked videos");
                });
        verify(jdbcTemplate, never()).update(startsWith("delete from actors"), any(MapSqlParameterSource.class));
    }

    @Test
    void deleteUnreferencedDeletesTemporaryDirectorWhenNoLinksExist() {
        UUID id = UUID.randomUUID();
        PeopleAdminRepository repository = new PeopleAdminRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(startsWith("select exists"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                startsWith("select (select count(*) from video_directors"),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.update(startsWith("delete from directors"), any(MapSqlParameterSource.class))).thenReturn(1);

        repository.deleteUnreferenced(PeopleResource.DIRECTORS, id);

        verify(jdbcTemplate).update(startsWith("delete from directors"), any(MapSqlParameterSource.class));
    }
}
