package com.feiyu.dbconnector.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.mcp.ExecuteTools;
import com.feiyu.dbconnector.mcp.QueryTools;
import com.feiyu.dbconnector.mcp.SchemaTools;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整应用链路（Execute/Query/Schema Tools → QueryService → Hikari → MySQL）冒烟，
 * 重点验证 MySQL catalog 语义下的元数据接口与 EXPLAIN 结果集路径，H2 测试覆盖不到。
 * 仅 MYSQL_URL 设置时运行，如：
 * MYSQL_URL='jdbc:mysql://localhost:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true'
 * MYSQL_USER=root MYSQL_PASSWORD=***
 */
@MicronautTest(environments = "test")
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".+")
class MysqlToolsSmokeTest {

    @Inject private ExecuteTools executeTools;
    @Inject private QueryTools queryTools;
    @Inject private SchemaTools schemaTools;
    @Inject private DbConnectionRepository connectionRepository;
    @Inject private AesCredentialCipher cipher;
    @Inject private ObjectMapper objectMapper;

    @Test
    void fullToolChain() throws Exception {
        URI uri = URI.create(System.getenv("MYSQL_URL").substring("jdbc:".length()));
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
        String serverUrl = "jdbc:mysql://" + uri.getHost() + ":" + (uri.getPort() > 0 ? uri.getPort() : 3306) + query;
        String database = uri.getPath() != null && uri.getPath().length() > 1
                ? uri.getPath().substring(1) : null;
        // URL 未指定库时自建临时库并在结束后清理
        boolean selfDatabase = database == null;
        if (selfDatabase) {
            database = "mcp_smoke_" + Long.toHexString(System.nanoTime());
            try (java.sql.Connection conn = DriverManager.getConnection(serverUrl,
                    System.getenv().getOrDefault("MYSQL_USER", "root"),
                    System.getenv().getOrDefault("MYSQL_PASSWORD", ""));
                 Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE " + database);
            }
        }

        String connId = "mysql-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String table = "mysql_smoke_" + Long.toHexString(System.nanoTime());

        DbConnection c = new DbConnection();
        c.setId(connId);
        c.setName(connId);
        c.setDbType("MYSQL");
        c.setHost(uri.getHost());
        c.setPort(uri.getPort() > 0 ? uri.getPort() : 3306);
        c.setUsername(System.getenv().getOrDefault("MYSQL_USER", "root"));
        c.setPassword(cipher.encrypt(System.getenv().getOrDefault("MYSQL_PASSWORD", "")));
        c.setDatabaseName(database);
        if (uri.getQuery() != null) {
            Map<String, Object> params = new LinkedHashMap<>();
            for (String kv : uri.getQuery().split("&")) {
                String[] pair = kv.split("=", 2);
                params.put(pair[0], pair.length > 1 ? pair[1] : "");
            }
            c.setExtraParams(objectMapper.writeValueAsString(params));
        }
        c.setAllowDml(true);
        c.setAllowDdl(true);
        LocalDateTime now = LocalDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        connectionRepository.save(c);

        try {
            String created = executeTools.execute_ddl(connId,
                    "CREATE TABLE " + table + " (ID INT PRIMARY KEY, NAME VARCHAR(50))");
            assertTrue(created.contains("DDL 执行成功"), created);

            String tables = schemaTools.list_tables(connId);
            assertTrue(tables.contains(table), tables);

            String describe = schemaTools.describe_table(connId, database + "." + table);
            assertTrue(describe.contains("ID"), describe);
            assertTrue(describe.contains("NAME"), describe);

            String inserted = executeTools.execute_dml(connId,
                    "INSERT INTO " + table + " VALUES (1, 'alice')", null, null);
            assertTrue(inserted.contains("影响行数: 1"), inserted);

            String updated = executeTools.execute_dml(connId,
                    "UPDATE " + table + " SET NAME = :n WHERE ID = :id",
                    Map.of("n", "bob", "id", 1), null);
            assertTrue(updated.contains("影响行数: 1"), updated);

            String queried = queryTools.query_database(connId,
                    "SELECT NAME FROM " + table + " WHERE ID = :id", Map.of("id", 1));
            assertTrue(queried.contains("bob"), queried);

            String plan = executeTools.explain_sql(connId,
                    "SELECT * FROM " + table + " WHERE ID = :id", Map.of("id", 1));
            assertTrue(plan.contains(table), plan);

            String deleted = executeTools.execute_dml(connId,
                    "DELETE FROM " + table + " WHERE ID = 1", null, null);
            assertTrue(deleted.contains("影响行数: 1"), deleted);

            String dropped = executeTools.execute_ddl(connId, "DROP TABLE " + table);
            assertTrue(dropped.contains("DDL 执行成功"), dropped);
        } finally {
            connectionRepository.deleteById(connId);
            if (selfDatabase) {
                try (java.sql.Connection conn = DriverManager.getConnection(serverUrl,
                        System.getenv().getOrDefault("MYSQL_USER", "root"),
                        System.getenv().getOrDefault("MYSQL_PASSWORD", ""));
                     Statement st = conn.createStatement()) {
                    st.execute("DROP DATABASE " + database);
                }
            }
        }
    }
}
