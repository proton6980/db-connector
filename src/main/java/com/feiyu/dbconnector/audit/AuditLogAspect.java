package com.feiyu.dbconnector.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.security.ParamMasker;
import com.feiyu.dbconnector.service.QueryService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 审计切面：@Audited tool 方法的唯一审计入口。
 * 成功记 SUCCESS；SQL_REJECTED 记 BLOCKED；其余异常记 ERROR。
 * BizException 不上抛，直接返回 LLM 友好文本（`[CODE] message`），避免客户端看到原始堆栈。
 */
@Aspect
@Component
public class AuditLogAspect {

    private final AuditEventQueue queue;
    private final ParamMasker masker;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditEventQueue queue, ParamMasker masker, ObjectMapper objectMapper) {
        this.queue = queue;
        this.masker = masker;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(Audited)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Object[] args = pjp.getArgs();

        String connectionId = null;
        String sql = null;
        String paramsJson = null;
        long start = System.currentTimeMillis();
        for (int i = 0; i < names.length; i++) {
            if (i >= args.length) break;
            switch (names[i]) {
                case "connection", "connectionId" -> connectionId = String.valueOf(args[i]);
                case "sql" -> sql = (String) args[i];
                case "params" -> paramsJson = toJson(masker.mask((Map<String, Object>) args[i]));
                default -> { }
            }
        }

        try {
            Object result = pjp.proceed();
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, "SUCCESS",
                    rowCount(result), System.currentTimeMillis() - start, null));
            return result;
        } catch (BizException e) {
            String status = e.getCode() == ErrorCode.SQL_REJECTED ? "BLOCKED" : "ERROR";
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, status,
                    null, System.currentTimeMillis() - start, e.toLlmMessage()));
            return e.toLlmMessage();
        } catch (Exception e) {
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, "ERROR",
                    null, System.currentTimeMillis() - start, String.valueOf(e.getMessage())));
            return "[" + ErrorCode.QUERY_FAILED + "] " + e.getMessage();
        }
    }

    private String accountId() {
        return System.getProperty("user.name", "unknown");
    }

    private Integer rowCount(Object result) {
        if (result instanceof QueryService.QueryResult q) return q.rowCount();
        if (result instanceof List<?> l) return l.size();
        return null;
    }

    private String toJson(Map<String, Object> params) {
        if (params == null) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{}";
        }
    }
}
