package com.feiyu.dbconnector.web.dashboard;

import com.feiyu.dbconnector.datasource.DynamicDataSourceManager;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.repository.DbConnectionRepository;
import com.feiyu.dbconnector.repository.SqlAuditLogRepository;
import com.feiyu.dbconnector.web.ConsoleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SqlAuditLogRepository auditRepo;

    @MockitoBean
    private DbConnectionRepository connectionRepo;

    @MockitoBean
    private DynamicDataSourceManager dataSourceManager;

    @MockitoBean
    private ConsoleProperties consoleProperties;

    @Test
    void dashboardRendersWithNoData() throws Exception {
        when(auditRepo.countSince(any())).thenReturn(0L);
        when(auditRepo.statusStatsSince(any())).thenReturn(List.of());
        when(auditRepo.topSql(anyInt())).thenReturn(List.of());
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("totalLast24h"))
                .andExpect(model().attributeExists("noPasswordWarning"));
    }

    @Test
    void dashboardShowsPasswordWarning() throws Exception {
        when(auditRepo.countSince(any())).thenReturn(0L);
        when(auditRepo.statusStatsSince(any())).thenReturn(List.of());
        when(auditRepo.topSql(anyInt())).thenReturn(List.of());
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());
        when(consoleProperties.isPasswordConfigured()).thenReturn(false);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("noPasswordWarning", true));
    }

    @Test
    void dashboardWithStats() throws Exception {
        when(auditRepo.countSince(any())).thenReturn(100L);
        List<Object[]> statusStats = new ArrayList<>();
        statusStats.add(new Object[]{"SUCCESS", 80L, 15.0});
        statusStats.add(new Object[]{"ERROR", 15L, 200.0});
        statusStats.add(new Object[]{"BLOCKED", 5L, 0.0});
        when(auditRepo.statusStatsSince(any())).thenReturn(statusStats);
        List<Object[]> topSql = new ArrayList<>();
        topSql.add(new Object[]{"SELECT 1", 50L, 10.0});
        when(auditRepo.topSql(anyInt())).thenReturn(topSql);
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of());
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalLast24h", 100L))
                .andExpect(model().attribute("successCount", 80L))
                .andExpect(model().attribute("errorCount", 15L))
                .andExpect(model().attribute("blockedCount", 5L));
    }

    @Test
    void dashboardWithConnections() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("conn1");
        c.setName("test-conn");
        when(auditRepo.countSince(any())).thenReturn(0L);
        when(auditRepo.statusStatsSince(any())).thenReturn(List.of());
        when(auditRepo.topSql(anyInt())).thenReturn(List.of());
        when(auditRepo.findSince(any())).thenReturn(List.of());
        when(connectionRepo.findAll()).thenReturn(List.of(c));
        when(dataSourceManager.getPool("conn1")).thenReturn(null);
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }
}