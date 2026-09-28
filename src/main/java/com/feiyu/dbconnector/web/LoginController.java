package com.feiyu.dbconnector.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ConditionalOnWebApplication
public class LoginController {

    private final ConsoleProperties properties;

    public LoginController(ConsoleProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
        ConsoleAuthSupport.ensureCsrfToken(session);
        if (!properties.isPasswordConfigured()) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String password, HttpSession session, RedirectAttributes redirect) {
        ConsoleAuthSupport.ensureCsrfToken(session);
        if (ConsoleAuthSupport.checkPassword(properties, password)) {
            session.setAttribute(ConsoleAuthFilter.SESSION_AUTH_KEY, true);
            return "redirect:/dashboard";
        }
        redirect.addFlashAttribute("loginError", "口令错误");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
