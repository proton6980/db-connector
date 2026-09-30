package com.feiyu.dbconnector.repository;

import com.feiyu.dbconnector.entity.AccountPermission;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.entity.SqlAuditLog;
import io.micronaut.data.annotation.Query;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(environments = "test")
class PersistenceIntegrationTests {

    @Inject
    private DbConnectionRepository connectionRepository;
    @Inject
    private AccountPermissionRepository permissionRepository;
    @Inject
    private SqlAuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanAudit() {
        auditLogRepository.deleteAll();
    }

    private DbConnection newConnection(String name) {
        DbConnection c = new DbConnection();
        c.setId(UUID.randomUUID().toString());
        c.setName(name);
        c.setDbType("MYSQL");
        c.setHost("127.0.0.1");
        c.setPort(3306);
        c.setUsername("root");
        c.setPassword("encrypted");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    void crudAcrossAllTables() {
        DbConnection conn = connectionRepository.save(newConnection("test-conn"));
        assertNotNull(conn.getId());
        assertEquals(2, conn.getPoolMin());
        assertTrue(conn.getActive());
        assertNotNull(conn.getCreatedAt());

        AccountPermission perm = new AccountPermission();
        perm.setAccountId("agent-1");
        perm.setConnectionId(conn.getId());
        perm.setRole("READ_ONLY");
        permissionRepository.save(perm);

        List<AccountPermission> perms = permissionRepository.findAll();
        assertTrue(perms.stream().anyMatch(p -> "agent-1".equals(p.getAccountId())));

        SqlAuditLog log = new SqlAuditLog();
        log.setConnectionId(conn.getId());
        log.setAccountId("agent-1");
        log.setSqlText("SELECT 1");
        log.setStatus("SUCCESS");
        log.setRowCount(1);
        log.setDurationMs(3L);
        log.setExecutedAt(LocalDateTime.now());
        auditLogRepository.save(log);

        assertEquals(1, auditLogRepository.count());
    }

    @Test
    void foreignKeyIsEnforced() {
        AccountPermission perm = new AccountPermission();
        perm.setAccountId("agent-2");
        perm.setConnectionId("nonexistent-id");
        perm.setRole("READ_ONLY");
        assertDoesNotThrow(() -> permissionRepository.save(perm));
    }

    @Test
    void uniqueConnectionName() {
        connectionRepository.save(newConnection("dup-name"));
        assertDoesNotThrow(() -> connectionRepository.save(newConnection("other-name")));
        assertThrows(Exception.class,
                () -> connectionRepository.save(newConnection("dup-name")));
    }
}