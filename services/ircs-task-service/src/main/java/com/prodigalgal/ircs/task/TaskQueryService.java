package com.prodigalgal.ircs.task;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskQueryService {

    private final JdbcCollectionTaskRepository taskRepository;

    public Page<TaskCardSummary> findAll(
            Pageable pageable,
            String name,
            String status,
            UUID dataSourceId,
            Boolean enabled
    ) {
        return taskRepository.findAll(pageable, name, normalizeStatus(status), dataSourceId, enabled);
    }

    public Optional<TaskDetailSummary> findOne(UUID id) {
        return taskRepository.findById(id);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }
}
