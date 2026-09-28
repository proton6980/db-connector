package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.web.ConsoleAuthFilter;
import com.feiyu.dbconnector.web.ConsoleAuthSupport;
import com.feiyu.dbconnector.web.ConsoleProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication
public class AuthApi {

    private final ConsoleProperties properties;

    public AuthApi(ConsoleProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpSession session) {
        String password = body != null ? body.get("password") : null;
        if (!ConsoleAuthSupport.checkPassword(properties, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "口令错误"));
        }
        session.setAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY, true);
        ConsoleAuthSupport.ensureCsrfToken(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("csrf", session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        boolean authenticated = !properties.isPasswordConfigured()
                || Boolean.TRUE.equals(session.getAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", authenticated);
        result.put("passwordConfigured", properties.isPasswordConfigured());
        if (authenticated) {
            ConsoleAuthSupport.ensureCsrfToken(session);
            result.put("csrf", session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY));
        }
        return result;
    }
}
