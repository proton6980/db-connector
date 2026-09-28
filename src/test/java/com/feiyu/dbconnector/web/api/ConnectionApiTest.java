package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.common.BizException;
import com.feiyu.dbconnector.common.ErrorCode;
import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import com.feiyu.dbconnector.web.connection.ConnectionForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectionApi.class)
class ConnectionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectionService connectionService;

    private DbConnection sample() {
        DbConnection c = new DbConnection();
        c.setId("test-id");
        c.setName("test-conn");
        c.setDbType("DM");
        c.setHost("localhost");
        c.setPort(5236);
        c.setUsername("SYSDBA");
        c.setPassword("secret");
        c.setActive(true);
        c.setPoolMin(2);
        c.setPoolMax(10);
        return c;
    }

    @Test
    void listReturnsConnectionsWithoutPassword() throws Exception {
        when(connectionService.listAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("test-id"))
                .andExpect(jsonPath("$[0].name").value("test-conn"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void getByIdOmitsPassword() throws Exception {
        when(connectionService.getById("test-id")).thenReturn(sample());

        mockMvc.perform(get("/api/connections/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createSuccess() throws Exception {
        when(connectionService.create(any(ConnectionForm.class))).thenReturn(sample());

        mockMvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test-conn","dbType":"DM","host":"localhost",
                                 "port":5236,"username":"admin","password":"secret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createWithoutPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test-conn","dbType":"DM","host":"localhost",
                                 "port":5236,"username":"admin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("密码不能为空"));
    }

    @Test
    void updateSuccess() throws Exception {
        when(connectionService.update(eq("test-id"), any(ConnectionForm.class))).thenReturn(sample());

        mockMvc.perform(put("/api/connections/test-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"test-conn","dbType":"DM","host":"localhost",
                                 "port":5236,"username":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"));
    }

    @Test
    void testConnection() throws Exception {
        when(connectionService.test("test-id"))
                .thenReturn(new ConnectionService.TestResult(true, 50, "连接成功"));

        mockMvc.perform(post("/api/connections/test-id/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.durationMs").value(50))
                .andExpect(jsonPath("$.message").value("连接成功"));
    }

    @Test
    void reloadConnection() throws Exception {
        mockMvc.perform(post("/api/connections/test-id/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        verify(connectionService).reload("test-id");
    }

    @Test
    void deleteConnection() throws Exception {
        mockMvc.perform(delete("/api/connections/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        verify(connectionService).delete("test-id");
    }

    @Test
    void getMissingReturnsJsonError() throws Exception {
        when(connectionService.getById("missing"))
                .thenThrow(new BizException(ErrorCode.CONNECTION_NOT_FOUND, "连接不存在: missing"));

        mockMvc.perform(get("/api/connections/missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("连接不存在: missing"));
    }
}
