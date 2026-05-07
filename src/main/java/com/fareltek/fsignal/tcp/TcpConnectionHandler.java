package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.db.SafetyEventService;
import com.fareltek.fsignal.section.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TcpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionHandler.class);

    private final SafetyEventService safetyEventService;
    private final Sinks.Many<TcpDataEvent> dataSink = Sinks.many().replay().limit(100);

    private final ConcurrentHashMap<Integer, SectionStats> sectionStats = new ConcurrentHashMap<>();

    public TcpConnectionHandler(SafetyEventService safetyEventService) {
        this.safetyEventService = safetyEventService;
    }

    // ── Per-section runtime statistics ────────────────────────────────────────
    public static class SectionStats {
        public final AtomicInteger reconnects  = new AtomicInteger(0);
        public final AtomicLong    packets     = new AtomicLong(0);
        public final AtomicLong    totalBytes  = new AtomicLong(0);
        public volatile Instant    connectedSince;
        public volatile Instant    lastPacket;
        public volatile boolean    connected;
    }

    public Map<Integer, SectionStats> getSectionStats() {
        return Collections.unmodifiableMap(sectionStats);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    public void onConnected(Section s) {
        log.info("[TCP][{}] Baglandi: {}", s.getName(), s.sourceAddr());
        SectionStats st = sectionStats.computeIfAbsent(s.getId(), k -> new SectionStats());
        st.reconnects.incrementAndGet();
        st.connectedSince = Instant.now();
        st.connected = true;
        emit(TcpDataEvent.connectionEvent(s.getId(), s.getName(), s.sourceAddr(), "CONNECTED"));
    }

    public void onDisconnected(Section s) {
        log.warn("[TCP][{}] Baglanti kesildi: {}", s.getName(), s.sourceAddr());
        SectionStats st = sectionStats.get(s.getId());
        if (st != null) { st.connected = false; st.connectedSince = null; }
        emit(TcpDataEvent.connectionEvent(s.getId(), s.getName(), s.sourceAddr(), "DISCONNECTED"));
    }

    public void onData(Section s, byte[] data) {
        Fa51Parser.ParsedPacket pkt = Fa51Parser.parse(data);
        TcpDataEvent event = TcpDataEvent.fromData(s.getId(), s.getName(), s.sourceAddr(), data, pkt);

        SectionStats st = sectionStats.computeIfAbsent(s.getId(), k -> new SectionStats());
        st.packets.incrementAndGet();
        st.totalBytes.addAndGet(data.length);
        st.lastPacket = Instant.now();

        if (pkt != null) {
            log.info("[TCP-DATA][{}] {} bytes type={} device={} cs={}", s.getName(),
                    event.byteCount(), pkt.messageType(), pkt.sourceId(), pkt.checksumValid());
        } else {
            log.info("[TCP-DATA][{}] {} bytes (raw) | {}", s.getName(), event.byteCount(), event.hex());
        }

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
