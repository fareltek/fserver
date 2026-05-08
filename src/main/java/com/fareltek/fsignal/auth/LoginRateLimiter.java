package com.fareltek.fsignal.auth;

import com.fareltek.fsignal.system.SystemConfigService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory per-IP sliding-window rate limiter for login attempts.
 * Limits are read from system_config DB table and cached; refreshed every minute.
 */
@Component
public class LoginRateLimiter {

    private final AtomicInteger maxAttempts = new AtomicInteger(10);
    private final AtomicLong    windowMs    = new AtomicLong(60_000L);

    private final SystemConfigService configService;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> attempts =
            new ConcurrentHashMap<>();

    public LoginRateLimiter(SystemConfigService configService) {
        this.configService = configService;
    }

    public boolean isBlocked(String ip) {
        long now    = System.currentTimeMillis();
        long cutoff = now - windowMs.get();
        ConcurrentLinkedDeque<Long> times =
                attempts.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        times.removeIf(t -> t < cutoff);
        times.addLast(now);
        return times.size() > maxAttempts.get();
    }

    public int getAttemptCount(String ip) {
        long cutoff = System.currentTimeMillis() - windowMs.get();
        ConcurrentLinkedDeque<Long> times = attempts.get(ip);
        if (times == null) return 0;
        times.removeIf(t -> t < cutoff);
        return times.size();
    }

    @Scheduled(fixedRate = 60_000)
    public void refreshConfig() {
        maxAttempts.set((int) configService.getLong("security.max-login-attempts", 10L));
        windowMs.set(configService.getLong("security.login-window-ms", 60_000L));
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - windowMs.get();
        attempts.entrySet().removeIf(e -> {
            e.getValue().removeIf(t -> t < cutoff);
            return e.getValue().isEmpty();
        });
    }
}
