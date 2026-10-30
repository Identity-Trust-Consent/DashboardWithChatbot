package com.chatbot.chatbot_fe.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
/** Compatibility callback accepts only a verified app-bound access token; never arbitrary code strings. */
@Component
public class LegacyIdentityTokenValidator {
  private final JwtDecoder decoder;
  private final String app;
  public LegacyIdentityTokenValidator(@Value("${IDENTITY_OS_ISSUER:http://localhost:8085/realms/identity-os}") String issuer,
      @Value("${identity.os.client-id}") String app) {
    var decoder=NimbusJwtDecoder.withJwkSetUri(issuer+"/protocol/openid-connect/certs").build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));this.decoder=decoder;this.app=app;
  }
  public Jwt validate(String token) {
    Jwt jwt=decoder.decode(token);
    if(jwt.getSubject()==null || jwt.getExpiresAt()==null || !"Bearer".equals(jwt.getClaimAsString("typ"))
        || !"identity-os-app-users".equals(jwt.getClaimAsString("azp"))
        || !app.equals(jwt.getClaimAsString("application_id"))) throw new JwtException("Unexpected callback token");
    return jwt;
  }
}
