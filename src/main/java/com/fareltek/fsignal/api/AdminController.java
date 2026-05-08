package com.fareltek.fsignal.api;

import com.fareltek.fsignal.db.SafetyEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SafetyEventService eventService;

    public AdminController(SafetyEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/restart")
    public Mono<Map<String, Object>> restart(Authentication auth) {
        String actor = auth != null ? auth.getName() : "system";
        log.warn("[ADMIN] Sunucu yeniden başlatma isteği — kullanıcı: {}", actor);
        return eventService.saveSystemEvent("SYSTEM", "WARNING",
                "Sunucu yeniden başlatılıyor | İşlem: " + actor)
                .thenReturn(Map.<String, Object>of("status", "restarting"))
                .doOnSuccess(m -> {
                    Thread t = new Thread(() -> {
                        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                        System.exit(0);
                    });
                    t.setDaemon(true);
                    t.start();
                });
    }
}
