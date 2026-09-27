package com.feiyu.dbconnector.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<ConsoleAuthFilter> consoleAuthFilter(ConsoleProperties properties) {
        ConsoleAuthFilter filter = new ConsoleAuthFilter(properties);
        FilterRegistrationBean<ConsoleAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/dashboard", "/connections/*", "/connections", "/logs/*", "/logs");
        registration.setOrder(1);
        return registration;
    }
}