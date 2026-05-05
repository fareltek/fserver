package com.fareltek.fsignal.db;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SafetyEventRepository extends ReactiveCrudRepository<SafetyEvent, UUID> {

    Flux<SafetyEvent> findTop100ByOrderByEventTimeDesc();
    Flux<SafetyEvent> findTop500ByOrderByEventTimeDesc();
    Flux<SafetyEvent> findByAcknowledgedFalseOrderByEventTimeDesc();
    Flux<SafetyEvent> findBySeverityOrderByEventTimeDesc(String severity);
    Flux<SafetyEvent> findBySourceAddrOrderByEventTimeDesc(String sourceAddr);
    Flux<SafetyEvent> findByEventTimeBetweenOrderByEventTimeDesc(OffsetDateTime from, OffsetDateTime to);
}
