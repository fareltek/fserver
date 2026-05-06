package com.fareltek.fsignal.tcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public record TcpDataEvent(
        String  type,
        String  timestamp,
        Integer sectionId,
        String  sectionName,
        String  remoteAddr,
        int     byteCount,
        String  hex,
        String  ascii,
        // FA·51 parsed fields (null for non-data events)
        String  packetType,
        String  severity,
        Integer sequence,
        Integer srcId,
        Integer eventCode,
        Integer eventData,
        String  description
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final HexFormat HEX_FMT = HexFormat.ofDelimiter(" ").withUpperCase();

    public static TcpDataEvent fromData(Integer sectionId, String sectionName,
                                        String remoteAddr, byte[] data,
                                        Fa51Parser.ParsedPacket pkt) {
        return new TcpDataEvent(
                "DATA", LocalDateTime.now().format(FMT),
                sectionId, sectionName, remoteAddr,
                data.length, HEX_FMT.formatHex(data), toAscii(data),
                pkt != null ? pkt.messageType()      : null,
                pkt != null ? pkt.severity()         : null,
                pkt != null ? pkt.sequence()         : null,
                pkt != null ? pkt.sourceId()         : null,
                pkt != null ? pkt.eventCode()        : null,
                pkt != null ? pkt.eventData()        : null,
                pkt != null ? Fa51Parser.describe(pkt) : null
        );
    }

    public static TcpDataEvent connectionEvent(Integer sectionId, String sectionName,
                                               String remoteAddr, String type) {
        return new TcpDataEvent(type, LocalDateTime.now().format(FMT),
                sectionId, sectionName, remoteAddr, 0, "", "",
                null, null, null, null, null, null, null);
    }

    public static TcpDataEvent heartbeat() {
        return new TcpDataEvent("HEARTBEAT", LocalDateTime.now().format(FMT),
                null, null, null, 0, "", "",
                null, null, null, null, null, null, null);
    }

    private static String toAscii(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) sb.append((b >= 32 && b < 127) ? (char) b : '.');
        return sb.toString();
    }
}
