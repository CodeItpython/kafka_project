package com.kafka.auth.admin.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "admin_audit_events",
        indexes = {
                @Index(name = "idx_admin_audit_events_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_audit_events_actor_action", columnList = "actor_email, action")
        }
)
public class AdminAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AdminAuditAction action;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 200)
    private String resourceId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private boolean success;

    @Lob
    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AdminAuditEvent(
            String actorEmail,
            String actorName,
            AdminAuditAction action,
            String resourceType,
            String resourceId,
            String summary,
            boolean success,
            String detailsJson
    ) {
        this.actorEmail = actorEmail;
        this.actorName = actorName;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.summary = summary;
        this.success = success;
        this.detailsJson = detailsJson;
    }
}
