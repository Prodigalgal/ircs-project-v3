package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.web.ApiErrorResponse;
import com.prodigalgal.ircs.common.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        RequestAuditController.class,
        WorkerJobAuditController.class,
        NotificationMailSendHistoryController.class,
        AuditGovernanceController.class,
        DlqController.class,
        RabbitDlqController.class,
        TrafficMonitorController.class,
        MaintenanceController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpsApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ApiErrorResponses.response(
                HttpStatus.BAD_REQUEST,
                "ops.request.invalid",
                ex.getMessage(),
                "ops",
                request);
    }
}
