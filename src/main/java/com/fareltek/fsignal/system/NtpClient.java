package com.fareltek.fsignal.system;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Minimal SNTP client (RFC 4330).
 * Queries a UDP NTP server on port 123 and returns clock offset vs system time.
 */
public class NtpClient {

    private static final int  NTP_PORT         = 123;
    private static final long NTP_EPOCH_OFFSET = 2208988800L; // seconds 1900→1970

    public record Result(long offsetMs, long rttMs, String server) {
        public boolean isDrifted(long thresholdMs) {
            return Math.abs(offsetMs) > thresholdMs;
        }
        public String summary() {
            String sign = offsetMs >= 0 ? "+" : "";
            return "offset=" + sign + offsetMs + "ms rtt=" + rttMs + "ms sunucu=" + server;
        }
    }

    public static Result query(String host, int timeoutMs) throws Exception {
        byte[] buf = new byte[48];
        // LI=0 (no warning), VN=4 (version 4), Mode=3 (client) → 0x23
        buf[0] = 0x23;

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            InetAddress addr    = InetAddress.getByName(host);
            DatagramPacket req  = new DatagramPacket(buf, buf.length, addr, NTP_PORT);

            long t1 = System.currentTimeMillis();
            socket.send(req);

            DatagramPacket resp = new DatagramPacket(new byte[48], 48);
            socket.receive(resp);
            long t4 = System.currentTimeMillis();

            byte[] data = resp.getData();
            long t2 = readNtpTimestampMs(data, 32); // Receive timestamp
            long t3 = readNtpTimestampMs(data, 40); // Transmit timestamp

            // Clock offset = ((T2-T1) + (T3-T4)) / 2
            long offsetMs = ((t2 - t1) + (t3 - t4)) / 2;
            long rttMs    = (t4 - t1) - (t3 - t2);

            return new Result(offsetMs, Math.max(0, rttMs), host);
        }
    }

    private static long readNtpTimestampMs(byte[] data, int offset) {
        long seconds = 0;
        for (int i = offset; i < offset + 4; i++) {
            seconds = (seconds << 8) | (data[i] & 0xFF);
        }
        long fraction = 0;
        for (int i = offset + 4; i < offset + 8; i++) {
            fraction = (fraction << 8) | (data[i] & 0xFF);
        }
        long ms = (fraction * 1000L) >>> 32;
        return (seconds - NTP_EPOCH_OFFSET) * 1000L + ms;
    }
}
