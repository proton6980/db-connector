package com.feiyu.dbconnector.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnWebApplication
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
}
