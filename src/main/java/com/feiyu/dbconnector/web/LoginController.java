package com.feiyu.dbconnector.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.net.URI;

@Controller
public class LoginController {

    @Get("/login")
    public HttpResponse<?> login() {
        return HttpResponse.redirect(URI.create("/login.html"));
    }

    @Get("/logout")
    public HttpResponse<?> logout() {
        return HttpResponse.redirect(URI.create("/login.html"));
    }
}