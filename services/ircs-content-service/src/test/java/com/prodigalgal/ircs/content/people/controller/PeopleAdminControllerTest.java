package com.prodigalgal.ircs.content.people.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prodigalgal.ircs.content.people.application.PeopleAdminService;
import com.prodigalgal.ircs.content.people.domain.PeopleResource;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonPatchRequest;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonRequest;
import com.prodigalgal.ircs.content.people.dto.PeopleAdminDtos.PersonResponse;
import com.prodigalgal.ircs.content.video.api.ContentApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PeopleAdminControllerTest {

    @Mock
    private PeopleAdminService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActorAdminController(service), new DirectorAdminController(service))
                .setControllerAdvice(new ContentApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void actorsRoutesMatchV1AdminContractAndUsePathIdForWrites() throws Exception {
        UUID id = UUID.randomUUID();
        PersonResponse actor = response(id, "Actor One");
        when(service.findAll(eq(PeopleResource.ACTORS), any(Pageable.class), eq("act")))
                .thenReturn(new PageImpl<>(
                        List.of(actor),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                        1));
        when(service.findAllSortedByName(PeopleResource.ACTORS)).thenReturn(List.of(actor));
        when(service.findOne(PeopleResource.ACTORS, id)).thenReturn(java.util.Optional.of(actor));
        when(service.create(eq(PeopleResource.ACTORS), any(PersonRequest.class))).thenReturn(actor);
        when(service.update(eq(PeopleResource.ACTORS), eq(id), any(PersonRequest.class))).thenReturn(actor);
        when(service.patch(eq(PeopleResource.ACTORS), eq(id), any(PersonPatchRequest.class))).thenReturn(actor);

        mockMvc.perform(get("/api/v1/actors")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .param("name", "act"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        mockMvc.perform(get("/api/v1/actors/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Actor One"));
        mockMvc.perform(get("/api/v1/actors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(post("/api/v1/actors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Actor One\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/actors/" + id));
        mockMvc.perform(put("/api/v1/actors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Actor One\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(patch("/api/v1/actors/{id}", id)
                        .contentType("application/merge-patch+json")
                        .content("{\"name\":\"Actor One\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Actor One"));
        mockMvc.perform(delete("/api/v1/actors/{id}", id))
                .andExpect(status().isNoContent());

        ArgumentCaptor<PersonRequest> updateCaptor = ArgumentCaptor.forClass(PersonRequest.class);
        verify(service).update(eq(PeopleResource.ACTORS), eq(id), updateCaptor.capture());
        assertThat(updateCaptor.getValue().id()).isNull();
        assertThat(updateCaptor.getValue().name()).isEqualTo("Actor One");

        ArgumentCaptor<PersonPatchRequest> patchCaptor = ArgumentCaptor.forClass(PersonPatchRequest.class);
        verify(service).patch(eq(PeopleResource.ACTORS), eq(id), patchCaptor.capture());
        assertThat(patchCaptor.getValue().id()).isNull();
    }

    @Test
    void directorsRoutesMatchV1AdminContractAndUsePathIdForWrites() throws Exception {
        UUID id = UUID.randomUUID();
        PersonResponse director = response(id, "Director One");
        when(service.findAll(eq(PeopleResource.DIRECTORS), any(Pageable.class), eq("dir")))
                .thenReturn(new PageImpl<>(
                        List.of(director),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                        1));
        when(service.findAllSortedByName(PeopleResource.DIRECTORS)).thenReturn(List.of(director));
        when(service.findOne(PeopleResource.DIRECTORS, id)).thenReturn(java.util.Optional.of(director));
        when(service.create(eq(PeopleResource.DIRECTORS), any(PersonRequest.class))).thenReturn(director);
        when(service.update(eq(PeopleResource.DIRECTORS), eq(id), any(PersonRequest.class))).thenReturn(director);
        when(service.patch(eq(PeopleResource.DIRECTORS), eq(id), any(PersonPatchRequest.class))).thenReturn(director);

        mockMvc.perform(get("/api/v1/directors")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,desc")
                        .param("name", "dir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        mockMvc.perform(get("/api/v1/directors/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Director One"));
        mockMvc.perform(get("/api/v1/directors/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(post("/api/v1/directors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Director One\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/directors/" + id));
        mockMvc.perform(put("/api/v1/directors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Director One\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(patch("/api/v1/directors/{id}", id)
                        .contentType("application/merge-patch+json")
                        .content("{\"name\":\"Director One\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Director One"));
        mockMvc.perform(delete("/api/v1/directors/{id}", id))
                .andExpect(status().isNoContent());

        ArgumentCaptor<PersonRequest> updateCaptor = ArgumentCaptor.forClass(PersonRequest.class);
        verify(service).update(eq(PeopleResource.DIRECTORS), eq(id), updateCaptor.capture());
        assertThat(updateCaptor.getValue().id()).isNull();

        ArgumentCaptor<PersonPatchRequest> patchCaptor = ArgumentCaptor.forClass(PersonPatchRequest.class);
        verify(service).patch(eq(PeopleResource.DIRECTORS), eq(id), patchCaptor.capture());
        assertThat(patchCaptor.getValue().id()).isNull();
    }

    private PersonResponse response(UUID id, String name) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        return new PersonResponse(id, name, now, now);
    }
}
