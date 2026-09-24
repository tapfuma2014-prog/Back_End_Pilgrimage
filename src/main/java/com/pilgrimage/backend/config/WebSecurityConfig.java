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
    private final List<String> allowedOrigins;
    
    public WebSecurityConfig(JwtAuthenticationEntryPoint unauthorizedHandler, 
                           JwtRequestFilter jwtRequestFilter,
                           @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.unauthorizedHandler = unauthorizedHandler;
        this.jwtRequestFilter = jwtRequestFilter;
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
            .authorizeHttpRequests(auth ->
                auth.requestMatchers(HttpMethod.GET,
                        "/artists/**",
                        "/api/artists/**"
                    ).permitAll()
                   .requestMatchers(
                        new AntPathRequestMatcher("/auth/login"),
                        new AntPathRequestMatcher("/auth/register"),
                        new AntPathRequestMatcher("/auth/password/**"),
                        new AntPathRequestMatcher("/api/auth/login"),
                        new AntPathRequestMatcher("/api/auth/register"),
                        new AntPathRequestMatcher("/api/auth/password/**"),
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
                        new AntPathRequestMatcher("/api/apps/**")
                    ).permitAll()
                   .anyRequest().authenticated()
            );
            
        // Add JWT filter before the default authentication filter
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
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
