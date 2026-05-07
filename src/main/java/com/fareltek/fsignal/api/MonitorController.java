package com.fareltek.fsignal.api;

import com.fareltek.fsignal.db.SafetyEvent;
import com.fareltek.fsignal.db.SafetyEventService;
import com.fareltek.fsignal.report.PdfReportService;
import com.fareltek.fsignal.tcp.TcpConnectionHandler;
import com.fareltek.fsignal.tcp.TcpDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final TcpConnectionHandler handler;
    private final SafetyEventService   safetyEventService;
    private final PdfReportService     pdfReportService;

    public MonitorController(TcpConnectionHandler handler,
                             SafetyEventService safetyEventService,
                             PdfReportService pdfReportService) {
        this.handler            = handler;
        this.safetyEventService = safetyEventService;
        this.pdfReportService   = pdfReportService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "FSignal Server",
                      "timestamp", LocalDateTime.now().toString());
    }

    @GetMapping("/api/stats")
    public Mono<Map<String, Object>> globalStats() {
        var sectStats = handler.getSectionStats();
        long connectedSections = sectStats.values().stream().filter(s -> s.connected).count();
        long totalPackets      = sectStats.values().stream().mapToLong(s -> s.packets.get()).sum();
        long totalBytesRcv     = sectStats.values().stream().mapToLong(s -> s.totalBytes.get()).sum();

        return Mono.zip(safetyEventService.getTotalCount(), safetyEventService.getUnacknowledgedCount())
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("totalEvents",          t.getT1());
                    m.put("unacknowledgedAlarms",  t.getT2());
                    m.put("connectedSections",     connectedSections);
                    m.put("totalPackets",          totalPackets);
                    m.put("totalBytesReceived",    totalBytesRcv);
                    m.put("serverTime",            LocalDateTime.now(ZoneOffset.UTC).toString());
                    return m;
                });
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TcpDataEvent> stream() {
        Flux<TcpDataEvent> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> TcpDataEvent.heartbeat());
        return Flux.merge(handler.getDataStream(), heartbeat)
                .doOnSubscribe(s -> log.info("[SSE] Browser baglandi"))
                .doOnCancel(() -> log.info("[SSE] Browser baglantisi kesildi"));
    }

    /** Recent events — returns proper JSON array (not streaming). */
    @GetMapping("/api/events/recent")
    public Mono<List<Map<String, Object>>> getRecent(
            @RequestParam(defaultValue = "200") int limit) {
        return safetyEventService.getRecent(limit).map(this::toDto).collectList();
    }

    /** Filtered events for report tab — proper JSON array. */
    @GetMapping("/api/events/report")
    public Mono<List<Map<String, Object>>> getReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String sourceAddr,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(defaultValue = "500") int limit) {
        OffsetDateTime fromDt = from != null ? LocalDate.parse(from).atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime toDt   = to   != null ? LocalDate.parse(to).atTime(23, 59, 59).atOffset(ZoneOffset.UTC) : null;
        return safetyEventService
                .getFiltered(fromDt, toDt, severity, sourceAddr, messageType, acknowledged)
                .take(limit).map(this::toDto).collectList();
    }

    /** Server-side PDF with proper Turkish encoding (OpenPDF + Cp1254). */
    @GetMapping(value = "/api/reports/pdf", produces = "application/pdf")
    public Mono<ResponseEntity<byte[]>> downloadPdf(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String sourceAddr,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(defaultValue = "500") int limit) {
        OffsetDateTime fromDt = from != null ? LocalDate.parse(from).atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime toDt   = to   != null ? LocalDate.parse(to).atTime(23, 59, 59).atOffset(ZoneOffset.UTC) : null;
        return safetyEventService
                .getFiltered(fromDt, toDt, severity, sourceAddr, messageType, acknowledged)
                .take(limit).collectList()
                .flatMap(events -> Mono.fromCallable(() ->
                        pdfReportService.generate(events, from, to, severity, sourceAddr, messageType, acknowledged))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Disposition",
                                "attachment; filename=\"FSIGNAL_REPORT_" + LocalDate.now(ZoneOffset.UTC) + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes));
    }

    @PostMapping("/api/events/{id}/acknowledge")
    public Mono<Map<String, Object>> acknowledge(
            @PathVariable UUID id,
            Authentication auth) {
        String by = auth != null ? auth.getName() : "system";
        return safetyEventService.acknowledge(id, by)
                .flatMap(event -> safetyEventService.saveSystemEvent("SYSTEM", "INFO",
                        "Olay onaylandı: ID=" + event.getId()
                        + " Tip=" + event.getMessageType()
                        + " Kaynak=" + event.getSourceAddr()
                        + " | İşlem: " + by)
                        .thenReturn(toDto(event)));
    }

    @PostMapping("/api/events/acknowledge-all")
    public Mono<Map<String, Object>> acknowledgeAll(Authentication auth) {
        String by = auth != null ? auth.getName() : "system";
        return safetyEventService.acknowledgeAllAlarms(by)
                .flatMap(count -> safetyEventService.saveSystemEvent("SYSTEM", "INFO",
                        "Tüm alarmlar onaylandı (" + count + " olay) | İşlem: " + by)
                        .thenReturn(Map.<String, Object>of("acknowledged", count)));
    }

    private Map<String, Object> toDto(SafetyEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             e.getId()          != null ? e.getId().toString()          : null);
        m.put("eventTime",      e.getEventTime()   != null ? e.getEventTime().toString()   : null);
        m.put("receiveTime",    e.getReceiveTime() != null ? e.getReceiveTime().toString() : null);
        m.put("sourceAddr",     e.getSourceAddr());
        m.put("severity",       e.getSeverity());
        m.put("messageType",    e.getMessageType());
        m.put("deviceType",     e.getDeviceType());
        m.put("eventCode",      e.getEventCode());
        m.put("eventData",      e.getEventData());
        m.put("eventFlags",     e.getEventFlags());
        m.put("description",    e.getDescription());
        m.put("acknowledged",     e.getAcknowledged());
        m.put("acknowledgedBy",   e.getAcknowledgedBy());
        m.put("acknowledgedTime", e.getAcknowledgedTime() != null ? e.getAcknowledgedTime().toString() : null);
        return m;
    }
}
