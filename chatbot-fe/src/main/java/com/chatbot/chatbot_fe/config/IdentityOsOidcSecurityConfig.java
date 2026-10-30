package com.chatbot.chatbot_fe.config;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
/** Opt-in standards-based replacement for hosted query-token login. */
@Configuration
@Profile("identityos-oidc")
public class IdentityOsOidcSecurityConfig {
  @Bean SecurityFilterChain oidcSecurity(HttpSecurity http,ClientRegistrationRepository registrations) throws Exception {
    var resolver=new DefaultOAuth2AuthorizationRequestResolver(registrations,"/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    var logout=new OidcClientInitiatedLogoutSuccessHandler(registrations);logout.setPostLogoutRedirectUri("{baseUrl}/");
    return http.authorizeHttpRequests(a->a.requestMatchers("/","/login","/register","/error","/css/**","/js/**","/images/**","/webjars/**").permitAll()
        .requestMatchers("/callback","/callback/**").denyAll().anyRequest().authenticated())
      .oauth2Login(o->o.authorizationEndpoint(a->a.authorizationRequestResolver(resolver))
        .userInfoEndpoint(u->u.oidcUserService(request->{
          var user=new org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService().loadUser(request);
          try {
            org.springframework.web.client.RestClient.create(integrationUrl).get()
              .uri("/api/v1/integration/applications/"+applicationId+"/me")
              .headers(h->h.setBearerAuth(request.getAccessToken().getTokenValue())).retrieve().toBodilessEntity();
          } catch(org.springframework.web.client.RestClientException failure) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                new org.springframework.security.oauth2.core.OAuth2Error("access_denied","Active application membership is required",null));
          }
          return user;
        })).defaultSuccessUrl("/dashboard",true))
      .logout(l->l.logoutUrl("/perform-logout").logoutSuccessHandler(logout).invalidateHttpSession(true).deleteCookies("JSESSIONID"))
      .build(); // CSRF and session fixation protection remain enabled.
  }
  @org.springframework.beans.factory.annotation.Value("${identity.os.integration-url:http://localhost:8081}") private String integrationUrl;
  @org.springframework.beans.factory.annotation.Value("${identity.os.client-id}") private String applicationId;
}
