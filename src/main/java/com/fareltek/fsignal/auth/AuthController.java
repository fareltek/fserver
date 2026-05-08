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
    private final LoginRateLimiter   rateLimiter;

    public AuthController(AppUserService userService, SafetyEventService eventService,
                          LoginRateLimiter rateLimiter) {
        this.userService  = userService;
        this.eventService = eventService;
        this.rateLimiter  = rateLimiter;
    }

    @PostMapping("/register")
    public Mono<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (req.fullName() == null || req.fullName().isBlank())
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ad soyad zorunludur."));
        if (req.username() == null || req.username().isBlank())
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kullanıcı adı zorunludur."));
        if (req.password() == null || req.password().length() < 8)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şifre en az 8 karakter olmalı."));

        String role = (req.role() != null) ? req.role().toUpperCase() : "GUEST";
        return userService.register(req.fullName().trim(), req.username().toLowerCase().trim(), req.password(), role)
                .map(u -> Map.<String, Object>of(
                        "message",  "Kayıt başarılı.",
                        "username", u.getUsername(),
                        "role",     u.getRole()))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()));
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
        if (req.username() == null || req.password() == null)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kullanıcı adı ve şifre zorunludur."));

        String username   = req.username().toLowerCase().trim();
        String ip         = extractIp(exchange.getRequest());
        String userAgent  = parseUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        String clientInfo = "IP: " + ip + " | " + userAgent;

        if (rateLimiter.isBlocked(ip)) {
            int count = rateLimiter.getAttemptCount(ip);
            return eventService.saveSystemEvent("SYSTEM", "WARNING",
                            "IP engellendi (çok fazla deneme): " + ip
                            + " — " + count + " deneme/dk | Hedef: " + username)
                    .then(Mono.error(new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Çok fazla giriş denemesi. 1 dakika bekleyin.")));
        }

        return userService.login(username, req.password())
                .flatMap(token -> eventService.saveSystemEvent("SYSTEM", "INFO",
                        "Giriş başarılı: " + username + " | " + clientInfo)
                        .thenReturn(Map.<String, Object>of("token", token)))
                .onErrorResume(IllegalArgumentException.class, e ->
                        eventService.saveSystemEvent("SYSTEM", "WARNING",
                                "Başarısız giriş: " + username + " (şifre hatalı) | " + clientInfo)
                                .then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()))))
                .onErrorResume(IllegalStateException.class, e ->
                        eventService.saveSystemEvent("SYSTEM", "WARNING",
                                "Başarısız giriş: " + username + " (" + e.getMessage() + ") | " + clientInfo)
                                .then(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage()))));
    }

    @GetMapping("/me")
    public Mono<Map<String, Object>> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return userService.findByUsername(auth.getName())
                .map(u -> Map.<String, Object>of(
                        "id",       u.getId().toString(),
                        "fullName", u.getFullName(),
                        "username", u.getUsername(),
                        "role",     u.getRole()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private String extractIp(ServerHttpRequest req) {
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = req.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return req.getRemoteAddress() != null
                ? req.getRemoteAddress().getAddress().getHostAddress()
                : "bilinmiyor";
    }

    private String parseUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Tarayıcı: bilinmiyor";
        String os = "?", browser = "?";
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

    public record RegisterRequest(String fullName, String username, String password, String role) {}
    public record LoginRequest(String username, String password) {}
}
