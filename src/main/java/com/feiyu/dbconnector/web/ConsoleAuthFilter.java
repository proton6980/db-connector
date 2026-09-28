package com.feiyu.dbconnector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Set;

public class ConsoleAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_AUTH_KEY = "console_authed";
    public static final String SESSION_CSRF_KEY = "console_csrf";
    public static final String CSRF_HEADER = "X-CSRF-TOKEN";
    static final String CSRF_FIELD = "_csrf";
    static final Set<String> PUBLIC_PREFIXES = Set.of("/login", "/css/", "/js/", "/webjars/");
    static final Set<String> PUBLIC_EXACT = Set.of(
            "/index.html", "/login.html", "/api/auth/login", "/api/auth/me");

    private final ConsoleProperties properties;

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
            ConsoleAuthSupport.ensureCsrfToken(request.getSession(true));
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_AUTH_KEY) == null) {
            if (isApiPath(path)) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录");
            } else {
                response.sendRedirect("/login");
            }
            return;
        }

        if (isMutatingMethod(request.getMethod())) {
            String csrfToken = request.getHeader(CSRF_HEADER);
            if (csrfToken == null) {
                csrfToken = request.getParameter(CSRF_FIELD);
            }
            String sessionCsrf = (String) session.getAttribute(SESSION_CSRF_KEY);
            if (sessionCsrf == null || !MessageDigest.isEqual(
                    csrfToken == null ? new byte[0] : csrfToken.getBytes(),
                    sessionCsrf.getBytes())) {
                if (isApiPath(path)) {
                    writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                } else {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                }
                return;
            }
            ConsoleAuthSupport.ensureCsrfToken(session);
        }

        filterChain.doFilter(request, response);
    }

    boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        if (PUBLIC_EXACT.contains(path)) {
            return true;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    boolean isApiPath(String path) {
        return path != null && path.startsWith("/api/");
    }

    boolean isMutatingMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    void ensureCsrfToken(HttpSession session) {
        ConsoleAuthSupport.ensureCsrfToken(session);
    }

    boolean checkPassword(String input) {
        return ConsoleAuthSupport.checkPassword(properties, input);
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
