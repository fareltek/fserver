package com.fareltek.fsignal.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class ScheduledTaskManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskManager.class);

    private final SystemHealthScheduler  healthScheduler;
    private final DataRetentionScheduler retentionScheduler;
    private final SystemConfigService    configService;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public ScheduledTaskManager(SystemHealthScheduler healthScheduler,
                                DataRetentionScheduler retentionScheduler,
                                SystemConfigService configService) {
        this.healthScheduler    = healthScheduler;
        this.retentionScheduler = retentionScheduler;
        this.configService      = configService;
    }

    @Override
    public void run(ApplicationArguments args) {
        taskScheduler.setPoolSize(3);
        taskScheduler.setThreadNamePrefix("fsignal-sched-");
        taskScheduler.initialize();

        ensureArchiveDir();

        long healthMs = configService.getLong("health.check-interval-ms",    1_800_000L);
        long ntpMs    = configService.getLong("health.ntp-check-interval-ms", 21_600_000L);
        int  runHour  = (int) configService.getLong("retention.run-hour",    2L);
        int  runMin   = (int) configService.getLong("retention.run-minute",  0L);

        scheduleHealth(healthMs);
        scheduleNtp(ntpMs);
        scheduleRetention(runHour, runMin);

        log.info("[SCHED] Zamanlayıcılar başlatıldı — health={}ms ntp={}ms retention={}:{}",
                healthMs, ntpMs, runHour, String.format("%02d", runMin));
    }

    public void scheduleHealth(long intervalMs) {
        cancel("health");
        futures.put("health", taskScheduler.scheduleAtFixedRate(
                healthScheduler::healthCheck, Duration.ofMillis(intervalMs)));
        log.info("[SCHED] Sağlık kontrolü yeniden planlandı: {}ms", intervalMs);
    }

    public void scheduleNtp(long intervalMs) {
        cancel("ntp");
        futures.put("ntp", taskScheduler.scheduleAtFixedRate(
                healthScheduler::ntpCheck, Duration.ofMillis(intervalMs)));
        log.info("[SCHED] NTP kontrolü yeniden planlandı: {}ms", intervalMs);
    }

    public void scheduleRetention(int hour, int minute) {
        cancel("retention");
        String cron = String.format("0 %d %d * * *", minute, hour);
        futures.put("retention", taskScheduler.schedule(
                retentionScheduler::runRetention, new CronTrigger(cron)));
        log.info("[SCHED] Veri saklama yeniden planlandı: cron={}", cron);
    }

    private void ensureArchiveDir() {
        String path = configService.getSync("archive.path", "./archive");
        try {
            Files.createDirectories(Paths.get(path));
            log.info("[SCHED] Arşiv dizini hazır: {}", Paths.get(path).toAbsolutePath());
        } catch (IOException e) {
            log.warn("[SCHED] Arşiv dizini oluşturulamadı: {} — {}", path, e.getMessage());
        }
    }

    private void cancel(String name) {
        ScheduledFuture<?> f = futures.remove(name);
        if (f != null) f.cancel(false);
    }
}
