package com.harmony.store.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtils {
    private CookieUtils() {}

    public static void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 3600); // 7 days
        // cookie.setSecure(true); // enable in production
        response.addCookie(cookie);
    }

    public static void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("access_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
