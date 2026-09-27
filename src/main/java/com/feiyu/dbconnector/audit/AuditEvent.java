package com.feiyu.dbconnector.audit;

/** 一次 tool 调用的审计事件（队列中流转，刷盘时转 SqlAuditLog 落库）。 */
public record AuditEvent(
        String connectionId,
        String accountId,
        String sqlText,
        String params,
        String status,      // SUCCESS / ERROR / BLOCKED
        Integer rowCount,
        long durationMs,
        String errorMsg) {
}
