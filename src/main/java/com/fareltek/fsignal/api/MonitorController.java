package com.fareltek.fsignal.api;

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
import java.time.LocalDateTime;
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

    @GetMapping("/api/events")
    public Flux<Object> getEvents() {
        return safetyEventService.getLast100().cast(Object.class);
    }

    @PostMapping("/api/events/{id}/acknowledge")
    public Mono<Object> acknowledge(@PathVariable UUID id,
                                    @RequestParam(defaultValue = "operator") String by) {
        return safetyEventService.acknowledge(id, by).cast(Object.class);
    }
}
