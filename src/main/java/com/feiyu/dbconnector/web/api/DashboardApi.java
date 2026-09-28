package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@ConditionalOnWebApplication
public class DashboardApi {

    private final SqlAuditLogRepository auditRepo;
    private final DbConnectionRepository connectionRepo;
    private final DynamicDataSourceManager dataSourceManager;

    public DashboardApi(SqlAuditLogRepository auditRepo, DbConnectionRepository connectionRepo,
                        DynamicDataSourceManager dataSourceManager) {
        this.auditRepo = auditRepo;
        this.connectionRepo = connectionRepo;
        this.dataSourceManager = dataSourceManager;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LocalDateTime since = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(24);

        long totalLast24h = auditRepo.countSince(since);

        List<Object[]> statusStats = auditRepo.statusStatsSince(since);
        long successCount = 0, errorCount = 0, blockedCount = 0;
        double avgDuration = 0;
        long totalCount = 0;
        for (Object[] row : statusStats) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double avg = row[2] != null ? ((Number) row[2]).doubleValue() : 0;
            totalCount += count;
            avgDuration += avg * count;
            switch (status) {
                case "SUCCESS" -> successCount = count;
                case "ERROR" -> errorCount = count;
                case "BLOCKED" -> blockedCount = count;
            }
        }
        if (totalCount > 0) {
            avgDuration /= totalCount;
        }
        double errorRate = totalCount > 0 ? (double) errorCount / totalCount * 100 : 0;

        List<Map<String, Object>> topSql = new ArrayList<>();
        for (Object[] row : auditRepo.topSql(5)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sqlText", row[0]);
            item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            item.put("avgDuration", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            topSql.add(item);
        }

        List<SqlAuditLog> recentLogs = auditRepo.findSince(since);
        Map<Integer, Long> hourlyQps = new HashMap<>();
        for (SqlAuditLog l : recentLogs) {
            if (l.getExecutedAt() != null) {
                int hour = l.getExecutedAt().getHour();
                hourlyQps.merge(hour, 1L, Long::sum);
            }
        }

        List<Map<String, Object>> poolStatus = new ArrayList<>();
        for (DbConnection c : connectionRepo.findAll()) {
            HikariDataSource ds = dataSourceManager.getPool(c.getId());
            if (ds != null && !ds.isClosed()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", c.getName());
                info.put("active", ds.getHikariPoolMXBean().getActiveConnections());
                info.put("idle", ds.getHikariPoolMXBean().getIdleConnections());
                info.put("total", ds.getHikariPoolMXBean().getTotalConnections());
                info.put("waiting", ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
                poolStatus.add(info);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLast24h", totalLast24h);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("blockedCount", blockedCount);
        result.put("errorRate", errorRate);
        result.put("avgDuration", avgDuration);
        result.put("hourlyQps", hourlyQps);
        result.put("topSql", topSql);
        result.put("poolStatus", poolStatus);
        return result;
    }
}
