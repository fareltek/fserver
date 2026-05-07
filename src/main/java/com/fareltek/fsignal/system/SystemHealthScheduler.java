package com.fareltek.fsignal.system;

import com.fareltek.fsignal.db.SafetyEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Value("${fsignal.archive.path:./archive}")
    private String archivePath;

    public SystemHealthScheduler(SafetyEventService eventService) {
        this.eventService = eventService;
    }

    /** Every 30 minutes: record system time + disk + memory. */
    @Scheduled(fixedRate = 1_800_000)
    public void healthAndTimeCheck() {
        String time   = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String uptime = formatUptime(ManagementFactory.getRuntimeMXBean().getUptime());
        String disk   = diskInfo();
        String mem    = memInfo();
        String diskSeverity = diskUsagePct() > 85 ? "WARNING" : "INFO";

        String msg = "Sistem durumu | Saat: " + time
                + " | Uptime: " + uptime
                + " | " + disk
                + " | " + mem;

        eventService.saveSystemEvent("SYSTEM", diskSeverity, msg)
                .doOnError(e -> log.error("[HEALTH] Kayit hatasi: {}", e.getMessage()))
                .subscribe();
    }

    /** Every 6 hours: explicit NTP/time reference snapshot. */
    @Scheduled(fixedRate = 21_600_000)
    public void ntpSnapshot() {
        String utc = OffsetDateTime.now(ZoneOffset.UTC).toString();
        long   jvmMs = ManagementFactory.getRuntimeMXBean().getUptime();
        eventService.saveSystemEvent("SYSTEM", "INFO",
                "NTP zaman referansı | UTC: " + utc + " | JVM uptime: " + formatUptime(jvmMs))
                .subscribe();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String diskInfo() {
        try {
            FileStore fs = Files.getFileStore(Paths.get(archivePath).toAbsolutePath());
            long total = fs.getTotalSpace();
            long free  = fs.getUsableSpace();
            long used  = total - free;
            int  pct   = total > 0 ? (int) (used * 100L / total) : 0;
            return String.format("Disk: %d/%d GB (%%%d)",
                    used / GB, total / GB, pct);
        } catch (Exception e) {
            return "Disk: bilinmiyor";
        }
    }

    private int diskUsagePct() {
        try {
            FileStore fs = Files.getFileStore(Paths.get(archivePath).toAbsolutePath());
            long total = fs.getTotalSpace();
            if (total == 0) return 0;
            return (int) ((total - fs.getUsableSpace()) * 100L / total);
        } catch (Exception e) {
            return 0;
        }
    }

    private String memInfo() {
        Runtime rt  = Runtime.getRuntime();
        long used   = (rt.totalMemory() - rt.freeMemory()) / MB;
        long max    = rt.maxMemory() / MB;
        return String.format("Bellek: %d/%d MB", used, max);
    }

    private String formatUptime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        return h + "sa " + m + "dk";
    }
}
