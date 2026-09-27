package com.feiyu.dbconnector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsoleAuthFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private HttpSession session;

    private ConsoleProperties properties;
    private ConsoleAuthFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ConsoleProperties();
        properties.setPassword("testpass");
        filter = new ConsoleAuthFilter(properties);
    }

    @Test
    void loginPathPassesThrough() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void cssPathPassesThrough() throws Exception {
        when(request.getServletPath()).thenReturn("/css/app.css");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void webjarsPathPassesThrough() throws Exception {
        when(request.getServletPath()).thenReturn("/webjars/bootstrap/css/bootstrap.min.css");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noSessionRedirectsToLogin() throws Exception {
        when(request.getServletPath()).thenReturn("/dashboard");
        when(request.getSession(false)).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        verify(response).sendRedirect("/login");
    }

    @Test
    void unauthenticatedSessionRedirectsToLogin() throws Exception {
        when(request.getServletPath()).thenReturn("/connections");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY)).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        verify(response).sendRedirect("/login");
    }

    @Test
    void authenticatedGetRequestPassesThrough() throws Exception {
        when(request.getServletPath()).thenReturn("/dashboard");
        when(request.getMethod()).thenReturn("GET");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY)).thenReturn(true);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedPostWithValidCsrfPassesThrough() throws Exception {
        when(request.getServletPath()).thenReturn("/connections");
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY)).thenReturn(true);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY)).thenReturn("valid-token");
        when(request.getParameter(ConsoleAuthFilter.CSRF_FIELD)).thenReturn("valid-token");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidCsrfTokenRejected() throws Exception {
        when(request.getServletPath()).thenReturn("/connections");
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY)).thenReturn(true);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY)).thenReturn("valid-token");
        when(request.getParameter(ConsoleAuthFilter.CSRF_FIELD)).thenReturn("wrong-token");
        filter.doFilterInternal(request, response, filterChain);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
    }

    @Test
    void missingCsrfTokenRejected() throws Exception {
        when(request.getServletPath()).thenReturn("/connections");
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY)).thenReturn(true);
        when(session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY)).thenReturn("valid-token");
        when(request.getParameter(ConsoleAuthFilter.CSRF_FIELD)).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
    }

    @Test
    void noPasswordConfiguredAllowsAccess() throws Exception {
        properties.setPassword("");
        filter = new ConsoleAuthFilter(properties);
        when(request.getServletPath()).thenReturn("/dashboard");
        when(request.getSession(true)).thenReturn(session);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void checkPasswordCorrect() {
        assert filter.checkPassword("testpass");
    }

    @Test
    void checkPasswordWrong() {
        assert !filter.checkPassword("wrong");
    }

    @Test
    void checkPasswordNoConfigAlwaysTrue() {
        properties.setPassword("");
        filter = new ConsoleAuthFilter(properties);
        assert filter.checkPassword("anything");
    }

    @Test
    void ensureCsrfTokenCreatesNewToken() {
        filter.ensureCsrfToken(session);
        verify(session).setAttribute(eq(ConsoleAuthFilter.SESSION_CSRF_KEY), any(String.class));
    }

    @Test
    void ensureCsrfTokenSkipsIfExisting() {
        when(session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY)).thenReturn("existing");
        filter.ensureCsrfToken(session);
        verify(session, never()).setAttribute(eq(ConsoleAuthFilter.SESSION_CSRF_KEY), any());
    }

    @Test
    void isPublicPathMatchesLogin() {
        assert filter.isPublicPath("/login");
    }

    @Test
    void isPublicPathMatchesCss() {
        assert filter.isPublicPath("/css/app.css");
    }

    @Test
    void isPublicPathMatchesWebjars() {
        assert filter.isPublicPath("/webjars/bootstrap/css/bootstrap.min.css");
    }

    @Test
    void isPublicPathRejectsDashboard() {
        assert !filter.isPublicPath("/dashboard");
    }
}