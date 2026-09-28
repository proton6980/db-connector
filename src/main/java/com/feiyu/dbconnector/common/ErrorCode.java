package com.feiyu.dbconnector.common;

/** P1 错误码集，MCP 输出形如 `[SQL_REJECTED] 仅允许单条 SELECT 查询`。 */
public enum ErrorCode {
    CONNECTION_NOT_FOUND("连接不存在"),
    CONNECTION_INACTIVE("连接已停用"),
    AUDIT_LOG_NOT_FOUND("日志不存在"),
    SQL_REJECTED("SQL 被安全策略拒绝"),
    QUERY_TIMEOUT("查询超时"),
    QUERY_FAILED("查询执行失败"),
    VALIDATION_ERROR("参数校验失败"),
    UNSUPPORTED_DB_TYPE("不支持的数据库类型");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public boolean isNotFound() {
        return this == CONNECTION_NOT_FOUND || this == AUDIT_LOG_NOT_FOUND;
    }
}
