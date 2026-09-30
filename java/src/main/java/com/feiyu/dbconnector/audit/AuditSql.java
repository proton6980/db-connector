package com.feiyu.dbconnector.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具方法的 SQL 形参，供审计拦截器准确定位 sql_text，
 * 避免按字符串长度猜测而把 name/host/password 误当 SQL 落库。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditSql {
}
