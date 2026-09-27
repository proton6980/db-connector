package com.feiyu.dbconnector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** dev 种子连接配置：YAML 明文 → 启动时加密入库（已存在同名连接则跳过）。 */
@ConfigurationProperties("dbconnector.seed")
public record ConnectionSeedProperties(List<Spec> connections) {

    public record Spec(String name, String dbType, String host, int port,
                       String username, String password, String databaseName, String extraParams) {}
}
