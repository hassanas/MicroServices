package com.vaimo.microservices.gateway.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;



@Configuration
public class SecurityConfig {

    private static final List<String> PROTECTED_BROWSER_PATHS = List.of(
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/kafka-ui",
        "/kafka-ui/**"
    );

    private static final List<String> PUBLIC_BROWSER_PATHS = List.of(
        "/login/**",
        "/oauth2/**",
        "/fallback/**"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher htmlRequestMatcher = new MediaTypeRequestMatcher(org.springframework.http.MediaType.TEXT_HTML);
        AuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak");

        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(PUBLIC_BROWSER_PATHS.toArray(String[]::new)).permitAll()
                .requestMatchers(PROTECTED_BROWSER_PATHS.toArray(String[]::new)).authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(loginEntryPoint, htmlRequestMatcher))
            .oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .build();
    }
}
