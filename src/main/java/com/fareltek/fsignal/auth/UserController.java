package com.fareltek.fsignal.auth;

import com.fareltek.fsignal.db.SafetyEventService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String SYSTEM_SOURCE = "SYSTEM";

    private final AppUserService     userService;
    private final SafetyEventService eventService;

    public UserController(AppUserService userService, SafetyEventService eventService) {
        this.userService  = userService;
        this.eventService = eventService;
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> list() {
        return userService.findAll()
                .filter(u -> !"fareltek".equals(u.getUsername()))
                .map(u -> Map.<String, Object>of(
                        "id",        u.getId().toString(),
                        "fullName",  u.getFullName(),
                        "username",  u.getUsername(),
                        "role",      u.getRole(),
                        "active",    Boolean.TRUE.equals(u.getActive()),
                        "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                        "lastLogin", u.getLastLogin()  != null ? u.getLastLogin().toString()  : ""))
                .collectList();
    }

    @PostMapping
    public Mono<Map<String, Object>> createUser(@RequestBody CreateUserRequest req, Authentication auth) {
        String actor = actorName(auth);
        return userService.create(req.fullName(), req.username(), req.password(), req.role())
                .flatMap(u -> eventService.saveSystemEvent(SYSTEM_SOURCE, "INFO",
                        "Kullanıcı oluşturuldu: " + u.getFullName() + " (" + u.getUsername() + ")"
                        + " Rol: " + u.getRole() + " | İşlem: " + actor)
                        .thenReturn(Map.<String, Object>of(
                                "id",       u.getId().toString(),
                                "fullName", u.getFullName(),
                                "username", u.getUsername(),
                                "role",     u.getRole(),
                                "active",   Boolean.TRUE.equals(u.getActive()))))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteUser(@PathVariable UUID id, Authentication auth) {
        String actor = actorName(auth);
        return userService.findById(id)
                .flatMap(u -> userService.delete(id)
                        .then(eventService.saveSystemEvent(SYSTEM_SOURCE, "WARNING",
                                "Kullanıcı silindi: " + u.getFullName() + " (" + u.getUsername() + ")"
                                + " | İşlem: " + actor)))
                .then()
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @PostMapping("/{id}/activate")
    public Mono<Map<String, Object>> activate(@PathVariable UUID id, Authentication auth) {
        String actor = actorName(auth);
        return userService.setActive(id, true)
                .flatMap(u -> eventService.saveSystemEvent(SYSTEM_SOURCE, "INFO",
                        "Kullanıcı aktifleştirildi: " + u.getFullName() + " (" + u.getUsername() + ")"
                        + " | İşlem: " + actor)
                        .thenReturn(Map.<String, Object>of("id", u.getId().toString(), "active", true)))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @PostMapping("/{id}/deactivate")
    public Mono<Map<String, Object>> deactivate(@PathVariable UUID id, Authentication auth) {
        String actor = actorName(auth);
        return userService.setActive(id, false)
                .flatMap(u -> eventService.saveSystemEvent(SYSTEM_SOURCE, "WARNING",
                        "Kullanıcı devre dışı bırakıldı: " + u.getFullName() + " (" + u.getUsername() + ")"
                        + " | İşlem: " + actor)
                        .thenReturn(Map.<String, Object>of("id", u.getId().toString(), "active", false)))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @PostMapping("/{id}/role")
    public Mono<Map<String, Object>> changeRole(@PathVariable UUID id, @RequestBody RoleRequest req, Authentication auth) {
        String actor = actorName(auth);
        return userService.findById(id)
                .flatMap(before -> userService.setRole(id, req.role())
                        .flatMap(u -> eventService.saveSystemEvent(SYSTEM_SOURCE, "INFO",
                                "Rol değiştirildi: " + u.getFullName() + " (" + u.getUsername() + ")"
                                + " " + before.getRole() + " → " + u.getRole() + " | İşlem: " + actor)
                                .thenReturn(Map.<String, Object>of("id", u.getId().toString(), "role", u.getRole()))))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private String actorName(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    public record CreateUserRequest(String fullName, String username, String password, String role) {}
    public record RoleRequest(String role) {}
}
