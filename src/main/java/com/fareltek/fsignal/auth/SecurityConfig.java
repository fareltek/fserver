package com.fareltek.fsignal.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        // Public: auth endpoints + static UI
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/", "/index.html", "/favicon.ico").permitAll()
                        .pathMatchers("/health").permitAll()
                        // MANAGER only
                        .pathMatchers(HttpMethod.POST, "/api/events/acknowledge-all").hasRole("MANAGER")
                        .pathMatchers(HttpMethod.POST, "/api/sections").hasRole("MANAGER")
                        .pathMatchers(HttpMethod.PUT, "/api/sections/**").hasRole("MANAGER")
                        .pathMatchers(HttpMethod.DELETE, "/api/sections/**").hasRole("MANAGER")
                        .pathMatchers("/api/users", "/api/users/**").hasRole("MANAGER")
                        .pathMatchers(HttpMethod.PUT, "/api/config/**").hasRole("MANAGER")
                        .pathMatchers(HttpMethod.GET, "/api/config").hasRole("MANAGER")
                        // OPERATOR or MANAGER
                        .pathMatchers(HttpMethod.POST, "/api/events/**").hasAnyRole("OPERATOR", "MANAGER")
                        // Any authenticated user (GUEST, OPERATOR, MANAGER)
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
