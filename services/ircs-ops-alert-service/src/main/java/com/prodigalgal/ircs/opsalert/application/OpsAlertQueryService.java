package com.prodigalgal.ircs.opsalert.application;

import com.prodigalgal.ircs.opsalert.dto.AlertEventFilter;
import com.prodigalgal.ircs.opsalert.dto.AlertEventResponse;
import com.prodigalgal.ircs.opsalert.dto.HealingActionFilter;
import com.prodigalgal.ircs.opsalert.dto.HealingActionResponse;
import com.prodigalgal.ircs.opsalert.dto.IncidentFilter;
import com.prodigalgal.ircs.opsalert.dto.IncidentResponse;
import com.prodigalgal.ircs.opsalert.dto.OpsAlertMapper;
import com.prodigalgal.ircs.opsalert.infrastructure.OpsAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpsAlertQueryService {

    private final OpsAlertRepository repository;

    public Page<AlertEventResponse> findEvents(Pageable pageable, AlertEventFilter filter) {
        return repository.findEvents(pageable, filter).map(OpsAlertMapper::toResponse);
    }

    public Page<IncidentResponse> findIncidents(Pageable pageable, IncidentFilter filter) {
        return repository.findIncidents(pageable, filter).map(OpsAlertMapper::toResponse);
    }

    public Page<HealingActionResponse> findHealingActions(Pageable pageable, HealingActionFilter filter) {
        return repository.findHealingActions(pageable, filter).map(OpsAlertMapper::toResponse);
    }
}
