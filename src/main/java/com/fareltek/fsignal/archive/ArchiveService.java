package com.fareltek.fsignal.archive;

import com.fareltek.fsignal.db.SafetyEventRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    @Value("${fsignal.archive.path:./archive}")
    private String archivePath;

    private final SafetyEventRepository repository;

    public ArchiveService(SafetyEventRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void schedule() {
        // İlk çalışma 60 saniye sonra, sonra saatte bir kontrol
        Flux.interval(Duration.ofSeconds(60), Duration.ofHours(1))
                .flatMap(tick -> checkAndExport())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, err -> log.error("[Archive] Hata: {}", err.getMessage()));
    }

    private reactor.core.publisher.Mono<Void> checkAndExport() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Path eventsDir = Paths.get(archivePath, "events");
        Path csvPath   = eventsDir.resolve("events-" + yesterday + ".csv");

        if (Files.exists(csvPath)) return reactor.core.publisher.Mono.empty();

        OffsetDateTime from = yesterday.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to   = yesterday.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        return repository.findByEventTimeBetweenOrderByEventTimeDesc(from, to)
                .collectList()
                .flatMap(events -> reactor.core.publisher.Mono.fromCallable(() -> {
                    Files.createDirectories(eventsDir);
                    try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                            new FileWriter(csvPath.toFile(), java.nio.charset.StandardCharsets.UTF_8)))) {
                        pw.println("id,event_time,receive_time,source_addr,severity,message_type," +
                                   "device_type,device_id,event_code,event_data,event_flags," +
                                   "description,acknowledged,acknowledged_by,acknowledged_time");
                        for (var e : events) {
                            pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,\"%s\",%s,%s,%s%n",
                                    nvl(e.getId()),
                                    nvl(e.getEventTime()),
                                    nvl(e.getReceiveTime()),
                                    nvl(e.getSourceAddr()),
                                    nvl(e.getSeverity()),
                                    nvl(e.getMessageType()),
                                    nvl(e.getDeviceType()),
                                    nvl(e.getDeviceId()),
                                    nvl(e.getEventCode()),
                                    nvl(e.getEventData()),
                                    nvl(e.getEventFlags()),
                                    (e.getDescription() != null ? e.getDescription().replace("\"","\"\"") : ""),
                                    nvl(e.getAcknowledged()),
                                    nvl(e.getAcknowledgedBy()),
                                    nvl(e.getAcknowledgedTime()));
                        }
                    }
                    log.info("[Archive] {} event dışa aktarıldı: {}", events.size(), csvPath);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private String nvl(Object o) {
        return o != null ? o.toString() : "";
    }
}
