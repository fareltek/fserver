package com.fareltek.fsignal.auth;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AppUserRepository extends R2dbcRepository<AppUser, UUID> {
    Mono<AppUser> findByEmail(String email);
}
