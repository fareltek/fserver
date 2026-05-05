package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.db.SafetyEventService;
import com.fareltek.fsignal.section.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class TcpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionHandler.class);

    private final SafetyEventService safetyEventService;
    private final Sinks.Many<TcpDataEvent> dataSink = Sinks.many().replay().limit(100);

    public TcpConnectionHandler(SafetyEventService safetyEventService) {
        this.safetyEventService = safetyEventService;
    }

    public void onConnected(Section s) {
        log.info("[TCP][{}] Baglandi: {}", s.getName(), s.sourceAddr());
        emit(TcpDataEvent.connectionEvent(s.getId(), s.getName(), s.sourceAddr(), "CONNECTED"));
    }

    public void onDisconnected(Section s) {
        log.warn("[TCP][{}] Baglanti kesildi: {}", s.getName(), s.sourceAddr());
        emit(TcpDataEvent.connectionEvent(s.getId(), s.getName(), s.sourceAddr(), "DISCONNECTED"));
    }

    public void onData(Section s, byte[] data) {
        TcpDataEvent event = TcpDataEvent.fromData(s.getId(), s.getName(), s.sourceAddr(), data);
        log.info("[TCP-DATA][{}] {} bytes | {}", s.getName(), event.byteCount(), event.hex());
        emit(event);

        safetyEventService.save(s.sourceAddr(), data, event.hex())
                .subscribe(null, e -> log.error("[DB] Kayit hatasi: {}", e.getMessage()));
    }

    public Flux<TcpDataEvent> getDataStream() {
        return dataSink.asFlux();
    }

    private void emit(TcpDataEvent event) {
        dataSink.emitNext(event, (signalType, result) -> result == Sinks.EmitResult.FAIL_NON_SERIALIZED);
    }
}
