package com.fareltek.fsignal.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository repo;
    private final PasswordEncoder   encoder;

    public DataInitializer(AppUserRepository repo, PasswordEncoder encoder) {
        this.repo    = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        repo.findByEmail("fareltek")
            .switchIfEmpty(
                repo.save(AppUser.create("Admin", "fareltek", encoder.encode("2580"), "MANAGER"))
                    .doOnSuccess(u -> log.info("[AUTH] Admin hesabi olusturuldu: {}", u.getEmail()))
            )
            .subscribe(u -> log.info("[AUTH] Admin hesabi mevcut: {}", u.getEmail()));
    }
}
