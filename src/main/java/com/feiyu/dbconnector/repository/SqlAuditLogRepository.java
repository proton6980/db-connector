package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface SqlAuditLogRepository extends CrudRepository<SqlAuditLog, Long> {

    @Query(value = "SELECT * FROM sql_audit_log WHERE " +
            "(:connectionId IS NULL OR connection_id = :connectionId) AND " +
            "(:accountId IS NULL OR account_id = :accountId) AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:fromTime IS NULL OR executed_at >= :fromTime) AND " +
            "(:toTime IS NULL OR executed_at <= :toTime) " +
            "ORDER BY executed_at DESC",
            countQuery = "SELECT COUNT(*) FROM sql_audit_log WHERE " +
            "(:connectionId IS NULL OR connection_id = :connectionId) AND " +
            "(:accountId IS NULL OR account_id = :accountId) AND " +
            "(:status IS NULL OR status = :status) AND " +
            "(:fromTime IS NULL OR executed_at >= :fromTime) AND " +
            "(:toTime IS NULL OR executed_at <= :toTime)",
            nativeQuery = true)
    Page<SqlAuditLog> search(@Nullable String connectionId, @Nullable String accountId, @Nullable String status,
                             @Nullable LocalDateTime fromTime, @Nullable LocalDateTime toTime, Pageable pageable);

    @Query(value = "SELECT DISTINCT account_id FROM sql_audit_log WHERE account_id IS NOT NULL ORDER BY account_id",
            nativeQuery = true)
    List<String> distinctAccountIds();

    @Query(value = "SELECT status, COUNT(*) FROM sql_audit_log WHERE executed_at >= :since GROUP BY status ORDER BY status",
            nativeQuery = true)
    List<Object[]> statusStatsSince(LocalDateTime since);

    @Query(value = "SELECT sql_text, COUNT(*) AS cnt, AVG(duration_ms) AS avg_dur FROM sql_audit_log GROUP BY sql_text ORDER BY cnt DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topSql(int limit);

    @Query(value = "SELECT COUNT(*) FROM sql_audit_log WHERE executed_at >= :since",
            nativeQuery = true)
    long countSince(LocalDateTime since);
}