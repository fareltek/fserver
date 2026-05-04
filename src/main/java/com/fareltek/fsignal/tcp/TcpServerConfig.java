package com.fareltek.fsignal.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

@Configuration
public class TcpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(TcpServerConfig.class);

    @Value("${fsignal.tcp.port:5000}")
    private int tcpPort;

    @Bean
    public DisposableServer tcpDisposableServer(TcpConnectionHandler handler) {
        DisposableServer server = TcpServer.create()
                .host("0.0.0.0")
                .port(tcpPort)
                .handle((inbound, outbound) -> {
                    String remoteAddr = inbound.channel().remoteAddress().toString();
                    log.info("[TCP] Yeni bağlantı: {}", remoteAddr);

                    return inbound.receive()
                            .asByteArray()
                            .doOnNext(bytes -> handler.onData(remoteAddr, bytes))
                            .doOnComplete(() -> log.info("[TCP] Bağlantı kapandı: {}", remoteAddr))
                            .doOnError(e -> log.error("[TCP] Hata ({}): {}", remoteAddr, e.getMessage()))
                            .then();
                })
                .bindNow();

        log.info("========================================");
        log.info("  FSignal TCP Server");
        log.info("  Port: {}", tcpPort);
        log.info("  Waveshare RS485/ETH bağlantısı bekleniyor...");
        log.info("========================================");

        return server;
    }
}
