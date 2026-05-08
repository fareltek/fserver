package com.fareltek.fsignal.system;

import com.fareltek.fsignal.db.SafetyEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class SystemHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthScheduler.class);
    private static final long   GB  = 1024L * 1024 * 1024;
    private static final long   MB  = 1024L * 1024;

    private final SafetyEventService eventService;
    private final SystemConfigService configService;

    public SystemHealthScheduler(SafetyEventService eventService, SystemConfigService configService) {
        this.eventService  = eventService;
        this.configService = configService;
    }

    /** Called by ScheduledTaskManager — interval configured via system_config. */
    public void healthCheck() {
        String archivePath  = configService.getSync("archive.path", "./archive");
        int    diskWarnPct  = (int) configService.getLong("health.disk-warn-pct", 85L);

        String time         = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String uptime       = formatUptime(ManagementFactory.getRuntimeMXBean().getUptime());
        String disk         = diskInfo(archivePath);
        String mem          = memInfo();
        String diskSeverity = diskUsagePct(archivePath) > diskWarnPct ? "WARNING" : "INFO";

        String msg = "Sistem durumu | Saat: " + time
                + " | Uptime: " + uptime
                + " | " + disk
                + " | " + mem;

        eventService.saveSystemEvent("SYSTEM", diskSeverity, msg)
                .doOnError(e -> log.error("[HEALTH] Kayit hatasi: {}", e.getMessage()))
                .subscribe();
    }

    /** Called by ScheduledTaskManager — interval configured via system_config. */
    public void ntpCheck() {
        String server    = configService.getSync("ntp.server", "pool.ntp.org");
        int    timeoutMs = (int) configService.getLong("ntp.timeout-ms", 4000L);
        long   threshold = configService.getLong("ntp.warn-threshold-ms", 1000L);

        Mono.fromCallable(() -> NtpClient.query(server, timeoutMs))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    result -> {
                        String severity = result.isDrifted(threshold) ? "WARNING" : "INFO";
                        String msg = "NTP zaman kontrolü | " + result.summary()
                                + " | Yerel UTC: " + OffsetDateTime.now(ZoneOffset.UTC)
                                + (result.isDrifted(threshold)
                                    ? " | ⚠ SAAT KAYMASI EŞİK AŞILDI (>" + threshold + "ms)"
                                    : "");
                        log.info("[NTP] {}", msg);
                        eventService.saveSystemEvent("SYSTEM", severity, msg).subscribe();
                    },
                    error -> {
                        String msg = "NTP sorgu hatası | Sunucu: " + server
                                + " | Hata: " + error.getMessage()
                                + " | Yerel UTC: " + OffsetDateTime.now(ZoneOffset.UTC);
                        log.warn("[NTP] {}", msg);
                        eventService.saveSystemEvent("SYSTEM", "WARNING", msg).subscribe();
                    }
                );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String diskInfo(String archivePath) {
        try {
            FileStore fs    = Files.getFileStore(Paths.get(archivePath).toAbsolutePath());
            long total      = fs.getTotalSpace();
            long free       = fs.getUsableSpace();
            long used       = total - free;
            int  pct        = total > 0 ? (int) (used * 100L / total) : 0;
            return String.format("Disk: %d/%d GB (%%%d)", used / GB, total / GB, pct);
        } catch (IOException e) {
            return "Disk: bilinmiyor";
        }
    }

    private int diskUsagePct(String archivePath) {
        try {
            FileStore fs = Files.getFileStore(Paths.get(archivePath).toAbsolutePath());
            long total   = fs.getTotalSpace();
            if (total == 0) return 0;
            return (int) ((total - fs.getUsableSpace()) * 100L / total);
        } catch (IOException e) {
            return 0;
        }
    }

    private String memInfo() {
        Runtime rt = Runtime.getRuntime();
        long used  = (rt.totalMemory() - rt.freeMemory()) / MB;
        long max   = rt.maxMemory() / MB;
        return String.format("Bellek: %d/%d MB", used, max);
    }

    private String formatUptime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        return h + "sa " + m + "dk";
    }
}
