package com.chatbot.chatbot_fe.service;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * UserInfoService — returns the currently logged-in username.
 * Spring Security reads it from the SecurityContext.
 * If Security is disabled (permitAll), returns "default_user".
 */
@Service
public class UserInfoService {

    /**
     * Returns the username of the currently authenticated user.
     * Used as userId in dashboard config, todo, favorites, etc.
     */
    public String getUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) {
                    String external = oidc.getClaimAsString("external_username");
                    if (external != null && !external.isBlank()) return external;
                    return oidc.getPreferredUsername() == null ? oidc.getSubject() : oidc.getPreferredUsername();
                }
                return auth.getName();
            }
        } catch (Exception e) {
            // Security not configured — fall through to default
        }
        return "default_user";
    }
}
