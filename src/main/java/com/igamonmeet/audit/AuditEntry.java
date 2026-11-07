package com.igamonmeet.audit;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.Immutable;

/** One audit row. Immutable: Hibernate never issues UPDATE for it, and SQL Server blocks UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant occurredAt;
    private String actor;
    private String action;
    private String targetType;
    private String targetId;
    private String field;
    private String oldValue;
    private String newValue;
    private String detail;

    protected AuditEntry() {
    }

    AuditEntry(Instant occurredAt, String actor, String action, String targetType, String targetId,
               String field, String oldValue, String newValue, String detail) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getField() { return field; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getDetail() { return detail; }
}
