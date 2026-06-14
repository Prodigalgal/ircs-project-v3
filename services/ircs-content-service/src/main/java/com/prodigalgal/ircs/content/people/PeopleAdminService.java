package com.prodigalgal.ircs.content.people;

import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonPatchRequest;
import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonRequest;
import com.prodigalgal.ircs.content.people.PeopleAdminDtos.PersonResponse;
import com.prodigalgal.ircs.content.video.ContentApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeopleAdminService {

    private final PeopleAdminRepository repository;

    public Page<PersonResponse> findAll(PeopleResource resource, Pageable pageable, String name) {
        return repository.findAll(resource, pageable, name);
    }

    public List<PersonResponse> findAllSortedByName(PeopleResource resource) {
        return repository.findAllSortedByName(resource);
    }

    public Optional<PersonResponse> findOne(PeopleResource resource, UUID id) {
        return repository.findOne(resource, id);
    }

    public PersonResponse create(PeopleResource resource, PersonRequest request) {
        if (request.id() != null) {
            throw new ContentApiException(
                    HttpStatus.BAD_REQUEST,
                    "A new " + resource.entityName() + " cannot already have an ID");
        }
        return repository.create(resource, request.name());
    }

    public PersonResponse update(PeopleResource resource, UUID id, PersonRequest request) {
        return repository.update(resource, id, request.name());
    }

    public PersonResponse patch(PeopleResource resource, UUID id, PersonPatchRequest request) {
        return repository.patch(resource, id, request.name());
    }

    public void delete(PeopleResource resource, UUID id) {
        repository.deleteUnreferenced(resource, id);
    }
}
