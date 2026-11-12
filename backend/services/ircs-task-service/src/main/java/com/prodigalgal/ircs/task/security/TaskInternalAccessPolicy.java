package com.prodigalgal.ircs.task.security;

import com.prodigalgal.ircs.common.security.InternalServiceAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskInternalAccessPolicy {

    @Value("${app.task.internal-access.require-token:true}")
    private boolean requireToken;

    @Value("${app.task.internal-access.token:${APP_TASK_SERVICE_TOKEN:}}")
    private String configuredToken;

    @Value("${app.task.internal-access.required-scope:task:maintenance}")
    private String requiredScope;

    public void assertAccess(String serviceId, String serviceToken, String serviceScopes) {
        InternalServiceAccessPolicy.require(
                "Task",
                configuredToken,
                requiredScope,
                serviceId,
                serviceToken,
                serviceScopes);
    }
}
