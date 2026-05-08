package com.fareltek.fsignal.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppUserService {

    private static final int MAX_ATTEMPTS  = 5;
    private static final int LOCK_MINUTES  = 15;
    private static final List<String> VALID_ROLES = List.of("MANAGER", "OPERATOR", "GUEST");

    private final AppUserRepository repo;
    private final PasswordEncoder   encoder;
    private final JwtUtil           jwtUtil;

    public AppUserService(AppUserRepository repo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.repo    = repo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    public Mono<AppUser> register(String fullName, String username, String rawPassword, String requestedRole) {
        String role = ("OPERATOR".equals(requestedRole)) ? "OPERATOR" : "GUEST";
        return repo.findByUsername(username)
                .flatMap(existing -> Mono.<AppUser>error(new IllegalArgumentException("Bu kullanıcı adı zaten kayıtlı.")))
                .switchIfEmpty(
                    repo.count().flatMap(count -> {
                        boolean isFirst = (count == 0);
                        String effectiveRole = isFirst ? "MANAGER" : role;
                        AppUser user = AppUser.create(fullName, username, encoder.encode(rawPassword), effectiveRole);
                        user.setActive(isFirst);
                        return repo.save(user);
                    })
                );
    }

    public Mono<String> login(String username, String rawPassword) {
        return repo.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kullanıcı bulunamadı.")))
                .flatMap(user -> {
                    if (!Boolean.TRUE.equals(user.getActive())) {
                        return Mono.error(new IllegalStateException("Hesap devre dışı."));
                    }
                    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
                        return Mono.error(new IllegalStateException(
                                "Hesap kilitli. " + LOCK_MINUTES + " dakika sonra tekrar deneyin."));
                    }
                    if (!encoder.matches(rawPassword, user.getPasswordHash())) {
                        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
                        user.setFailedAttempts(attempts);
                        boolean lockout = attempts >= MAX_ATTEMPTS;
                        if (lockout) {
                            user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
                            user.setFailedAttempts(0);
                        }
                        return repo.save(user).then(Mono.error(lockout
                                ? new IllegalStateException("Çok fazla başarısız giriş. Hesap " + LOCK_MINUTES + " dakika kilitlendi.")
                                : new IllegalArgumentException("Şifre hatalı.")));
                    }
                    user.setFailedAttempts(0);
                    user.setLockedUntil(null);
                    user.setLastLogin(OffsetDateTime.now());
                    String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getFullName(), user.getRole());
                    return repo.save(user).thenReturn(token);
                });
    }

    public Mono<AppUser> findByUsername(String username) {
        return repo.findByUsername(username);
    }

    public Mono<AppUser> findById(UUID id) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kullanıcı bulunamadı.")));
    }

    public Flux<AppUser> findAll() {
        return repo.findAll();
    }

    public Mono<AppUser> setActive(UUID id, boolean active) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kullanıcı bulunamadı.")))
                .flatMap(u -> { u.setActive(active); return repo.save(u); });
    }

    public Mono<AppUser> setRole(UUID id, String role) {
        if (!VALID_ROLES.contains(role))
            return Mono.error(new IllegalArgumentException("Geçersiz rol: " + role));
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kullanıcı bulunamadı.")))
                .flatMap(u -> { u.setRole(role); return repo.save(u); });
    }

    public Mono<AppUser> create(String fullName, String username, String rawPassword, String role) {
        if (!VALID_ROLES.contains(role))
            return Mono.error(new IllegalArgumentException("Geçersiz rol: " + role));
        return repo.findByUsername(username)
                .flatMap(existing -> Mono.<AppUser>error(new IllegalArgumentException("Bu kullanıcı adı zaten mevcut.")))
                .switchIfEmpty(Mono.defer(() -> {
                    AppUser u = AppUser.create(fullName, username, encoder.encode(rawPassword), role);
                    u.setActive(true);
                    return repo.save(u);
                }));
    }

    public Mono<Void> delete(UUID id) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kullanıcı bulunamadı.")))
                .flatMap(u -> {
                    if ("fareltek".equals(u.getUsername()))
                        return Mono.<Void>error(new IllegalArgumentException("Sistem yöneticisi silinemez."));
                    return repo.deleteById(id);
                });
    }
}
