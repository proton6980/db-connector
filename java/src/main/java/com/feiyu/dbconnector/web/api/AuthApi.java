package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.web.ConsoleProperties;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
@Controller("/api/auth")
public class AuthApi {

    private final ConsoleProperties consoleProperties;

    public AuthApi(ConsoleProperties consoleProperties) {
        this.consoleProperties = consoleProperties;
    }

    @Post("/login")
    public HttpResponse<?> login(@Body LoginRequest req) {
        if (!consoleProperties.isPasswordConfigured()) {
            String csrf = UUID.randomUUID().toString();
            return HttpResponse.ok(Map.of("ok", true, "csrf", csrf));
        }

        if (consoleProperties.getPassword().equals(req.password())) {
            String csrf = UUID.randomUUID().toString();
            return HttpResponse.ok(Map.of("ok", true, "csrf", csrf));
        }

        return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "口令错误"));
    }

    @Get("/me")
    public Map<String, Object> me() {
        boolean authenticated = !consoleProperties.isPasswordConfigured();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", authenticated);
        result.put("passwordConfigured", consoleProperties.isPasswordConfigured());

        if (authenticated) {
            result.put("csrf", UUID.randomUUID().toString());
        }
        return result;
    }

    @Post("/logout")
    public HttpResponse<?> logout() {
        return HttpResponse.ok(Map.of("ok", true));
    }

    @Serdeable
    public record LoginRequest(String password) {}
}