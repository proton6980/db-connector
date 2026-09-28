package com.feiyu.dbconnector.web.log;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@ConditionalOnWebApplication
public class AuditLogController {

    private static final int CSV_MAX_ROWS = 10000;
    private static final DateTimeFormatter CSV_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SqlAuditLogRepository auditRepo;
    private final DbConnectionRepository connectionRepo;

    public AuditLogController(SqlAuditLogRepository auditRepo, DbConnectionRepository connectionRepo) {
        this.auditRepo = auditRepo;
        this.connectionRepo = connectionRepo;
    }

    @GetMapping("/logs")
    public String list(@RequestParam(required = false) String connectionId,
                       @RequestParam(required = false) String accountId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        LocalDateTime fromTime = parseDateTime(from);
        LocalDateTime toTime = parseDateTime(to);
        Page<SqlAuditLog> logs = auditRepo.search(
                connectionId, accountId, status, fromTime, toTime,
                PageRequest.of(page, size));

        model.addAttribute("logs", logs);
        model.addAttribute("connections", connectionRepo.findAll());
        model.addAttribute("accountIds", auditRepo.distinctAccountIds());
        model.addAttribute("connectionId", connectionId);
        model.addAttribute("accountId", accountId);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("paginationPath", "/logs");
        return "logs/list";
    }

    @GetMapping("/logs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        SqlAuditLog log = auditRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.AUDIT_LOG_NOT_FOUND, "日志不存在: " + id));
        model.addAttribute("log", log);
        return "logs/detail";
    }

    @GetMapping("/logs/export")
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

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(",") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}