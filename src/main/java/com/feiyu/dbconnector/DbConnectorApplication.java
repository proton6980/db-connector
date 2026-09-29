package com.feiyu.dbconnector;

import io.micronaut.runtime.Micronaut;

public class DbConnectorApplication {

    static void main(String[] args) {
        Micronaut.build(args)
                .mainClass(DbConnectorApplication.class)
                .banner(false)
                .start();
    }
}