package com.fareltek.fsignal.system;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    private final SystemConfigService  configService;
    private final ScheduledTaskManager scheduledTaskManager;

    public SystemConfigController(SystemConfigService configService,
                                  ScheduledTaskManager scheduledTaskManager) {
        this.configService       = configService;
        this.scheduledTaskManager = scheduledTaskManager;
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> getAll() {
        return configService.getAll().map(this::toDto).collectList();
    }

    @PutMapping("/{key}")
    public Mono<Map<String, Object>> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String value = body.get("value");
        if (value == null)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "value alanı zorunludur."));
        String actor = auth != null ? auth.getName() : "system";

        return configService.update(key, value, actor)
                .flatMap(cfg -> applyLive(key, value).thenReturn(cfg))
                .map(this::toDto);
    }

    private Mono<Void> applyLive(String key, String value) {
        return switch (key) {
            case "health.check-interval-ms" -> {
                scheduledTaskManager.scheduleHealth(parseLong(value, 1_800_000L));
                yield Mono.empty();
            }
            case "health.ntp-check-interval-ms" -> {
                scheduledTaskManager.scheduleNtp(parseLong(value, 21_600_000L));
                yield Mono.empty();
            }
            case "retention.run-hour", "retention.run-minute" ->
                Mono.zip(
                    configService.get("retention.run-hour").defaultIfEmpty(defaultCfg("2")),
                    configService.get("retention.run-minute").defaultIfEmpty(defaultCfg("0"))
                ).doOnNext(t -> scheduledTaskManager.scheduleRetention(
                        parseInt(t.getT1().getConfigValue(), 2),
                        parseInt(t.getT2().getConfigValue(), 0)))
                .then();
            default -> Mono.empty();
        };
    }

    private SystemConfig defaultCfg(String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigValue(value);
        return c;
    }

    private long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private Map<String, Object> toDto(SystemConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key",             c.getConfigKey());
        m.put("value",           c.getConfigValue());
        m.put("description",     c.getDescription());
        m.put("group",           c.getConfigGroup());
        m.put("requiresRestart", Boolean.TRUE.equals(c.getRequiresRestart()));
        m.put("updatedAt",       c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        m.put("updatedBy",       c.getUpdatedBy());
        return m;
    }
}
