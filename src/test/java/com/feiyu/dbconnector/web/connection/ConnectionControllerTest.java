package com.feiyu.dbconnector.web.connection;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConnectionController.class)
class ConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectionService connectionService;

    @Test
    void listRendersConnections() throws Exception {
        when(connectionService.listAll()).thenReturn(List.of());
        mockMvc.perform(get("/connections"))
                .andExpect(status().isOk())
                .andExpect(view().name("connections/list"));
    }

    @Test
    void listWithConnections() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("test-id");
        c.setName("test-conn");
        c.setDbType("DM");
        c.setHost("localhost");
        c.setPort(5236);
        c.setActive(true);
        when(connectionService.listAll()).thenReturn(List.of(c));
        mockMvc.perform(get("/connections"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("connections"));
    }

    @Test
    void newFormRenders() throws Exception {
        mockMvc.perform(get("/connections/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("connections/form"))
                .andExpect(model().attribute("isNew", true));
    }

    @Test
    void editFormRenders() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("test-id");
        c.setName("test-conn");
        c.setDbType("DM");
        c.setHost("localhost");
        c.setPort(5236);
        c.setUsername("SYSDBA");
        c.setActive(true);
        c.setPoolMin(2);
        c.setPoolMax(10);
        when(connectionService.resolve("test-id")).thenReturn(c);
        mockMvc.perform(get("/connections/test-id/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("connections/form"))
                .andExpect(model().attribute("isNew", false));
    }

    @Test
    void createWithMissingPasswordShowsError() throws Exception {
        mockMvc.perform(post("/connections")
                        .param("name", "new-conn")
                        .param("dbType", "DM")
                        .param("host", "localhost")
                        .param("port", "5236")
                        .param("username", "admin")
                        .param("_csrf", "token"))
                .andExpect(status().isOk())
                .andExpect(view().name("connections/form"));
    }

    @Test
    void createSuccess() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("new-id");
        when(connectionService.create(any(ConnectionForm.class))).thenReturn(c);
        mockMvc.perform(post("/connections")
                        .param("name", "new-conn")
                        .param("dbType", "DM")
                        .param("host", "localhost")
                        .param("port", "5236")
                        .param("username", "admin")
                        .param("password", "secret")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
    }

    @Test
    void updateSuccess() throws Exception {
        DbConnection c = new DbConnection();
        c.setId("test-id");
        when(connectionService.update(any(String.class), any(ConnectionForm.class))).thenReturn(c);
        mockMvc.perform(post("/connections/test-id")
                        .param("name", "updated")
                        .param("dbType", "DM")
                        .param("host", "localhost")
                        .param("port", "5236")
                        .param("username", "admin")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
    }

    @Test
    void deleteCallsService() throws Exception {
        mockMvc.perform(post("/connections/test-id/delete")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
        verify(connectionService).delete("test-id");
    }

    @Test
    void reloadCallsService() throws Exception {
        mockMvc.perform(post("/connections/test-id/reload")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
        verify(connectionService).reload("test-id");
    }

    @Test
    void testSuccess() throws Exception {
        when(connectionService.test("test-id"))
                .thenReturn(new ConnectionService.TestResult(true, 50, "连接成功"));
        mockMvc.perform(post("/connections/test-id/test")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
        verify(connectionService).test("test-id");
    }

    @Test
    void testFailure() throws Exception {
        when(connectionService.test("test-id"))
                .thenReturn(new ConnectionService.TestResult(false, 5000, "Connection refused"));
        mockMvc.perform(post("/connections/test-id/test")
                        .param("_csrf", "token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connections"));
    }
}