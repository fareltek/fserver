package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.section.Section;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpDeviceClient {

    private static final Logger log = LoggerFactory.getLogger(TcpDeviceClient.class);
    private static final int RECONNECT_SECONDS = 5;

    private final Section section;
    private final TcpConnectionHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean firstErrorInCycle = new AtomicBoolean(true);

    public TcpDeviceClient(Section section, TcpConnectionHandler handler) {
        this.section = section;
        this.handler = handler;
    }

    public void start() {
        log.info("[TCP][{}] Baslatiyor: {}", section.getName(), section.sourceAddr());
        connect();
    }

    public void stop() {
        running.set(false);
        if (connected.getAndSet(false)) {
            handler.onDisconnected(section);
        }
    }

    public boolean isConnected() { return connected.get(); }
    public Section getSection() { return section; }

    private void connect() {
        if (!running.get()) return;

        TcpClient.create()
                .host(section.getHost())
                .port(section.getPort())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(conn -> {
                    connected.set(true);
                    firstErrorInCycle.set(true);
                    handler.onConnected(section);
                })
                .doOnDisconnected(conn -> {
                    connected.set(false);
                    handler.onDisconnected(section);
                })
                .handle((inbound, outbound) ->
                        inbound.receive()
                                .asByteArray()
                                .doOnNext(data -> handler.onData(section, data))
                                .then()
                )
                .connect()
                .flatMap(conn -> conn.onDispose())
                .subscribe(
                        null,
                        error -> {
                            if (running.get()) {
                                if (firstErrorInCycle.getAndSet(false)) {
                                    log.warn("[TCP][{}] Baglanti hatasi: {}", section.getName(), error.getMessage());
                                    handler.onConnectionError(section, error.getMessage());
                                }
                                Mono.delay(Duration.ofSeconds(RECONNECT_SECONDS)).subscribe(x -> connect());
                            }
                        },
                        () -> {
                            if (running.get()) {
                                Mono.delay(Duration.ofSeconds(RECONNECT_SECONDS)).subscribe(x -> connect());
                            }
                        }
                );
    }
}
