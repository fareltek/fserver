package com.fareltek.fsignal.system;

import com.fareltek.fsignal.db.SafetyEventService;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final SafetyEventService     eventService;
    private final DatabaseClient         dbClient;

    public SystemConfigService(SystemConfigRepository repository,
                               SafetyEventService eventService,
                               ConnectionFactory connectionFactory) {
        this.repository   = repository;
        this.eventService = eventService;
        this.dbClient     = DatabaseClient.create(connectionFactory);
    }

    public Flux<SystemConfig> getAll() {
        return repository.findAllOrdered();
    }

    public Mono<SystemConfig> get(String key) {
        return repository.findById(key);
    }

    /**
     * Blocking fetch — safe ONLY on @Scheduled / ApplicationRunner threads,
     * never on Netty event-loop or reactive chains.
     */
    public String getSync(String key, String defaultValue) {
        try {
            return repository.findById(key)
                    .map(SystemConfig::getConfigValue)
                    .defaultIfEmpty(defaultValue)
                    .block(Duration.ofSeconds(3));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        try { return Long.parseLong(getSync(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public Mono<SystemConfig> update(String key, String value, String actor) {
        if (value == null || value.isBlank())
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Değer boş olamaz."));
        return repository.findById(key)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Ayar bulunamadı: " + key)))
                .flatMap(existing ->
                        dbClient.sql("UPDATE system_config SET config_value=:v, updated_at=NOW(), updated_by=:by WHERE config_key=:k")
                                .bind("v", value).bind("by", actor).bind("k", key)
                                .fetch().rowsUpdated()
                                .then(repository.findById(key)))
                .flatMap(cfg -> eventService.saveSystemEvent("SYSTEM", "INFO",
                        "Sistem ayarı değiştirildi: " + key + " = " + value + " | İşlem: " + actor)
                        .thenReturn(cfg));
    }
}
