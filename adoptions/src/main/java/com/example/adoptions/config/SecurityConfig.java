package com.example.adoptions.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.List;

import static com.example.adoptions.controller.AdoptionsController.ASSISTANT_PATH;
import static com.example.adoptions.controller.AdoptionsController.CLEAR_MESSAGES_PATH;
import static com.example.adoptions.controller.AdoptionsController.DUMMY_MESSAGES_PATH;
import static com.example.adoptions.controller.AdoptionsController.MESSAGES_PATH;

@Configuration
@EnableWebSecurity
/*
  Security configuration for the application.
  If the goal is to have no security at all (not recommended for production),
  you should remove these two dependencies from your pom.xml:
  spring-boot-starter-security
  spring-boot-starter-oauth2-resource-server
  <p>
  Disabling security config only but still having the above jars in the pom.xml leads to 401 on all endpoints.
  <p>
  For the token to be valid at runtime, the resource server code below must be enabled
  and your application.yml must point to a valid JWK Set URI
  or have a public key configured so Spring can verify the token's signature:
          spring:
              security:
                  oauth2:
                      resourceserver:
                          jwt:
                              issuer-uri: http://your-auth-server
 */
public class SecurityConfig {

    private static final Boolean USE_AUTH_SERVER = true;

    public static final List<String> EXCLUDED_RESOURCES = List.of(
            "/swagger/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**",
            "/error");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSecurity httpSecurity = http
                // 0. Fixes the 403 on DELETE requests
                //.csrf(csrf -> csrf.disable())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {

                    // 1. Specific rules first to restrict access -> will give 403 if not fulfilled
                    auth.requestMatchers(DUMMY_MESSAGES_PATH).hasAuthority("SCOPE_write");

                    // 2. Explicitly permit the other adoption endpoints that should be public
                    auth.requestMatchers(ASSISTANT_PATH).permitAll();
                    auth.requestMatchers(MESSAGES_PATH).permitAll();
                    auth.requestMatchers(CLEAR_MESSAGES_PATH).permitAll();

                    // 3. Then general exclusions
                    EXCLUDED_RESOURCES.forEach(s -> auth.requestMatchers(s).permitAll());

                    // 4. Everything else
                    auth.anyRequest().authenticated();// everything else requires authentication

                });

        //if not enabled, security violation gives 403/Unauthorized, means missing or bad login credentials.
        //if enabled, security violation gives 401/Forbidden, means you're identified but lack permission.
        //if JwtAuthenticationToken auth is used in the corresponding endpoint,
        //a JwtDecoderInitializationException is thrown since server URL points nowhere
        if (USE_AUTH_SERVER) httpSecurity
        // enable when auth server for jwt authentication is added in application.yml
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()) // configure app as Resource Server with JWT
                );

        return http.build();
    }

    /**
     * Creates a JwtDecoder with zero clock skew.
     * This makes the token's {@code iat}, {@code nbf}, and {@code exp} timestamps
     * apply exactly, without Spring Security's default 60-second grace period.
     * @return the JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        // Use the explicit JWK endpoint instead of issuer discovery. The authorization
        // server exposes its keys at /oauth2/jwks and may not expose discovery metadata
        // early enough during local startup.
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withClockSkew = new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuerUri),
                new JwtTimestampValidator(Duration.ZERO) // Set skew to 0
        );

        jwtDecoder.setJwtValidator(withClockSkew);
        return jwtDecoder;
    }
}
