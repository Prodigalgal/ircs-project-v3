package com.prodigalgal.ircs.content.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonPatchRequest;
import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonRequest;
import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonResponse;
import com.prodigalgal.ircs.content.video.ContentApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PeopleAdminServiceTest {

    @Mock
    private PeopleAdminRepository repository;

    @InjectMocks
    private PeopleAdminService service;

    @Test
    void createRejectsBodyIdLikeV1() {
        PersonRequest request = new PersonRequest(UUID.randomUUID(), "Actor One");

        assertThatThrownBy(() -> service.create(PeopleResource.ACTORS, request))
                .isInstanceOfSatisfying(ContentApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("cannot already have an ID");
                });
        verifyNoInteractions(repository);
    }

    @Test
    void updateUsesPathIdWhenRequestBodyOmitsId() {
        UUID pathId = UUID.randomUUID();
        PersonRequest request = new PersonRequest(null, "Actor One");
        PersonResponse response = response(pathId, "Actor One");
        when(repository.update(PeopleResource.ACTORS, pathId, "Actor One")).thenReturn(response);

        assertThat(service.update(PeopleResource.ACTORS, pathId, request)).isEqualTo(response);

        verify(repository).update(PeopleResource.ACTORS, pathId, "Actor One");
    }

    @Test
    void patchUsesPathIdEvenWhenRequestBodyCarriesAnotherId() {
        UUID pathId = UUID.randomUUID();
        PersonPatchRequest request = new PersonPatchRequest(UUID.randomUUID(), "Director One");
        PersonResponse response = response(pathId, "Director One");
        when(repository.patch(PeopleResource.DIRECTORS, pathId, "Director One")).thenReturn(response);

        assertThat(service.patch(PeopleResource.DIRECTORS, pathId, request)).isEqualTo(response);

        verify(repository).patch(PeopleResource.DIRECTORS, pathId, "Director One");
    }

    private PersonResponse response(UUID id, String name) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        return new PersonResponse(id, name, now, now);
    }
}
