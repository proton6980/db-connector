package com.feiyu.dbconnector.audit;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 每秒批量刷盘队列中的审计事件。 */
@Component
public class AuditLogFlusher {

    private static final Logger log = LoggerFactory.getLogger(AuditLogFlusher.class);

    private final AuditEventQueue queue;
    private final SqlAuditLogRepository repository;

    public AuditLogFlusher(AuditEventQueue queue, SqlAuditLogRepository repository) {
        this.queue = queue;
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        List<AuditEvent> events = queue.drain();
        if (events.isEmpty()) {
            return;
        }
        long dropped = queue.droppedCount();
        if (dropped > 0) {
            log.warn("审计队列已丢弃 {} 条事件（不反压查询路径）", dropped);
        }
        repository.saveAll(events.stream().map(e -> {
            SqlAuditLog l = new SqlAuditLog();
            l.setConnectionId(e.connectionId());
            l.setAccountId(e.accountId());
            l.setSqlText(e.sqlText() == null ? "" : e.sqlText());
            l.setParams(e.params());
            l.setStatus(e.status());
            l.setRowCount(e.rowCount());
            l.setDurationMs(e.durationMs());
            l.setErrorMsg(e.errorMsg());
            return l;
        }).toList());
    }
}
