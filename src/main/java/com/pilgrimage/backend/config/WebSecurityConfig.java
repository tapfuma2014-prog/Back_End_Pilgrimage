package com.pilgrimage.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    
    private final JwtAuthenticationEntryPoint unauthorizedHandler;
    private final JwtRequestFilter jwtRequestFilter;
    private final XperienceAuthFilter xperienceAuthFilter;
    private final List<String> allowedOrigins;
    
    public WebSecurityConfig(JwtAuthenticationEntryPoint unauthorizedHandler, 
                           JwtRequestFilter jwtRequestFilter,
                           XperienceAuthFilter xperienceAuthFilter,
                           @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtRequestFilter = jwtRequestFilter;
        this.xperienceAuthFilter = xperienceAuthFilter;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            // CSRF is intentionally disabled: this API is stateless (SessionCreationPolicy.STATELESS)
            // and authenticates via Bearer JWTs in the Authorization header, not cookies, so there is
            // no ambient browser credential for CSRF to exploit.
            .csrf().disable()
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Security headers for the JSON API. CSP is document-agnostic ('none')
            // since this service serves API responses, not HTML pages.
            .headers(headers -> headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy("default-src 'none'; frame-ancestors 'none'").and()
                .permissionsPolicy(permissions -> permissions.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()"))
            )
            .authorizeHttpRequests(auth ->
                auth.requestMatchers(HttpMethod.GET,
                        "/artists/**",
                        "/api/artists/**",
                        // Public catalogue reads — the SPA renders events/auctions
                        // pages for anonymous visitors.
                        "/events/**",
                        "/api/events/**",
                        "/auctions/**",
                        "/api/auctions/**"
                    ).permitAll()
                   .requestMatchers(
                        new AntPathRequestMatcher("/auth/login"),
                        new AntPathRequestMatcher("/auth/register"),
                        new AntPathRequestMatcher("/auth/password/**"),
                        new AntPathRequestMatcher("/api/auth/login"),
                        new AntPathRequestMatcher("/api/auth/register"),
                        new AntPathRequestMatcher("/api/auth/password/**"),
                        // The error dispatch must be reachable anonymously: with
                        // authentication required, any controller error (403/404/500)
                        // is re-authorized on the /error dispatch and masked as a
                        // bare 401, which the SPA misreads as an expired session.
                        new AntPathRequestMatcher("/error"),
                        new AntPathRequestMatcher("/search/**"),
                        new AntPathRequestMatcher("/artworks/**"),
                        new AntPathRequestMatcher("/api/search/**"),
                        new AntPathRequestMatcher("/api/artworks/**"),
                        // Entity routes enforce their own per-entity authorization inside
                        // EntityController (public catalogue reads vs owner/admin writes).
                        new AntPathRequestMatcher("/entities/**"),
                        new AntPathRequestMatcher("/api/entities/**"),
                        // Public integration endpoints are open to all users.
                        new AntPathRequestMatcher("/integrations/llm"),
                        new AntPathRequestMatcher("/integrations/contact"),
                        new AntPathRequestMatcher("/integrations/upload"),
                        new AntPathRequestMatcher("/integrations/uploads/**"),
                        new AntPathRequestMatcher("/integrations/generate-image/preview"),
                        new AntPathRequestMatcher("/api/integrations/llm"),
                        new AntPathRequestMatcher("/api/integrations/contact"),
                        new AntPathRequestMatcher("/api/integrations/upload"),
                        new AntPathRequestMatcher("/api/integrations/uploads/**"),
                        new AntPathRequestMatcher("/api/integrations/generate-image/preview"),
                        // Base44 compat routes enforce their own auth inside the controllers
                        new AntPathRequestMatcher("/apps/**"),
                        new AntPathRequestMatcher("/api/apps/**"),
                        // Xperience endpoints use custom auth via XperienceAuthFilter
                        new AntPathRequestMatcher("/xperience/**"),
                        new AntPathRequestMatcher("/api/xperience/**")
                    ).permitAll()
                   .anyRequest().authenticated()
            );
            
        // Add JWT filter before the default authentication filter
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        
        // Add Xperience auth filter before JWT filter for /xperience endpoints
        http.addFilterBefore(xperienceAuthFilter, JwtRequestFilter.class);
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin", "X-App-Secret", "X-Xperience-Signature"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
