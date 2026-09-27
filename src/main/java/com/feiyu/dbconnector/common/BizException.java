package com.feiyu.dbconnector.common;

/** 业务异常，携带错误码；由审计切面统一转为 LLM 友好文本后返回。 */
public class BizException extends RuntimeException {

    private final ErrorCode code;

    public BizException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    /** LLM 友好输出：`[SQL_REJECTED] 仅允许单条 SELECT 查询`。 */
    public String toLlmMessage() {
        return "[" + code + "] " + getMessage();
    }
}
