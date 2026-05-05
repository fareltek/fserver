package com.fareltek.fsignal.api;

import com.fareltek.fsignal.tcp.TcpConnectionHandler;
import com.fareltek.fsignal.tcp.TcpDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final TcpConnectionHandler handler;

    public MonitorController(TcpConnectionHandler handler) {
        this.handler = handler;
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
                .doOnNext(e -> log.info("[SSE] Event gonderiliyor: {} bytes", e.byteCount()))
                .doOnCancel(() -> log.info("[SSE] Browser baglantisi kesildi"))
                .doOnError(e -> log.error("[SSE] Hata: {}", e.getMessage()));
    }
}
