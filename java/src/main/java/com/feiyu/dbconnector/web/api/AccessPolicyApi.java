package com.feiyu.dbconnector.web.api;

import com.feiyu.dbconnector.web.AccessPolicyService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
@Controller("/api/access-policy")
public class AccessPolicyApi {

    private final AccessPolicyService policyService;

    public AccessPolicyApi(AccessPolicyService policyService) {
        this.policyService = policyService;
    }

    @Get
    public Map<String, Object> get() {
        return Map.of("allowed", policyService.getEntries());
    }

    @Put
    public Map<String, Object> update(@Body AccessPolicyForm form) {
        return Map.of("allowed", policyService.replace(form.allowed()));
    }

    @Serdeable
    public record AccessPolicyForm(List<String> allowed) {}
}
