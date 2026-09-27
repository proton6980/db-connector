package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SqlAuditLogRepository extends JpaRepository<SqlAuditLog, Long> {

    @Query("SELECT l FROM SqlAuditLog l WHERE " +
            "(:connectionId IS NULL OR l.connectionId = :connectionId) AND " +
            "(:accountId IS NULL OR l.accountId = :accountId) AND " +
            "(:status IS NULL OR l.status = :status) AND " +
            "(:fromTime IS NULL OR l.executedAt >= :fromTime) AND " +
            "(:toTime IS NULL OR l.executedAt <= :toTime) " +
            "ORDER BY l.executedAt DESC")
    Page<SqlAuditLog> search(@Param("connectionId") String connectionId,
                             @Param("accountId") String accountId,
                             @Param("status") String status,
                             @Param("fromTime") LocalDateTime fromTime,
                             @Param("toTime") LocalDateTime toTime,
                             Pageable pageable);

    @Query("SELECT DISTINCT l.accountId FROM SqlAuditLog l ORDER BY l.accountId")
    List<String> distinctAccountIds();

    @Query("SELECT l.status, COUNT(l), AVG(l.durationMs) FROM SqlAuditLog l " +
            "WHERE l.executedAt >= :since GROUP BY l.status")
    List<Object[]> statusStatsSince(@Param("since") LocalDateTime since);

    @Query("SELECT l.status, COUNT(l), AVG(l.durationMs) FROM SqlAuditLog l GROUP BY l.status")
    List<Object[]> statusStatsAll();

    @Query(value = "SELECT sql_text, COUNT(*) AS cnt, AVG(duration_ms) AS avg_dur " +
            "FROM sql_audit_log GROUP BY sql_text ORDER BY cnt DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> topSql(@Param("limit") int limit);

    @Query("SELECT COUNT(l) FROM SqlAuditLog l WHERE l.executedAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT l FROM SqlAuditLog l WHERE l.executedAt >= :since ORDER BY l.executedAt DESC")
    List<SqlAuditLog> findSince(@Param("since") LocalDateTime since);
}