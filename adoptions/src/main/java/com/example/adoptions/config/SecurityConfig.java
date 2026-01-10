package com.example.adoptions.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static com.example.adoptions.controller.AdoptionsController.DUMMY_MESSAGES_PATH;

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
                              jwk-set-uri: http://your-auth-server/.well-known/jwks.json
 */
public class SecurityConfig {

    public static final List<String> EXCLUDED_RESOURCES = List.of(
            "/api/adoption/**",
            "/swagger/**",
            "/swagger-ui/**",
            "/actuator/**");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                //.csrf(csrf -> csrf.disable()) // Fixes the 403 on DELETE requests
                // Fixes the 403 on DELETE requests
                .authorizeHttpRequests(auth -> {

                    // 1. Specific rules first to restrict access -> will give 403 if not fulfilled
                    auth.requestMatchers(DUMMY_MESSAGES_PATH).hasAuthority("SCOPE_write");

                    // 2. Then general exclusions
                    EXCLUDED_RESOURCES.forEach(s -> auth.requestMatchers(s).permitAll());

                    // 3. Everything else
                    auth.anyRequest().authenticated();// everything else requires authentication

                });
                // enable when resource server is added in application.yml
                //.oauth2ResourceServer(oauth2 -> oauth2
                //        .jwt(Customizer.withDefaults()) // Konfigurera appen som Resource Server med JWT
                //);

        return http.build();
    }
}
