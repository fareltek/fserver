package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.db.SafetyEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TcpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionHandler.class);

    private final SafetyEventService safetyEventService;
    private final Sinks.Many<TcpDataEvent> dataSink = Sinks.many().replay().limit(50);

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<String> currentAddr = new AtomicReference<>("—");

    public TcpConnectionHandler(SafetyEventService safetyEventService) {
        this.safetyEventService = safetyEventService;
    }

    public void onConnected(String addr) {
        connected.set(true);
        currentAddr.set(addr);
        TcpDataEvent event = TcpDataEvent.connectionEvent(addr, "CONNECTED");
        dataSink.tryEmitNext(event);
    }

    public void onDisconnected() {
        connected.set(false);
        String addr = currentAddr.get();
        currentAddr.set("—");
        TcpDataEvent event = TcpDataEvent.connectionEvent(addr, "DISCONNECTED");
        dataSink.tryEmitNext(event);
    }

    public void onData(byte[] data) {
        String addr = currentAddr.get();
        TcpDataEvent event = TcpDataEvent.from(addr, data);
        log.info("[TCP-DATA] {} | {} bytes | {}", addr, event.byteCount(), event.hex());

        Sinks.EmitResult result = dataSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("[TCP-DATA] Sink emit failed: {}", result);
        }

        safetyEventService.save(addr, data, event.hex())
                .subscribe(
                        saved -> {},
                        error -> log.error("[DB] Kayit hatasi: {}", error.getMessage())
                );
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getCurrentAddr() {
        return currentAddr.get();
    }

    public Flux<TcpDataEvent> getDataStream() {
        return dataSink.asFlux();
    }
}
