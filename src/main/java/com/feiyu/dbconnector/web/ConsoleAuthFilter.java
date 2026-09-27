package com.feiyu.dbconnector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

public class ConsoleAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAuthFilter.class);
    static final String SESSION_AUTH_KEY = "console_authed";
    static final String SESSION_CSRF_KEY = "console_csrf";
    static final String CSRF_FIELD = "_csrf";
    static final Set<String> PUBLIC_PATHS = Set.of("/login", "/css/", "/webjars/");

    private final ConsoleProperties properties;
    private final SecureRandom random = new SecureRandom();

    public ConsoleAuthFilter(ConsoleProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!properties.isPasswordConfigured()) {
            ensureCsrfToken(request.getSession(true));
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_AUTH_KEY) == null) {
            response.sendRedirect("/login");
            return;
        }

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String csrfToken = request.getParameter(CSRF_FIELD);
            String sessionCsrf = (String) session.getAttribute(SESSION_CSRF_KEY);
            if (sessionCsrf == null || !MessageDigest.isEqual(
                    csrfToken == null ? new byte[0] : csrfToken.getBytes(),
                    sessionCsrf.getBytes())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return;
            }
            ensureCsrfToken(session);
        }

        filterChain.doFilter(request, response);
    }

    boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    void ensureCsrfToken(HttpSession session) {
        if (session.getAttribute(SESSION_CSRF_KEY) == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            session.setAttribute(SESSION_CSRF_KEY, HexFormat.of().formatHex(bytes));
        }
    }

    boolean checkPassword(String input) {
        if (!properties.isPasswordConfigured()) {
            return true;
        }
        return MessageDigest.isEqual(
                input.getBytes(),
                properties.getPassword().getBytes());
    }
}