package com.kafka.auth.internal;

import com.kafka.auth.admin.audit.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal audit sink consumed by chat-service. Guarded by
 * {@link com.kafka.auth.config.InternalApiTokenFilter}; not for public traffic.
 */
@RestController
@RequestMapping("/api/internal/admin-audit")
@RequiredArgsConstructor
public class InternalAdminAuditController {
    private final AdminAuditService adminAuditService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@RequestBody AdminAuditRecordRequest request) {
        adminAuditService.record(request);
    }
}
