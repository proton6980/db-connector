package com.feiyu.dbconnector.audit;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorBindingDefinitions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Around
@InterceptorBindingDefinitions({
    @InterceptorBinding(value = Audited.class)
})
public @interface Audited {
}