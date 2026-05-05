package com.fareltek.fsignal.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class TcpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionHandler.class);

    private final Sinks.Many<TcpDataEvent> dataSink =
            Sinks.many().multicast().onBackpressureBuffer();

    public void onData(String remoteAddr, byte[] data) {
        TcpDataEvent event = TcpDataEvent.from(remoteAddr, data);
        log.info("[TCP-DATA] {} | {} bytes | {}", event.remoteAddr(), event.byteCount(), event.hex());
        dataSink.tryEmitNext(event);
    }

    public Flux<TcpDataEvent> getDataStream() {
        return dataSink.asFlux();
    }
}
