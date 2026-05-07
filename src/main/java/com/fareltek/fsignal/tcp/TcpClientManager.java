package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.section.Section;
import com.fareltek.fsignal.section.SectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TcpClientManager {

    private static final Logger log = LoggerFactory.getLogger(TcpClientManager.class);

    private final SectionService sectionService;
    private final TcpConnectionHandler handler;
    private final Map<Integer, TcpDeviceClient> clients = new ConcurrentHashMap<>();

    public TcpClientManager(SectionService sectionService, TcpConnectionHandler handler) {
        this.sectionService = sectionService;
        this.handler = handler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("[TCP] Section baglantilari yukleniyor...");
        sectionService.getAllEnabled()
                .subscribe(
                        this::startClient,
                        e -> log.error("[TCP] Section yukleme hatasi: {}", e.getMessage()),
                        () -> log.info("[TCP] {} section baglantisi baslatildi", clients.size())
                );
    }

    public void startClient(Section s) {
        TcpDeviceClient client = new TcpDeviceClient(s, handler);
        clients.put(s.getId(), client);
        client.start();
    }

    public void stopClient(int sectionId) {
        TcpDeviceClient client = clients.remove(sectionId);
        if (client != null) client.stop();
    }

    public Map<Integer, TcpDeviceClient> getClients() {
        return Collections.unmodifiableMap(clients);
    }
}
