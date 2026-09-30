package com.feiyu.dbconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class JacksonConfig {

    @Singleton
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}