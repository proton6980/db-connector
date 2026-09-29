package com.feiyu.dbconnector.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.net.URI;

@Controller
public class HomeController {

    @Get("/")
    public HttpResponse<?> index() {
        return HttpResponse.redirect(URI.create("/index.html"));
    }
}