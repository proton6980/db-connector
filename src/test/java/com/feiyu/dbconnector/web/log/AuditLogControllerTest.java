package com.feiyu.dbconnector.web.log;

import com.feiyu.dbconnector.entity.SqlAuditLog;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlAuditLogRepository auditRepo;

    @MockitoBean
    private DbConnectionRepository connectionRepo;

    private Page<SqlAuditLog> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    }

    @Test
    void listRendersLogs() throws Exception {
        when(auditRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(emptyPage());
        when(auditRepo.distinctAccountIds()).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/list"));
    }

    @Test
    void listWithFilters() throws Exception {
        when(auditRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(emptyPage());
        when(auditRepo.distinctAccountIds()).thenReturn(List.of("alice", "bob"));
        when(connectionRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/logs")
                        .param("connectionId", "conn1")
                        .param("accountId", "alice")
                        .param("status", "ERROR")
                        .param("from", "2026-09-27T00:00")
                        .param("to", "2026-09-27T23:59"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/list"));
    }

    @Test
    void listWithCustomPageSize() throws Exception {
        when(auditRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(emptyPage());
        when(auditRepo.distinctAccountIds()).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/logs").param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void detailRendersLog() throws Exception {
        SqlAuditLog log = new SqlAuditLog();
        log.setId(1L);
        log.setConnectionId("conn1");
        log.setAccountId("user1");
        log.setSqlText("SELECT 1");
        log.setStatus("SUCCESS");
        log.setDurationMs(10L);
        log.setExecutedAt(LocalDateTime.now());
        when(auditRepo.findById(1L)).thenReturn(java.util.Optional.of(log));

        mockMvc.perform(get("/logs/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("logs/detail"))
                .andExpect(model().attributeExists("log"));
    }

    @Test
    void csvExportReturnsCorrectContentType() throws Exception {
        Page<SqlAuditLog> emptyCsvPage = new PageImpl<>(List.of(), PageRequest.of(0, 10000), 0);
        when(auditRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(emptyCsvPage);

        mockMvc.perform(get("/logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=audit_log.csv"));
    }

    @Test
    void csvExportWithFilters() throws Exception {
        Page<SqlAuditLog> emptyCsvPage = new PageImpl<>(List.of(), PageRequest.of(0, 10000), 0);
        when(auditRepo.search(any(), any(), any(), any(), any(), any())).thenReturn(emptyCsvPage);

        mockMvc.perform(get("/logs/export")
                        .param("status", "ERROR"))
                .andExpect(status().isOk());
    }

    @Test
    void csvEscapeHandlesSpecialCharacters() {
        assertEquals("\"a,\"\"b,c\"", AuditLogController.csvEscape("a,\"b,c"));
        assertEquals("simple", AuditLogController.csvEscape("simple"));
        assertEquals("", AuditLogController.csvEscape(null));
        assertEquals("\"line1\nline2\"", AuditLogController.csvEscape("line1\nline2"));
        assertEquals("\"has \"\"quotes\"\"\"", AuditLogController.csvEscape("has \"quotes\""));
    }

    private void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }
}