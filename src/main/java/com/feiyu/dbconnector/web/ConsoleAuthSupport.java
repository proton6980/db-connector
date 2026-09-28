package com.feiyu.dbconnector.web;

import jakarta.servlet.http.HttpSession;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Shared console auth helpers: CSRF minting and password compare. */
public final class ConsoleAuthSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ConsoleAuthSupport() {}

    public static void ensureCsrfToken(HttpSession session) {
        if (session.getAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY) == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            session.setAttribute(ConsoleAuthFilter.SESSION_CSRF_KEY, HexFormat.of().formatHex(bytes));
        }
    }

    public static boolean checkPassword(ConsoleProperties properties, String input) {
        if (!properties.isPasswordConfigured()) {
            return true;
        }
        if (input == null) {
            return false;
        }
        return MessageDigest.isEqual(
                input.getBytes(),
                properties.getPassword().getBytes());
    }
}
