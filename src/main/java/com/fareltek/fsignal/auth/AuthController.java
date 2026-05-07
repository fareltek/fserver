package com.fareltek.fsignal.auth;

import com.fareltek.fsignal.db.SafetyEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserService     userService;
    private final SafetyEventService eventService;

    public AuthController(AppUserService userService, SafetyEventService eventService) {
        this.userService  = userService;
        this.eventService = eventService;
    }

    @PostMapping("/register")
    public Mono<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (req.fullName() == null || req.fullName().isBlank())
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad soyad zorunludur."));
        if (req.email() == null || !req.email().contains("@"))
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz e-posta."));
        if (req.password() == null || req.password().length() < 8)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şifre en az 8 karakter olmalı."));

        String role = (req.role() != null) ? req.role().toUpperCase() : "GUEST";
        return userService.register(req.fullName().trim(), req.email().toLowerCase().trim(), req.password(), role)
                .map(u -> Map.<String, Object>of(
                        "message", "Kayıt başarılı.",
                        "email",   u.getEmail(),
                        "role",    u.getRole()))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()));
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
        if (req.email() == null || req.password() == null)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-posta ve şifre zorunludur."));

        String email     = req.email().toLowerCase().trim();
        String ip        = extractIp(exchange.getRequest());
        String userAgent = parseUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        String clientInfo = "IP: " + ip + " | " + userAgent;

        return userService.login(email, req.password())
                .flatMap(token -> eventService.saveSystemEvent("SYSTEM", "INFO",
                        "Giriş başarılı: " + email + " | " + clientInfo)
                        .thenReturn(Map.<String, Object>of("token", token)))
                .onErrorResume(IllegalArgumentException.class, e ->
                        eventService.saveSystemEvent("SYSTEM", "WARNING",
                                "Başarısız giriş: " + email + " (şifre hatalı) | " + clientInfo)
                                .then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()))))
                .onErrorResume(IllegalStateException.class, e ->
                        eventService.saveSystemEvent("SYSTEM", "WARNING",
                                "Başarısız giriş: " + email + " (" + e.getMessage() + ") | " + clientInfo)
                                .then(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage()))));
    }

    @GetMapping("/me")
    public Mono<Map<String, Object>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return userService.findByEmail(auth.getName())
                .map(u -> Map.<String, Object>of(
                        "id",       u.getId().toString(),
                        "fullName", u.getFullName(),
                        "email",    u.getEmail(),
                        "role",     u.getRole()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    /** Returns client IP, checking Coolify/nginx reverse-proxy headers first. */
    private String extractIp(ServerHttpRequest req) {
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return req.getRemoteAddress() != null
                ? req.getRemoteAddress().getAddress().getHostAddress()
                : "bilinmiyor";
    }

    /** Extracts readable browser + OS summary from raw User-Agent string. */
    private String parseUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Tarayıcı: bilinmiyor";
        String os  = "?";
        String browser = "?";
        if (ua.contains("Windows NT 10"))   os = "Windows 10/11";
        else if (ua.contains("Windows NT")) os = "Windows";
        else if (ua.contains("Mac OS X"))   os = "macOS";
        else if (ua.contains("Android"))    os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";
        else if (ua.contains("Linux"))      os = "Linux";

        if (ua.contains("Edg/"))            browser = "Edge";
        else if (ua.contains("OPR/") || ua.contains("Opera")) browser = "Opera";
        else if (ua.contains("Chrome/"))    browser = "Chrome";
        else if (ua.contains("Firefox/"))   browser = "Firefox";
        else if (ua.contains("Safari/"))    browser = "Safari";

        return "OS: " + os + " Tarayıcı: " + browser;
    }

    public record RegisterRequest(String fullName, String email, String password, String role) {}
    public record LoginRequest(String email, String password) {}
}
