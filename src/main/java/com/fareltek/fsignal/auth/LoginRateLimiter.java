package com.fareltek.fsignal.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory per-IP rate limiter for login attempts.
 * Allows up to MAX_ATTEMPTS within WINDOW_MS; beyond that, isBlocked() returns true.
 */
@Component
public class LoginRateLimiter {

    private static final int  MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS    = 60_000L; // 1 minute

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> attempts =
            new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        long now    = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        ConcurrentLinkedDeque<Long> times =
                attempts.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        times.removeIf(t -> t < cutoff);
        times.addLast(now);
        return times.size() > MAX_ATTEMPTS;
    }

    /** How many attempts in the current window (for logging). */
    public int getAttemptCount(String ip) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        ConcurrentLinkedDeque<Long> times = attempts.get(ip);
        if (times == null) return 0;
        times.removeIf(t -> t < cutoff);
        return times.size();
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        attempts.entrySet().removeIf(e -> {
            e.getValue().removeIf(t -> t < cutoff);
            return e.getValue().isEmpty();
        });
    }
}
