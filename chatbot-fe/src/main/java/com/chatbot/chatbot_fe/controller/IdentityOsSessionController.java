package com.chatbot.chatbot_fe.controller;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("identityos-oidc")
public class IdentityOsSessionController {
  @GetMapping("/api/identityos/csrf") public Map<String,String> csrf(CsrfToken token) {
    return Map.of("headerName",token.getHeaderName(),"parameterName",token.getParameterName(),"token",token.getToken());
  }
}
