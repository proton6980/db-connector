package com.feiyu.dbconnector.web.dashboard;

import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.feiyu.dbconnector.web.ConsoleProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@ConditionalOnWebApplication
public class DashboardController {

    private final SqlAuditLogRepository auditRepo;
    private final DbConnectionRepository connectionRepo;
    private final DynamicDataSourceManager dataSourceManager;
    private final ConsoleProperties consoleProperties;

    public DashboardController(SqlAuditLogRepository auditRepo, DbConnectionRepository connectionRepo,
                               DynamicDataSourceManager dataSourceManager, ConsoleProperties consoleProperties) {
        this.auditRepo = auditRepo;
        this.connectionRepo = connectionRepo;
        this.dataSourceManager = dataSourceManager;
        this.consoleProperties = consoleProperties;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        LocalDateTime since = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(24);

        long totalLast24h = auditRepo.countSince(since);
        model.addAttribute("totalLast24h", totalLast24h);

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
        if (totalCount > 0) avgDuration /= totalCount;
        double errorRate = totalCount > 0 ? (double) errorCount / totalCount * 100 : 0;

        model.addAttribute("successCount", successCount);
        model.addAttribute("errorCount", errorCount);
        model.addAttribute("blockedCount", blockedCount);
        model.addAttribute("errorRate", String.format("%.1f", errorRate));
        model.addAttribute("avgDuration", String.format("%.0f", avgDuration));

        List<Object[]> topSql = auditRepo.topSql(5);
        model.addAttribute("topSql", topSql);

        List<SqlAuditLog> recentLogs = auditRepo.findSince(since);
        Map<Integer, Long> hourlyQps = new HashMap<>();
        for (SqlAuditLog l : recentLogs) {
            int hour = l.getExecutedAt().getHour();
            hourlyQps.merge(hour, 1L, Long::sum);
        }
        model.addAttribute("hourlyQps", hourlyQps);

        List<Map<String, Object>> poolStatuses = new ArrayList<>();
        for (DbConnection c : connectionRepo.findAll()) {
            HikariDataSource ds = dataSourceManager.getPool(c.getId());
            if (ds != null && !ds.isClosed()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", c.getName());
                info.put("active", ds.getHikariPoolMXBean().getActiveConnections());
                info.put("idle", ds.getHikariPoolMXBean().getIdleConnections());
                info.put("total", ds.getHikariPoolMXBean().getTotalConnections());
                info.put("waiting", ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
                poolStatuses.add(info);
            }
        }
        model.addAttribute("poolStatuses", poolStatuses);
        model.addAttribute("noPasswordWarning", !consoleProperties.isPasswordConfigured());

        return "dashboard";
    }
}