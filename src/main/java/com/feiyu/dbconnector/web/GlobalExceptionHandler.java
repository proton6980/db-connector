package com.feiyu.dbconnector.web;

import com.feiyu.dbconnector.common.BizException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = "com.feiyu.dbconnector.web")
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ModelAndView handleBiz(BizException ex) {
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 400);
        mv.addObject("message", ex.toLlmMessage());
        return mv;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex) {
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 500);
        mv.addObject("message", "服务器内部错误");
        return mv;
    }
}