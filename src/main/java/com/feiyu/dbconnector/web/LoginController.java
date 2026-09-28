package com.feiyu.dbconnector.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Controller
@ConditionalOnWebApplication
public class LoginController {

    private final ConsoleProperties properties;
    private final SecureRandom random = new SecureRandom();

    public LoginController(ConsoleProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
        if (!properties.isPasswordConfigured()) {
            ensureCsrfToken(session);
            return "redirect:/dashboard";
        }
        ensureCsrfToken(session);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String password, HttpSession session, RedirectAttributes redirect) {
        ensureCsrfToken(session);
        if (checkPassword(password)) {
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

    private void ensureCsrfToken(HttpSession session) {
        if (session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY) == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            session.setAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY, HexFormat.of().formatHex(bytes));
        }
    }

    private boolean checkPassword(String input) {
        if (!properties.isPasswordConfigured()) {
            return true;
        }
        return MessageDigest.isEqual(
                input.getBytes(),
                properties.getPassword().getBytes());
    }
}