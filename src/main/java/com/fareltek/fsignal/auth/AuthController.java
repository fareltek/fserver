package com.fareltek.fsignal.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserService userService;

    public AuthController(AppUserService userService) {
        this.userService = userService;
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
    public Mono<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.email() == null || req.password() == null)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-posta ve şifre zorunludur."));
        return userService.login(req.email().toLowerCase().trim(), req.password())
                .map(token -> Map.<String, Object>of("token", token))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()))
                .onErrorMap(IllegalStateException.class,
                        e -> new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage()));
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

    public record RegisterRequest(String fullName, String email, String password, String role) {}
    public record LoginRequest(String email, String password) {}
}
