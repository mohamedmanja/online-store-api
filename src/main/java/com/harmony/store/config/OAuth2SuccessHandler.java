package com.harmony.store.config;

import com.harmony.store.service.AuthService;
import com.harmony.store.dto.LoginResponse;
import com.harmony.store.model.User;
import com.harmony.store.model.UserRole;
import com.harmony.store.service.UsersService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UsersService usersService;
    private final AuthService  authService;
    private final JwtService   jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId(); // "google" | "facebook"

        String providerId = oauthUser.getAttribute("sub") != null
                ? oauthUser.getAttribute("sub")
                : oauthUser.getAttribute("id");
        String email      = oauthUser.getAttribute("email");
        String name       = oauthUser.getAttribute("name");
        String avatarUrl  = extractAvatar(oauthUser, provider);

        if (email == null) {
            response.sendRedirect(frontendUrl + "/login?error=oauth_no_email");
            return;
        }

        try {
            User user = usersService.findOrCreateOAuthUser(email, name, avatarUrl, provider, providerId);
            LoginResponse result = authService.login(user);

            if (result.isRequires2FA()) {
                // Redirect with pending token as URL param so LoginPage can pick it up
                String params = "tfa_token=" + result.getPendingToken()
                        + "&tfa_methods=" + String.join(",", result.getMethods())
                        + "&tfa_default=" + result.getDefaultMethod()
                        + (result.getHint() != null ? "&tfa_hint=" + result.getHint() : "");
                response.sendRedirect(frontendUrl + "/login?" + params);
            } else {
                Cookie cookie = buildCookie(result.getAccessToken(), (int) (7 * 86400));
                response.addCookie(cookie);
                response.sendRedirect(frontendUrl);
            }
        } catch (Exception e) {
            log.error("OAuth login failed for {}: {}", email, e.getMessage());
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        }
    }

    private String extractAvatar(OAuth2User user, String provider) {
        if ("google".equals(provider)) return user.getAttribute("picture");
        Object pic = user.getAttribute("picture");
        if (pic instanceof Map<?, ?> picMap) {
            Object data = picMap.get("data");
            if (data instanceof Map<?, ?> dataMap) return (String) dataMap.get("url");
        }
        return null;
    }

    public Cookie buildCookie(String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        // cookie.setSecure(true); // enable in production
        return cookie;
    }
}
