package com.fareltek.fsignal.api;

import com.fareltek.fsignal.section.Section;
import com.fareltek.fsignal.section.SectionService;
import com.fareltek.fsignal.tcp.TcpClientManager;
import com.fareltek.fsignal.tcp.TcpConnectionHandler;
import com.fareltek.fsignal.tcp.TcpDeviceClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sections")
public class SectionApi {

    private final SectionService        sectionService;
    private final TcpClientManager      tcpClientManager;
    private final TcpConnectionHandler  handler;

    public SectionApi(SectionService sectionService,
                      TcpClientManager tcpClientManager,
                      TcpConnectionHandler handler) {
        this.sectionService   = sectionService;
        this.tcpClientManager = tcpClientManager;
        this.handler          = handler;
    }

    @GetMapping
    public Flux<Map<String, Object>> getAll() {
        Map<Integer, TcpDeviceClient>              clients   = tcpClientManager.getClients();
        Map<Integer, TcpConnectionHandler.SectionStats> stats = handler.getSectionStats();

        return sectionService.getAll().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          s.getId());
            m.put("name",        s.getName());
            m.put("host",        s.getHost());
            m.put("port",        s.getPort());
            m.put("enabled",     s.getEnabled());
            m.put("description", s.getDescription());

            TcpDeviceClient client = clients.get(s.getId());
            m.put("connected", client != null && client.isConnected());

            TcpConnectionHandler.SectionStats st = stats.get(s.getId());
            m.put("reconnects",     st != null ? st.reconnects.get()  : 0);
            m.put("packets",        st != null ? st.packets.get()     : 0L);
            m.put("totalBytes",     st != null ? st.totalBytes.get()  : 0L);
            m.put("connectedSince", st != null && st.connectedSince != null ? st.connectedSince.toString() : null);
            m.put("lastPacket",     st != null && st.lastPacket     != null ? st.lastPacket.toString()     : null);
            return m;
        });
    }

    @PostMapping
    public Mono<Section> create(@RequestBody Section section) {
        return sectionService.save(section)
                .doOnSuccess(saved -> {
                    if (Boolean.TRUE.equals(saved.getEnabled())) tcpClientManager.startClient(saved);
                });
    }

    @PutMapping("/{id}")
    public Mono<Section> update(@PathVariable int id, @RequestBody Section section) {
        section.setId(id);
        tcpClientManager.stopClient(id);
        return sectionService.save(section)
                .doOnSuccess(saved -> {
                    if (Boolean.TRUE.equals(saved.getEnabled())) tcpClientManager.startClient(saved);
                });
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable int id) {
        tcpClientManager.stopClient(id);
        return sectionService.delete(id);
    }
}
