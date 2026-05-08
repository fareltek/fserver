package com.fareltek.fsignal.system;

import com.fareltek.fsignal.db.SafetyEvent;
import com.fareltek.fsignal.db.SafetyEventService;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    @Value("${fsignal.archive.path:./fsignal}")
    private String archivePath;

    private final SafetyEventService eventService;
    private final DatabaseClient     dbClient;
    private final SystemConfigService configService;

    public DataRetentionScheduler(SafetyEventService eventService,
                                  ConnectionFactory connectionFactory,
                                  SystemConfigService configService) {
        this.eventService  = eventService;
        this.dbClient      = DatabaseClient.create(connectionFactory);
        this.configService = configService;
    }

    /** Called by ScheduledTaskManager — schedule configured via system_config (retention.run-hour/minute). */
    public void runRetention() {
        int    retentionDays = (int) configService.getLong("retention.days", 730L);
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        log.info("[RETENTION] Başlıyor — {} günden eski olaylar arşivleniyor (cutoff={})", retentionDays, cutoff);

        eventService.getOlderThan(cutoff)
                .collectList()
                .flatMap(events -> {
                    if (events.isEmpty()) {
                        return eventService.saveSystemEvent("SYSTEM", "INFO",
                                "Veri saklama kontrolü: arşivlenecek olay yok (cutoff=" + cutoff.toLocalDate() + ")");
                    }
                    return archiveToCsv(events, cutoff, this.archivePath)
                            .flatMap(csvPath -> bulkDelete(cutoff)
                                    .flatMap(deleted -> eventService.saveSystemEvent("SYSTEM", "INFO",
                                            "Veri arşivlendi: " + deleted + " olay silindi"
                                            + ", CSV: " + csvPath
                                            + " | Saklama süresi: " + retentionDays + " gün")));
                })
                .doOnError(e -> {
                    log.error("[RETENTION] Hata: {}", e.getMessage());
                    eventService.saveSystemEvent("SYSTEM", "WARNING",
                            "Veri arşivleme hatası: " + e.getMessage()).subscribe();
                })
                .subscribe();
    }

    private Mono<String> archiveToCsv(List<SafetyEvent> events, OffsetDateTime cutoff, String path) {
        return Mono.fromCallable(() -> {
            Path dir = Paths.get(path).resolve("retention");
            Files.createDirectories(dir);
            String fileName = "retention-" + LocalDate.now(ZoneOffset.UTC) + "-cutoff-"
                    + cutoff.toLocalDate() + ".csv";
            Path file = dir.resolve(fileName);
            Files.write(file, buildCsv(events).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("[RETENTION] CSV yazıldı: {} ({} satır)", file, events.size());
            return dir.getFileName().resolve(fileName).toString();
        });
    }

    private Mono<Long> bulkDelete(OffsetDateTime cutoff) {
        return dbClient.sql("DELETE FROM safety_events WHERE event_time < :cutoff")
                .bind("cutoff", cutoff)
                .fetch()
                .rowsUpdated();
    }

    private String buildCsv(List<SafetyEvent> events) throws IOException {
        StringBuilder sb = new StringBuilder(
                "﻿sequence_no,id,event_time,source_addr,severity,message_type," +
                "device_type,device_id,event_code,description,acknowledged,acknowledged_by\r\n");
        for (SafetyEvent e : events) {
            sb.append(nvl(e.getSequenceNo())).append(',')
              .append(nvl(e.getId())).append(',')
              .append(nvl(e.getEventTime())).append(',')
              .append(nvl(e.getSourceAddr())).append(',')
              .append(nvl(e.getSeverity())).append(',')
              .append(nvl(e.getMessageType())).append(',')
              .append(nvl(e.getDeviceType())).append(',')
              .append(nvl(e.getDeviceId())).append(',')
              .append(nvl(e.getEventCode())).append(',')
              .append('"').append(e.getDescription() != null
                      ? e.getDescription().replace("\"", "\"\"") : "").append('"').append(',')
              .append(nvl(e.getAcknowledged())).append(',')
              .append(nvl(e.getAcknowledgedBy())).append("\r\n");
        }
        return sb.toString();
    }

    private String nvl(Object o) { return o != null ? o.toString() : ""; }
}
