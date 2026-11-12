package com.candyshare;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public UserDetailsService userDetailsService(AccountRepository accounts) {
        return email -> {
            Account account = accounts.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
            return User.withUsername(account.getEmail()).password(account.getPasswordHash()).roles("USER").build();
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String[] origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "X-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json, AccountService accounts) throws Exception {
        http.cors().and()
                .authorizeRequests()
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .antMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/listings").permitAll()
                .regexMatchers(HttpMethod.GET, "/api/listings/[0-9]+").permitAll()
                .antMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .anyRequest().authenticated().and()
                .formLogin().loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), new ApiViews.UserView(accounts.current(authentication)));
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), new ApiError(401, "Unauthorized", "Invalid email or password.", null));
                }).and()
                .logout().logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), Collections.singletonMap("message", "Signed out."));
                }).and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), new ApiError(401, "Unauthorized", "Please sign in.", null));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), new ApiError(403, "Forbidden", "Request rejected. Refresh and try again.", null));
                });
        http.requestCache().disable();
        return http.build();
    }
}
