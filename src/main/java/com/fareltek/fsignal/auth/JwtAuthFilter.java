package com.fareltek.fsignal.auth;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);
        if (token != null && jwtUtil.isValid(token)) {
            Claims claims = jwtUtil.parse(token);
            String role  = claims.get("role",  String.class);
            String email = claims.get("email", String.class);
            var auth = new UsernamePasswordAuthenticationToken(
                    email, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }
        return chain.filter(exchange);
    }

    private String extractToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // SSE endpoints cannot set headers — accept token as query param
        return exchange.getRequest().getQueryParams().getFirst("token");
    }
}
