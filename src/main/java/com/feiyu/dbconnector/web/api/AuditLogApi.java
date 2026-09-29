package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Controller("/api/logs")
public class AuditLogApi {

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final SqlAuditLogRepository auditRepo;
    private final DbConnectionRepository connectionRepo;

    public AuditLogApi(SqlAuditLogRepository auditRepo, DbConnectionRepository connectionRepo) {
        this.auditRepo = auditRepo;
        this.connectionRepo = connectionRepo;
    }

    @Get
    public Map<String, Object> list(
            @QueryValue(defaultValue = "") String connectionId,
            @QueryValue(defaultValue = "") String accountId,
            @QueryValue(defaultValue = "") String status,
            @QueryValue(defaultValue = "") String from,
            @QueryValue(defaultValue = "") String to,
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);

        Page<SqlAuditLog> result = auditRepo.search(
                blankToNull(connectionId), blankToNull(accountId), blankToNull(status),
                fromTime, toTime, Pageable.from(page, safeSize));

        return buildPageResponse(result, page, safeSize);
    }

    @Get("/{id}")
    public SqlAuditLog detail(Long id) {
        return auditRepo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("日志不存在: " + id));
    }

    @Get("/filters")
    public Map<String, Object> filters() {
        List<DbConnection> connections = connectionRepo.findAll();
        List<String> accountIds = auditRepo.distinctAccountIds();
        return Map.of("connections", connections, "accountIds", accountIds);
    }

    @Get("/export")
    public HttpResponse<String> export(
            @QueryValue(defaultValue = "") String connectionId,
            @QueryValue(defaultValue = "") String accountId,
            @QueryValue(defaultValue = "") String status,
            @QueryValue(defaultValue = "") String from,
            @QueryValue(defaultValue = "") String to) {
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);

        Page<SqlAuditLog> result = auditRepo.search(
                blankToNull(connectionId), blankToNull(accountId), blankToNull(status),
                fromTime, toTime, Pageable.from(0, 10000));

        StringBuilder csv = new StringBuilder();
        csv.append("id,connection_id,account_id,sql_text,status,row_count,duration_ms,executed_at\n");
        for (SqlAuditLog log : result.getContent()) {
            csv.append(log.getId()).append(',');
            csv.append(csvEscape(log.getConnectionId())).append(',');
            csv.append(csvEscape(log.getAccountId())).append(',');
            csv.append(csvEscape(log.getSqlText())).append(',');
            csv.append(csvEscape(log.getStatus())).append(',');
            csv.append(log.getRowCount() != null ? log.getRowCount() : "").append(',');
            csv.append(log.getDurationMs() != null ? log.getDurationMs() : "").append(',');
            csv.append(log.getExecutedAt() != null ? log.getExecutedAt().format(DT_FMT) : "").append('\n');
        }

        return HttpResponse.ok(csv.toString())
                .header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=audit_log.csv");
    }

    public static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Map<String, Object> buildPageResponse(Page<SqlAuditLog> result, int page, int size) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalElements", result.getTotalSize());
        resp.put("totalPages", result.getTotalPages());
        return resp;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}