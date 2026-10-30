package com.chatbot.chatbot_fe.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class LoginController {
    private final com.chatbot.chatbot_fe.config.LegacyIdentityTokenValidator legacyTokens;
    public LoginController(com.chatbot.chatbot_fe.config.LegacyIdentityTokenValidator legacyTokens) { this.legacyTokens=legacyTokens; }
    @Value("${identity.os.oidc.enabled:false}")
    private boolean oidcEnabled;

    @Value("${identity.os.base-url}")
    private String identityBaseUrl;

    @Value("${identity.os.client-id}")
    private String clientId;

    @Value("${identity.os.redirect-uri}")
    private String redirectUri;

    @Value("${identity.os.login-path}")
    private String loginPath;

    @Value("${identity.os.register-path}")
    private String registerPath;

    @Value("${identity.os.trust-score-path}")
    private String trustScorePath;

    @GetMapping("/")
    public String welcomePage(Model model) {
        addIdentityModel(model);
        return "welcome";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {
        if (oidcEnabled) return "redirect:/oauth2/authorization/identityos";
        return "redirect:" + buildIdentityUrl(loginPath);
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (oidcEnabled) return "redirect:/oauth2/authorization/identityos";
        return "redirect:" + buildIdentityUrl(registerPath);
    }

    @GetMapping("/identity/trust-score")
    public String trustScorePage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String sessionUsername = session == null ? null : (String) session.getAttribute("username");
        String sessionClientId = session == null ? null : (String) session.getAttribute("client_id");
        String username = firstNonBlank(sessionUsername, SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getName());
        String resolvedClientId = firstNonBlank(sessionClientId, clientId);
        String returnUrl = firstNonBlank(request.getHeader("Referer"), buildClientUrl(request, "/"));

        return "redirect:" + UriComponentsBuilder
                .fromUriString(identityBaseUrl)
                .path(trustScorePath)
                .queryParam("client_id", resolvedClientId)
                .queryParam("username", username)
                .queryParam("return_url", returnUrl)
                .build()
                .toUriString();
    }


    @GetMapping("/callback")
    public String callback(
            @RequestParam(value = "access_token", required = false) String accessToken,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "token_type", required = false) String tokenType,
            @RequestParam(value = "client_id", required = false) String callbackClientId,
            @RequestParam(value = "username", required = false) String username,
            HttpServletRequest request,
            Model model) {

        String credential = accessToken;
        if (credential == null) {
            model.addAttribute("errorMsg", "A verified access token is required. Authorization codes use the OIDC callback.");
            addIdentityModel(model);
            return "callback";
        }

        if (callbackClientId != null && !callbackClientId.isBlank() && !clientId.equals(callbackClientId)) {
            model.addAttribute("errorMsg", "Identity OS returned an unexpected client_id.");
            addIdentityModel(model);
            return "callback";
        }

        final org.springframework.security.oauth2.jwt.Jwt verified;
        try { verified=legacyTokens.validate(credential); }
        catch(org.springframework.security.oauth2.jwt.JwtException invalid) {
            model.addAttribute("errorMsg", "Identity OS returned an invalid or expired access token.");
            return "callback";
        }
        String resolvedTokenType = "Bearer";
        String resolvedUsername = firstNonBlank(verified.getClaimAsString("external_username"), verified.getSubject());

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("access_token", accessToken);
        session.setAttribute("accessToken", accessToken);
        session.setAttribute("identity_code", code);
        session.setAttribute("identityCode", code);
        session.setAttribute("token_type", resolvedTokenType);
        session.setAttribute("tokenType", resolvedTokenType);
        session.setAttribute("client_id", callbackClientId);
        session.setAttribute("clientId", callbackClientId);
        session.setAttribute("username", resolvedUsername);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        resolvedUsername,
                        credential,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);

        model.addAttribute("accessToken", accessToken);
        model.addAttribute("code", code);
        model.addAttribute("tokenType", resolvedTokenType);
        model.addAttribute("clientId", callbackClientId);
        model.addAttribute("username", resolvedUsername);
        model.addAttribute("identityBaseUrl", identityBaseUrl);
        return "callback";
    }

    @RequestMapping("/logout")
    public String logout(HttpServletRequest request) {
        // In OIDC mode logout is a CSRF-protected POST /perform-logout handled by Spring.
        if (oidcEnabled) return "logout";
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "logout";
    }

    private void addIdentityModel(Model model) {
        model.addAttribute("identityBaseUrl", identityBaseUrl);
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("loginPath", loginPath);
        model.addAttribute("registerPath", registerPath);
        model.addAttribute("trustScorePath", trustScorePath);
        model.addAttribute("identityLoginUrl", buildIdentityUrl(loginPath));
        model.addAttribute("identityRegisterUrl", buildIdentityUrl(registerPath));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String buildIdentityUrl(String path) {
        return UriComponentsBuilder
                .fromUriString(identityBaseUrl)
                .path(path)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .build()
                .toUriString();
    }

    private String buildClientUrl(HttpServletRequest request, String path) {
        return UriComponentsBuilder
                .newInstance()
                .scheme(request.getScheme())
                .host(request.getServerName())
                .port(request.getServerPort())
                .path(path)
                .build()
                .toUriString();
    }
}
