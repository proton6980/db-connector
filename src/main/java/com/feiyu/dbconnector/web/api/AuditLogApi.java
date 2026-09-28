package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@ConditionalOnWebApplication
public class AuditLogApi {

    private static final int CSV_MAX_ROWS = 10000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter CSV_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SqlAuditLogRepository auditRepo;
    private final DbConnectionRepository connectionRepo;

    public AuditLogApi(SqlAuditLogRepository auditRepo, DbConnectionRepository connectionRepo) {
        this.auditRepo = auditRepo;
        this.connectionRepo = connectionRepo;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String connectionId,
                                    @RequestParam(required = false) String accountId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);
        int pageNum = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<SqlAuditLog> logs = auditRepo.search(
                connectionId, accountId, status, fromTime, toTime,
                PageRequest.of(pageNum, pageSize));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", logs.getContent());
        result.put("page", logs.getNumber());
        result.put("size", logs.getSize());
        result.put("totalElements", logs.getTotalElements());
        result.put("totalPages", logs.getTotalPages());
        return result;
    }

    @GetMapping("/filters")
    public Map<String, Object> filters() {
        List<Map<String, Object>> connections = connectionRepo.findAll().stream()
                .map(this::toFilterConnection)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", connections);
        result.put("accountIds", auditRepo.distinctAccountIds());
        return result;
    }

    @GetMapping("/export")
    public void exportCsv(@RequestParam(required = false) String connectionId,
                          @RequestParam(required = false) String accountId,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          HttpServletResponse response) throws IOException {
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);
        List<SqlAuditLog> logs = auditRepo.search(
                connectionId, accountId, status, fromTime, toTime,
                PageRequest.of(0, CSV_MAX_ROWS)).getContent();

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=audit_log.csv");

        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("id,connection_id,account_id,sql_text,params,status,row_count,duration_ms,error_msg,client_ip,executed_at\n");
        for (SqlAuditLog l : logs) {
            writer.write(csvEscape(String.valueOf(l.getId()))); writer.write(",");
            writer.write(csvEscape(l.getConnectionId())); writer.write(",");
            writer.write(csvEscape(l.getAccountId())); writer.write(",");
            writer.write(csvEscape(l.getSqlText())); writer.write(",");
            writer.write(csvEscape(l.getParams())); writer.write(",");
            writer.write(csvEscape(l.getStatus())); writer.write(",");
            writer.write(csvEscape(l.getRowCount() != null ? String.valueOf(l.getRowCount()) : "")); writer.write(",");
            writer.write(csvEscape(l.getDurationMs() != null ? String.valueOf(l.getDurationMs()) : "")); writer.write(",");
            writer.write(csvEscape(l.getErrorMsg())); writer.write(",");
            writer.write(csvEscape(l.getClientIp())); writer.write(",");
            writer.write(csvEscape(l.getExecutedAt() != null ? l.getExecutedAt().format(CSV_FMT) : ""));
            writer.write("\n");
        }
        if (logs.size() == CSV_MAX_ROWS) {
            writer.write("# 结果已达上限 " + CSV_MAX_ROWS + " 行，请缩小筛选范围\n");
        }
        writer.flush();
    }

    @GetMapping("/{id}")
    public SqlAuditLog detail(@PathVariable Long id) {
        return auditRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.AUDIT_LOG_NOT_FOUND, "日志不存在: " + id));
    }

    private Map<String, Object> toFilterConnection(DbConnection c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("name", c.getName());
        return item;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
