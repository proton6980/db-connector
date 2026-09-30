package com.feiyu.dbconnector.web;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("dbconnector.console")
public class ConsoleProperties {

    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPasswordConfigured() {
        return password != null && !password.isBlank();
    }
}