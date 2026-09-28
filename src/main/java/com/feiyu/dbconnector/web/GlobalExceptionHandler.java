package com.feiyu.dbconnector.web;

import com.feiyu.dbconnector.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice(basePackages = "com.feiyu.dbconnector.web")
@ConditionalOnWebApplication
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Object handleBiz(BizException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 400);
        mv.addObject("message", ex.toLlmMessage());
        return mv;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(errorBody(message));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 400);
        mv.addObject("message", message);
        return mv;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 400);
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("服务器内部错误"));
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 500);
        mv.addObject("message", "服务器内部错误");
        return mv;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message != null ? message : "未知错误");
        return body;
    }
}
