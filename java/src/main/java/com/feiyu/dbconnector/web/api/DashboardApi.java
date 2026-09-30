package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Controller("/api/dashboard")
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

    @Get("/stats")
    public Map<String, Object> stats() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        long totalLast24h = auditRepo.countSince(since);
        List<Object[]> statusStats = auditRepo.statusStatsSince(since);

        long successCount = 0;
        long errorCount = 0;
        long blockedCount = 0;
        double totalDuration = 0;
        long durationCount = 0;

        for (Object[] row : statusStats) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double avgDur = ((Number) row[2]).doubleValue();
            switch (status) {
                case "SUCCESS" -> successCount = count;
                case "ERROR" -> errorCount = count;
                case "BLOCKED" -> blockedCount = count;
            }
            totalDuration += avgDur * count;
            durationCount += count;
        }

        double errorRate = totalLast24h > 0 ? (errorCount * 100.0 / totalLast24h) : 0.0;
        double avgDuration = durationCount > 0 ? (totalDuration / durationCount) : 0.0;

        List<Object[]> topSqlRaw = auditRepo.topSql(5);
        List<Map<String, Object>> topSql = new ArrayList<>();
        for (Object[] row : topSqlRaw) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sqlText", row[0]);
            item.put("count", ((Number) row[1]).longValue());
            item.put("avgDuration", ((Number) row[2]).doubleValue());
            topSql.add(item);
        }

        List<Map<String, Object>> poolStatus = new ArrayList<>();
        for (DbConnection c : connectionRepo.findAll()) {
            HikariDataSource pool = dataSourceManager.getPool(c.getId());
            if (pool != null && !pool.isClosed()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", c.getName());
                p.put("active", pool.getHikariPoolMXBean().getActiveConnections());
                p.put("idle", pool.getHikariPoolMXBean().getIdleConnections());
                p.put("total", pool.getHikariPoolMXBean().getTotalConnections());
                p.put("waiting", pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
                poolStatus.add(p);
            }
        }

        Map<String, Object> hourlyQps = new LinkedHashMap<>();
        LocalDateTime hourStart = LocalDateTime.now().minusHours(24).withMinute(0).withSecond(0).withNano(0);
        for (int i = 0; i < 24; i++) {
            LocalDateTime hStart = hourStart.plusHours(i);
            LocalDateTime hEnd = hStart.plusHours(1);
            long count = auditRepo.countSince(hStart);
            hourlyQps.put(String.valueOf(hStart.getHour()), count);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLast24h", totalLast24h);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("blockedCount", blockedCount);
        result.put("errorRate", errorRate);
        result.put("avgDuration", avgDuration);
        result.put("topSql", topSql);
        result.put("poolStatus", poolStatus);
        result.put("hourlyQps", hourlyQps);
        return result;
    }
}