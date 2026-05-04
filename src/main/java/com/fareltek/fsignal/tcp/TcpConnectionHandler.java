package com.fareltek.fsignal.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
public class TcpConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(TcpConnectionHandler.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

    private final Sinks.Many<String> dataSink = Sinks.many().multicast().onBackpressureBuffer();

    public void onData(String remoteAddr, byte[] data) {
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String hex = HEX.formatHex(data);

        String logLine = String.format("[%s] %s | %d bytes | %s", timestamp, remoteAddr, data.length, hex);
        log.info("[TCP-DATA] {}", logLine);

        dataSink.tryEmitNext(logLine);
    }

    public Flux<String> getDataStream() {
        return dataSink.asFlux();
    }
}
