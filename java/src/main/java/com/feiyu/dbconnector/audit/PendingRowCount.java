package com.feiyu.dbconnector.audit;

import jakarta.inject.Singleton;

/**
 * 工具方法返回的是给 LLM 的文本，审计拦截器无法从返回值拿到 DML 影响行数。
 * ExecuteTools 成功后在此暂存，拦截器 proceed 返回后读取（同一调用线程）。
 */
@Singleton
public class PendingRowCount {

    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();

    public void set(int rowCount) {
        HOLDER.set(rowCount);
    }

    public Integer drain() {
        Integer v = HOLDER.get();
        HOLDER.remove();
        return v;
    }
}
