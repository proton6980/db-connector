package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SqlAuditLogRepository extends JpaRepository<SqlAuditLog, Long> {
}
