package com.feiyu.dbconnector.audit;

public record AuditEvent(String connectionId, String accountId, String sqlText, String params,
                         String status, Integer rowCount, long durationMs, String errorMsg) {
}