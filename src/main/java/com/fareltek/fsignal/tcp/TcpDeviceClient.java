package com.fareltek.fsignal.tcp;

import com.fareltek.fsignal.section.Section;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpDeviceClient {

    private static final Logger log = LoggerFactory.getLogger(TcpDeviceClient.class);
    private static final int RECONNECT_SECONDS = 5;
    // If no data received for this many seconds, assume connection is dead (cable unplug etc.)
    private static final int READ_TIMEOUT_SECONDS = 30;

    private final Section section;
    private final TcpConnectionHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connected = new AtomicBoolean(false);

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
                    conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    connected.set(true);
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
                                log.warn("[TCP][{}] Baglanti hatasi: {}", section.getName(), error.getMessage());
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
