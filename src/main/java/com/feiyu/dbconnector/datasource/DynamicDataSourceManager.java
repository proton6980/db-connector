package com.feiyu.dbconnector.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按业务 key 动态创建/复用/销毁 Hikari 连接池，与 Spring 容器主数据源（H2 元数据库）互不影响。
 */
@Component
public class DynamicDataSourceManager {

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public HikariDataSource getOrCreate(String key, DataSourceSpec spec) {
        return pools.computeIfAbsent(key, k -> {
            HikariConfig cfg = new HikariConfig();
            cfg.setPoolName(key);
            cfg.setJdbcUrl(spec.jdbcUrl());
            cfg.setUsername(spec.username());
            cfg.setPassword(spec.password());
            cfg.setMinimumIdle(spec.minIdle());
            cfg.setMaximumPoolSize(spec.maxPoolSize());
            cfg.setConnectionTimeout(5000);
            cfg.setReadOnly(spec.readOnly());
            if (spec.connectionTestQuery() != null) {
                cfg.setConnectionTestQuery(spec.connectionTestQuery());
            }
            return new HikariDataSource(cfg);
        });
    }

    public void close(String key) {
        HikariDataSource ds = pools.remove(key);
        if (ds != null) {
            ds.close();
        }
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
