package com.feiyu.dbconnector.kingbase;

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
 * 完整应用链路（Execute/Query/Schema Tools → QueryService → Hikari → Kingbase）冒烟，
 * 验证 KADB（Greenplum/PostgreSQL 系）schema 语义下的元数据接口与 EXPLAIN 结果集路径，H2 测试覆盖不到。
 * 仅 KINGBASE_URL 设置时运行，如：
 * KINGBASE_URL='jdbc:kingbase8://localhost:5432/testdb'
 * KINGBASE_USER=system KINGBASE_PASSWORD=***
 */
@MicronautTest(environments = "test")
@EnabledIfEnvironmentVariable(named = "KINGBASE_URL", matches = ".+")
class KingbaseToolsSmokeTest {

    @Inject private ExecuteTools executeTools;
    @Inject private QueryTools queryTools;
    @Inject private SchemaTools schemaTools;
    @Inject private DbConnectionRepository connectionRepository;
    @Inject private AesCredentialCipher cipher;
    @Inject private ObjectMapper objectMapper;

    @Test
    void fullToolChain() throws Exception {
        URI uri = URI.create(System.getenv("KINGBASE_URL").substring("jdbc:".length()));
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
        String serverUrl = "jdbc:kingbase8://" + uri.getHost() + ":" + (uri.getPort() > 0 ? uri.getPort() : 5432) + query;
        String database = uri.getPath() != null && uri.getPath().length() > 1
                ? uri.getPath().substring(1) : null;
        // URL 未指定库时自建临时库并在结束后清理
        boolean selfDatabase = database == null;
        if (selfDatabase) {
            database = "mcp_smoke_" + Long.toHexString(System.nanoTime());
            try (java.sql.Connection conn = DriverManager.getConnection(serverUrl,
                    System.getenv().getOrDefault("KINGBASE_USER", "system"),
                    System.getenv().getOrDefault("KINGBASE_PASSWORD", ""));
                 Statement st = conn.createStatement()) {
                st.execute("CREATE DATABASE " + database);
            }
        }

        String connId = "kingbase-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String table = "kingbase_smoke_" + Long.toHexString(System.nanoTime());

        DbConnection c = new DbConnection();
        c.setId(connId);
        c.setName(connId);
        c.setDbType("KINGBASE");
        c.setHost(uri.getHost());
        c.setPort(uri.getPort() > 0 ? uri.getPort() : 5432);
        c.setUsername(System.getenv().getOrDefault("KINGBASE_USER", "system"));
        c.setPassword(cipher.encrypt(System.getenv().getOrDefault("KINGBASE_PASSWORD", "")));
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

            // KADB 前缀是 schema；新建表默认落在 public
            String describe = schemaTools.describe_table(connId, "public." + table);
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
                        System.getenv().getOrDefault("KINGBASE_USER", "system"),
                        System.getenv().getOrDefault("KINGBASE_PASSWORD", ""));
                     Statement st = conn.createStatement()) {
                    st.execute("DROP DATABASE " + database);
                }
            }
        }
    }
}
