package com.fareltek.fsignal.tcp;

/**
 * FA·51 binary protocol parser.
 * Frame: FA 51 [TYPE:1] [SEC_ID:2 BE] [SEQ:4 BE] [LEN:1] [DATA:N] [XOR:1]
 */
public class Fa51Parser {

    public record ParsedPacket(
            String  messageType,
            String  severity,
            int     sourceId,
            int     sequence,
            byte[]  data,
            Integer eventCode,
            Integer eventData,
            Integer eventFlags,
            boolean checksumValid
    ) {}

    public static ParsedPacket parse(byte[] raw) {
        if (raw == null || raw.length < 10) return null;
        if ((raw[0] & 0xFF) != 0xFA || (raw[1] & 0xFF) != 0x51) return null;

        int type    = raw[2] & 0xFF;
        int srcId   = ((raw[3] & 0xFF) << 8) | (raw[4] & 0xFF);
        int seq     = ((raw[5] & 0xFF) << 24) | ((raw[6] & 0xFF) << 16)
                    | ((raw[7] & 0xFF) <<  8) |  (raw[8] & 0xFF);
        int dataLen = raw[9] & 0xFF;

        if (raw.length < 10 + dataLen + 1) return null;

        byte[] data = new byte[dataLen];
        System.arraycopy(raw, 10, data, 0, dataLen);

        byte xor = 0;
        for (int i = 0; i < 10 + dataLen; i++) xor ^= raw[i];
        boolean csOk = (xor == raw[10 + dataLen]);

        String msgType = switch (type) {
            case 0x01 -> "DATA";
            case 0x02 -> "WARNING";
            case 0x03 -> "ALARM";
            case 0x04 -> "CRITICAL";
            case 0x05 -> "HEARTBEAT";
            default   -> "UNKNOWN";
        };
        String severity = switch (type) {
            case 0x02 -> "WARNING";
            case 0x03 -> "ALARM";
            case 0x04 -> "CRITICAL";
            default   -> "INFO";
        };

        Integer eventCode  = dataLen >= 1 ? (data[0] & 0xFF) : null;
        Integer eventData  = dataLen >= 2 ? (data[1] & 0xFF) : null;
        Integer eventFlags = dataLen >= 4 ? (((data[2] & 0xFF) << 8) | (data[3] & 0xFF)) : null;

        return new ParsedPacket(msgType, severity, srcId, seq, data,
                eventCode, eventData, eventFlags, csOk);
    }
}
