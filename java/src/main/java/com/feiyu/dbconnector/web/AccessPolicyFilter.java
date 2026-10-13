package com.feiyu.dbconnector.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;

@Singleton
@Filter("/**")
public class AccessPolicyFilter implements HttpServerFilter {

    private final AccessPolicyService policyService;

    public AccessPolicyFilter(AccessPolicyService policyService) {
        this.policyService = policyService;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (policyService.isAllowed(request)) {
            return chain.proceed(request);
        }
        return Flux.just(HttpResponse.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "当前客户端不在访问白名单中")));
    }
}
