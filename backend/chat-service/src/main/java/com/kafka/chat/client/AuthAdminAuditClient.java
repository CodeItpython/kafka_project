package com.kafka.chat.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Feign client for auth-service's internal admin-audit sink. */
@FeignClient(
        name = "auth-admin-audit",
        url = "${app.services.auth-service.url:http://localhost:8890}",
        path = "/api/internal/admin-audit"
)
public interface AuthAdminAuditClient {

    @PostMapping
    void record(@RequestBody AdminAuditRecordRequest request);
}
