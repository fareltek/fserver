package com.fareltek.fsignal.tcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public record TcpDataEvent(
        String type,
        String timestamp,
        Integer sectionId,
        String sectionName,
        String remoteAddr,
        int byteCount,
        String hex,
        String ascii
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final HexFormat HEX_FMT = HexFormat.ofDelimiter(" ").withUpperCase();

    public static TcpDataEvent fromData(Integer sectionId, String sectionName,
                                        String remoteAddr, byte[] data) {
        return new TcpDataEvent("DATA", LocalDateTime.now().format(FMT),
                sectionId, sectionName, remoteAddr,
                data.length, HEX_FMT.formatHex(data), toAscii(data));
    }

    public static TcpDataEvent connectionEvent(Integer sectionId, String sectionName,
                                               String remoteAddr, String type) {
        return new TcpDataEvent(type, LocalDateTime.now().format(FMT),
                sectionId, sectionName, remoteAddr, 0, "", "");
    }

    public static TcpDataEvent heartbeat() {
        return new TcpDataEvent("HEARTBEAT", LocalDateTime.now().format(FMT),
                null, null, null, 0, "", "");
    }

    private static String toAscii(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append((b >= 32 && b < 127) ? (char) b : '.');
        return sb.toString();
    }
}
