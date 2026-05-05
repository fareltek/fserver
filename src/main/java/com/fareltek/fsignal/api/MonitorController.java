package com.fareltek.fsignal.api;

import com.fareltek.fsignal.db.SafetyEvent;
import com.fareltek.fsignal.db.SafetyEventService;
import com.fareltek.fsignal.tcp.TcpConnectionHandler;
import com.fareltek.fsignal.tcp.TcpDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final TcpConnectionHandler handler;
    private final SafetyEventService safetyEventService;

    public MonitorController(TcpConnectionHandler handler, SafetyEventService safetyEventService) {
        this.handler = handler;
        this.safetyEventService = safetyEventService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "FSignal Server",
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TcpDataEvent> stream() {
        Flux<TcpDataEvent> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> TcpDataEvent.heartbeat());
        return Flux.merge(handler.getDataStream(), heartbeat)
                .doOnSubscribe(s -> log.info("[SSE] Browser baglandi"))
                .doOnCancel(() -> log.info("[SSE] Browser baglantisi kesildi"));
    }

    /** Last 100 events from DB (legacy). */
    @GetMapping("/api/events")
    public Flux<Map<String, Object>> getEvents() {
        return safetyEventService.getLast100().map(this::toDto);
    }

    /** Recent events for monitor initial load. */
    @GetMapping("/api/events/recent")
    public Flux<Map<String, Object>> getRecent(
            @RequestParam(defaultValue = "200") int limit) {
        return safetyEventService.getRecent(limit).map(this::toDto);
    }

    /** Filtered events for report tab. */
    @GetMapping("/api/events/report")
    public Flux<Map<String, Object>> getReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String sourceAddr) {
        OffsetDateTime fromDt = from != null ? LocalDate.parse(from).atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime toDt   = to   != null ? LocalDate.parse(to).atTime(23, 59, 59).atOffset(ZoneOffset.UTC) : null;
        return safetyEventService.getFiltered(fromDt, toDt, severity, sourceAddr).map(this::toDto);
    }

    @PostMapping("/api/events/{id}/acknowledge")
    public Mono<Map<String, Object>> acknowledge(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "operator") String by) {
        return safetyEventService.acknowledge(id, by).map(this::toDto);
    }

    private Map<String, Object> toDto(SafetyEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            e.getId() != null ? e.getId().toString() : null);
        m.put("eventTime",     e.getEventTime() != null ? e.getEventTime().toString() : null);
        m.put("receiveTime",   e.getReceiveTime() != null ? e.getReceiveTime().toString() : null);
        m.put("sourceAddr",    e.getSourceAddr());
        m.put("severity",      e.getSeverity());
        m.put("messageType",   e.getMessageType());
        m.put("description",   e.getDescription()); // hex string
        m.put("acknowledged",  e.getAcknowledged());
        m.put("acknowledgedBy", e.getAcknowledgedBy());
        return m;
    }
}
