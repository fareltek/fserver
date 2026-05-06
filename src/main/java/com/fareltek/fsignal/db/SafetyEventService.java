package com.fareltek.fsignal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
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
                .doOnSuccess(e -> log.debug("[DB] Event kaydedildi: {} seq={} type={}",
                        e.getId(), e.getSequence(), e.getMessageType()))
                .doOnError(e -> log.error("[DB] Kayit hatasi: {}", e.getMessage()));
    }

    public Flux<SafetyEvent> getLast100() {
        return repository.findTop100ByOrderByEventTimeDesc();
    }

    public Flux<SafetyEvent> getRecent(int limit) {
        return repository.findTop500ByOrderByEventTimeDesc().take(limit);
    }

    public Flux<SafetyEvent> getUnacknowledged() {
        return repository.findByAcknowledgedFalseOrderByEventTimeDesc();
    }

    public Flux<SafetyEvent> getFiltered(OffsetDateTime from, OffsetDateTime to,
                                          String severity, String sourceAddr,
                                          String messageType, Boolean acknowledged) {
        Flux<SafetyEvent> base = (from != null && to != null)
                ? repository.findByEventTimeBetweenOrderByEventTimeDesc(from, to)
                : repository.findTop500ByOrderByEventTimeDesc();

        if (severity    != null && !severity.isBlank())
            base = base.filter(e -> severity.equalsIgnoreCase(e.getSeverity()));
        if (sourceAddr  != null && !sourceAddr.isBlank())
            base = base.filter(e -> sourceAddr.equals(e.getSourceAddr()));
        if (messageType != null && !messageType.isBlank())
            base = base.filter(e -> messageType.equalsIgnoreCase(e.getMessageType()));
        if (acknowledged != null)
            base = base.filter(e -> acknowledged.equals(e.getAcknowledged()));
        return base;
    }

    public Mono<Long> getTotalCount() {
        return repository.count();
    }

    public Mono<Long> getUnacknowledgedCount() {
        return repository.countByAcknowledgedFalse();
    }

    public Mono<SafetyEvent> acknowledge(UUID id, String acknowledgedBy) {
        return repository.findById(id)
                .flatMap(event -> {
                    event.setAcknowledged(true);
                    event.setAcknowledgedBy(acknowledgedBy);
                    event.setAcknowledgedTime(OffsetDateTime.now(java.time.ZoneOffset.UTC));
                    return repository.save(event);
                });
    }
}
