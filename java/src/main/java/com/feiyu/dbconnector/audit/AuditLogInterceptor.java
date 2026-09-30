package com.feiyu.dbconnector.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.security.ParamMasker;
import com.feiyu.dbconnector.service.QueryService;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@InterceptorBinding(value = Audited.class)
public class AuditLogInterceptor implements MethodInterceptor<Object, Object> {

    private final AuditEventQueue queue;
    private final ParamMasker masker;
    private final ObjectMapper objectMapper;
    private final PendingRowCount pendingRows;

    public AuditLogInterceptor(AuditEventQueue queue, ParamMasker masker, ObjectMapper objectMapper,
                               PendingRowCount pendingRows) {
        this.queue = queue;
        this.masker = masker;
        this.objectMapper = objectMapper;
        this.pendingRows = pendingRows;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Object[] args = context.getParameterValues();
        Argument<?>[] paramDefs = context.getArguments();
        String tool = context.getMethodName();

        String connectionId = null;
        String sql = null;
        String paramsJson = null;
        long start = System.currentTimeMillis();

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String s) {
                if (i == 0 && connectionId == null) connectionId = s;
                if (paramDefs[i].getAnnotationMetadata().hasAnnotation(AuditSql.class)) sql = s;
            } else if (args[i] instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) m;
                paramsJson = toJson(masker.mask(typed));
            }
        }

        try {
            Object result = context.proceed();
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, "SUCCESS",
                    rowCount(result, pendingRows.drain()),
                    System.currentTimeMillis() - start, null, tool));
            return result;
        } catch (BizException e) {
            pendingRows.drain(); // 正常无值；防御性清理
            String status = e.getCode() == ErrorCode.SQL_REJECTED ? "BLOCKED" : "ERROR";
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, status,
                    null, System.currentTimeMillis() - start, e.toLlmMessage(), tool));
            return e.toLlmMessage();
        } catch (Exception e) {
            pendingRows.drain();
            queue.offer(new AuditEvent(connectionId, accountId(), sql, paramsJson, "ERROR",
                    null, System.currentTimeMillis() - start, String.valueOf(e.getMessage()), tool));
            return "[" + ErrorCode.QUERY_FAILED + "] " + e.getMessage();
        }
    }

    private String accountId() {
        return System.getProperty("user.name", "unknown");
    }

    private Integer rowCount(Object result, Integer pending) {
        if (pending != null) return pending;
        if (result instanceof QueryService.QueryResult q) return q.rowCount();
        if (result instanceof java.util.List<?> l) return l.size();
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
