package com.fareltek.fsignal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class SafetyEventService {

    private static final Logger log = LoggerFactory.getLogger(SafetyEventService.class);

    private final SafetyEventRepository repository;

    public SafetyEventService(SafetyEventRepository repository) {
        this.repository = repository;
    }

    public Mono<SafetyEvent> save(String sourceAddr, byte[] rawData, String hex) {
        return repository.save(SafetyEvent.fromRaw(sourceAddr, rawData, hex))
                .doOnSuccess(e -> log.debug("[DB] Event kaydedildi: {}", e.getId()))
                .doOnError(e -> log.error("[DB] Kayit hatasi: {}", e.getMessage()));
    }

    public Flux<SafetyEvent> getLast100() {
        return repository.findTop100ByOrderByEventTimeDesc();
    }

    public Flux<SafetyEvent> getUnacknowledged() {
        return repository.findByAcknowledgedFalseOrderByEventTimeDesc();
    }

    public Mono<SafetyEvent> acknowledge(UUID id, String acknowledgedBy) {
        return repository.findById(id)
                .flatMap(event -> {
                    event.setAcknowledged(true);
                    event.setAcknowledgedBy(acknowledgedBy);
                    event.setAcknowledgedTime(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
                    return repository.save(event);
                });
    }
}
