package com.chatbot.chatbot_fe.config;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIdentityTokenValidatorTests {
    private HttpServer server;
    private RSAKey key;
    private String issuer;
    private LegacyIdentityTokenValidator validator;

    @BeforeEach void startProvider() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("test-key").generate();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/identity-os";
        server.createContext("/realms/identity-os/protocol/openid-connect/certs",exchange->{
            byte[] response=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();validator=new LegacyIdentityTokenValidator(issuer,"app_test");
    }
    @AfterEach void stopProvider(){if(server!=null)server.stop(0);}

    private String token(String application,String tokenIssuer,String client,Instant expiration) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(tokenIssuer).subject("synthetic-subject")
            .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(expiration))
            .claim("typ","Bearer").claim("azp",client).claim("application_id",application)
            .claim("external_username","business-user").build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(),claims);
        jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    @Test void acceptsVerifiedHostedTokenWithApplicationClaim() throws Exception {
        var jwt=validator.validate(token("app_test",issuer,"identity-os-app-users",Instant.now().plusSeconds(300)));
        assertEquals("business-user",jwt.getClaimAsString("external_username"));
    }
    @Test void rejectsTokenWhenProviderHasNoApplicationClaimMapper() throws Exception {
        String token=token(null,issuer,"identity-os-app-users",Instant.now().plusSeconds(300));
        assertThrows(JwtException.class,()->validator.validate(token));
    }
    @Test void rejectsWrongApplicationAndClient() throws Exception {
        String wrongApp=token("app_other",issuer,"identity-os-app-users",Instant.now().plusSeconds(300));
        String wrongClient=token("app_test",issuer,"another-client",Instant.now().plusSeconds(300));
        assertThrows(JwtException.class,()->validator.validate(wrongApp));
        assertThrows(JwtException.class,()->validator.validate(wrongClient));
    }
    @Test void rejectsExpiredTokenAndWrongIssuer() throws Exception {
        String expired=token("app_test",issuer,"identity-os-app-users",Instant.now().minusSeconds(600));
        String wrongIssuer=token("app_test",issuer+"-other","identity-os-app-users",Instant.now().plusSeconds(300));
        assertThrows(JwtException.class,()->validator.validate(expired));
        assertThrows(JwtException.class,()->validator.validate(wrongIssuer));
    }
    @Test void rejectsUnverifiedSignature() throws Exception {
        String token=token("app_test",issuer,"identity-os-app-users",Instant.now().plusSeconds(300));
        var other=new RSAKeyGenerator(2048).keyID(key.getKeyID()).generate();
        var forged=new SignedJWT(SignedJWT.parse(token).getHeader(),SignedJWT.parse(token).getJWTClaimsSet());
        forged.sign(new RSASSASigner(other));
        assertThrows(JwtException.class,()->validator.validate(forged.serialize()));
    }
}
