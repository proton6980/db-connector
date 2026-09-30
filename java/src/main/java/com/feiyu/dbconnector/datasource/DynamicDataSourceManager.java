package com.feiyu.dbconnector.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
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

    public HikariDataSource getPool(String key) {
        return pools.get(key);
    }

    @PreDestroy
    public void closeAll() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}