package com.feiyu.dbconnector.dm;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.mcp.ExecuteTools;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.security.AesCredentialCipher;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 完整应用链路（ExecuteTools → QueryService → Hikari → DM）冒烟，
 * 重点覆盖 Hikari 代理下 Statement/Connection 的 unwrap 取计划，H2 测试覆盖不到。
 * 仅 DM_URL 设置时运行。
 */
@MicronautTest
@EnabledIfEnvironmentVariable(named = "DM_URL", matches = ".+")
class DmToolsSmokeTest {

    @Inject private ExecuteTools executeTools;
    @Inject private DbConnectionRepository connectionRepository;
    @Inject private AesCredentialCipher cipher;

    @Test
    void fullToolChain() {
        String connId = "dm-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        String table = "DM_SMOKE_" + System.nanoTime();

        DbConnection c = new DbConnection();
        c.setId(connId);
        c.setName(connId);
        c.setDbType("DM");
        c.setHost("localhost");
        c.setPort(5236);
        c.setUsername(System.getenv().getOrDefault("DM_USER", "SYSDBA"));
        c.setPassword(cipher.encrypt(System.getenv().getOrDefault("DM_PASSWORD", "SYSDBA001")));
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

            String inserted = executeTools.execute_dml(connId,
                    "INSERT INTO " + table + " VALUES (1, 'alice')", null, null);
            assertTrue(inserted.contains("影响行数: 1"), inserted);

            String updated = executeTools.execute_dml(connId,
                    "UPDATE " + table + " SET NAME = :n WHERE ID = :id",
                    Map.of("n", "bob", "id", 1), null);
            assertTrue(updated.contains("影响行数: 1"), updated);

            String plan = executeTools.explain_sql(connId,
                    "SELECT * FROM " + table + " WHERE ID = :id", Map.of("id", 1));
            assertTrue(plan.contains("PLAN"), plan);
            assertTrue(plan.contains("NSET2"), plan);

            String deleted = executeTools.execute_dml(connId,
                    "DELETE FROM " + table + " WHERE ID = 1", null, null);
            assertTrue(deleted.contains("影响行数: 1"), deleted);

            String dropped = executeTools.execute_ddl(connId, "DROP TABLE " + table);
            assertTrue(dropped.contains("DDL 执行成功"), dropped);
        } finally {
            connectionRepository.deleteById(connId);
        }
    }
}
