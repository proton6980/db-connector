package com.feiyu.dbconnector.web;

import com.feiyu.dbconnector.common.BizException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class GlobalErrorHandler implements ExceptionHandler<BizException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, BizException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case CONNECTION_NOT_FOUND, CONNECTION_INACTIVE -> HttpStatus.NOT_FOUND;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case SQL_REJECTED -> HttpStatus.FORBIDDEN;
            case QUERY_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case QUERY_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        return HttpResponse.status(status).body(Map.of("error", exception.getMessage()));
    }
}