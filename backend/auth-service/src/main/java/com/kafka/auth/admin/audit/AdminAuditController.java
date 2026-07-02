package com.kafka.auth.admin.audit;

import com.kafka.auth.admin.audit.AdminAuditDtos.AdminAuditEventListResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-events")
@RequiredArgsConstructor
@Validated
public class AdminAuditController {
    private final AdminAuditService adminAuditService;

    @GetMapping
    public ResponseEntity<AdminAuditEventListResponse> events(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(adminAuditService.recentEvents(limit));
    }
}
