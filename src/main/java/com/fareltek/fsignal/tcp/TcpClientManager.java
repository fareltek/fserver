package com.fareltek.fsignal.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;

import java.time.Duration;

@Component
public class TcpClientManager {

    private static final Logger log = LoggerFactory.getLogger(TcpClientManager.class);

    @Value("${fsignal.waveshare.host:192.168.55.50}")
    private String host;

    @Value("${fsignal.waveshare.port:4001}")
    private int port;

    @Value("${fsignal.waveshare.reconnect-seconds:5}")
    private int reconnectSeconds;

    private final TcpConnectionHandler handler;

    public TcpClientManager(TcpConnectionHandler handler) {
        this.handler = handler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("========================================");
        log.info("  FSignal TCP Client (SIL-2)");
        log.info("  Hedef: {}:{}", host, port);
        log.info("  Yeniden baglanti: {}s", reconnectSeconds);
        log.info("========================================");
        connect();
    }

    private void connect() {
        connectOnce()
                .subscribe(
                        null,
                        error -> {
                            log.error("[TCP] Baglanti hatasi: {} — {}s sonra yeniden denenecek",
                                    error.getMessage(), reconnectSeconds);
                            scheduleReconnect();
                        },
                        () -> {
                            log.warn("[TCP] Baglanti kapandi — {}s sonra yeniden denenecek", reconnectSeconds);
                            scheduleReconnect();
                        }
                );
    }

    private void scheduleReconnect() {
        Mono.delay(Duration.ofSeconds(reconnectSeconds))
                .subscribe(x -> connect());
    }

    private Mono<Void> connectOnce() {
        return TcpClient.create()
                .host(host)
                .port(port)
                .doOnConnected(conn -> {
                    String addr = host + ":" + port;
                    log.info("[TCP] Waveshare baglantisi kuruldu: {}", addr);
                    handler.onConnected(addr);
                })
                .doOnDisconnected(conn -> {
                    log.warn("[TCP] Waveshare baglantisi kesildi: {}:{}", host, port);
                    handler.onDisconnected();
                })
                .handle((inbound, outbound) ->
                        inbound.receive()
                                .asByteArray()
                                .doOnNext(handler::onData)
                                .then()
                )
                .connect()
                .then();
    }
}
