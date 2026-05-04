package com.fareltek.fsignal.api;

import com.fareltek.fsignal.tcp.TcpConnectionHandler;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class MonitorController {

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

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return handler.getDataStream();
    }
}
