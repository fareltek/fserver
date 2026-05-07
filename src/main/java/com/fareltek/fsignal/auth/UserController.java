package com.fareltek.fsignal.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserService userService;

    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> list() {
        return userService.findAll()
                .map(u -> Map.<String, Object>of(
                        "id",        u.getId().toString(),
                        "fullName",  u.getFullName(),
                        "email",     u.getEmail(),
                        "role",      u.getRole(),
                        "active",    Boolean.TRUE.equals(u.getActive()),
                        "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                        "lastLogin", u.getLastLogin()  != null ? u.getLastLogin().toString()  : ""))
                .collectList();
    }

    @PostMapping("/{id}/activate")
    public Mono<Map<String, Object>> activate(@PathVariable UUID id) {
        return userService.setActive(id, true)
                .map(u -> Map.<String, Object>of("id", u.getId().toString(), "active", true))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @PostMapping("/{id}/deactivate")
    public Mono<Map<String, Object>> deactivate(@PathVariable UUID id) {
        return userService.setActive(id, false)
                .map(u -> Map.<String, Object>of("id", u.getId().toString(), "active", false))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }
}
