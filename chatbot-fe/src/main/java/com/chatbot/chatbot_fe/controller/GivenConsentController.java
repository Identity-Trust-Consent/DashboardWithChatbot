package com.chatbot.chatbot_fe.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class GivenConsentController {
    private final RestClient client;
    private final String identityBaseUrl;
    private final String clientId;

    public GivenConsentController(
            @Value("${identity.os.base-url}") String identityBaseUrl,
            @Value("${identity.os.client-id}") String clientId) {
        this.identityBaseUrl = identityBaseUrl;
        this.clientId = clientId;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping(value = "/identity/given-consent", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> givenConsent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String token = session == null ? null : (String) session.getAttribute("access_token");
        if (token == null || token.isBlank()) {
            return message(401, "Sign in again to view your consents.");
        }
        String application = (String) session.getAttribute("client_id");
        if (application == null || application.isBlank()) application = clientId;
        try {
            String body = client.get().uri(UriComponentsBuilder.fromUriString(identityBaseUrl)
                    .path("/api/consent/given").queryParam("client_id", application).build().encode().toUri())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(String.class);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) return message(401, "Your session has expired. Sign in again to view your consents.");
            if (exception.getStatusCode().value() == 403) return message(403, "Your identity has no verified consent identifier.");
            return message(503, "Unable to load your consents. Please try again.");
        } catch (Exception exception) {
            return message(503, "Unable to reach Identity OS. Please try again.");
        }
    }

    private ResponseEntity<String> message(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"" + message + "\"}");
    }
}
