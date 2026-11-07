package com.igamonmeet.audit;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Deliberately exposes no update or delete methods. */
public interface AuditLogRepository extends Repository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

    AuditEntry save(AuditEntry entry);
}
