package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.web.ConsoleAuthFilter;
import com.feiyu.dbconnector.web.ConsoleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthApi.class)
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsoleProperties consoleProperties;

    @Test
    void loginSuccessReturnsCsrf() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);
        when(consoleProperties.getPassword()).thenReturn("secret");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.csrf").isString());
    }

    @Test
    void loginFailureReturns401() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);
        when(consoleProperties.getPassword()).thenReturn("secret");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("口令错误"));
    }

    @Test
    void meUnauthenticated() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.passwordConfigured").value(true))
                .andExpect(jsonPath("$.csrf").doesNotExist());
    }

    @Test
    void meAuthenticated() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY, true);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.csrf").isString());
    }

    @Test
    void meAuthenticatedReusesExistingCsrf() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY, true);
        session.setAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY, "existing-csrf-token");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.csrf").value("existing-csrf-token"));
    }

    @Test
    void meWhenNoPasswordConfigured() throws Exception {
        when(consoleProperties.isPasswordConfigured()).thenReturn(false);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.passwordConfigured").value(false))
                .andExpect(jsonPath("$.csrf").isString());
    }

    @Test
    void logoutReturnsOk() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY, true);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
