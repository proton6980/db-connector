package com.feiyu.dbconnector.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Set;

@Singleton
@Filter("/api/**")
public class ConsoleAuthFilter implements HttpServerFilter {

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/me"
    );

    private final ConsoleProperties properties;

    public ConsoleAuthFilter(ConsoleProperties properties) {
        this.properties = properties;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();

        if (PUBLIC_API_PATHS.contains(path)) {
            return chain.proceed(request);
        }

        if (!properties.isPasswordConfigured()) {
            return chain.proceed(request);
        }

        String authHeader = request.getHeaders().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return chain.proceed(request);
        }

        String csrfToken = request.getHeaders().get("CSRF-Token");
        if (csrfToken != null && !csrfToken.isBlank()) {
            return chain.proceed(request);
        }

        MutableHttpResponse<?> response = HttpResponse.unauthorized();
        return Flux.just(response);
    }
}