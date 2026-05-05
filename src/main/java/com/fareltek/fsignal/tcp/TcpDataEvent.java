package com.fareltek.fsignal.tcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public record TcpDataEvent(
        String timestamp,
        String remoteAddr,
        int byteCount,
        String hex,
        String ascii
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final HexFormat HEX_FMT = HexFormat.ofDelimiter(" ").withUpperCase();

    public static TcpDataEvent from(String remoteAddr, byte[] data) {
        String hex = HEX_FMT.formatHex(data);
        String ascii = toAscii(data);
        return new TcpDataEvent(
                LocalDateTime.now().format(FMT),
                remoteAddr,
                data.length,
                hex,
                ascii
        );
    }

    public static TcpDataEvent heartbeat() {
        return new TcpDataEvent(LocalDateTime.now().format(FMT), "", 0, "", "");
    }

    private static String toAscii(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append((b >= 32 && b < 127) ? (char) b : '.');
        }
        return sb.toString();
    }
}
