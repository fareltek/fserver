package com.fareltek.fsignal.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.fareltek.fsignal.db.SafetyEvent;
import com.fareltek.fsignal.db.SafetyEventRepository;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private static final Logger log = LoggerFactory.getLogger(ArchiveController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Value("${fsignal.archive.path:./archive}")
    private String archivePath;

    private final SafetyEventRepository repository;

    public ArchiveController(SafetyEventRepository repository) {
        this.repository = repository;
    }

    /** List all files under archive/logs and archive/events */
    @GetMapping("/list")
    public Mono<Map<String, Object>> list() {
        return Mono.fromCallable(() -> {
            Path base = Paths.get(archivePath).toAbsolutePath().normalize();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("basePath", base.toString());
            result.put("logs",   scanDir(base.resolve("logs")));
            result.put("events", scanDir(base.resolve("events")));
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Tail last N lines of a text file (not for .gz) */
    @GetMapping("/tail")
    public Mono<ResponseEntity<List<String>>> tail(
            @RequestParam String f,
            @RequestParam(defaultValue = "200") int n) {
        return Mono.fromCallable(() -> {
            Path target = resolveAndValidate(f);
            if (target == null) return ResponseEntity.badRequest().<List<String>>build();
            if (!Files.exists(target)) return ResponseEntity.notFound().<List<String>>build();
            return ResponseEntity.ok(tailLines(target, n));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** All event dates from DB + whether a pre-built CSV file exists for each */
    @GetMapping("/dates")
    public Flux<Map<String, Object>> eventDates() {
        Path eventsDir = Paths.get(archivePath).toAbsolutePath().normalize().resolve("events");
        return repository.findDistinctEventDates()
                .map(date -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", date.toString());
                    String fileName = "events-" + date + ".csv";
                    Path filePath = eventsDir.resolve(fileName);
                    boolean fileExists = Files.exists(filePath);
                    m.put("hasFile", fileExists);
                    if (fileExists) {
                        try {
                            m.put("size", Files.size(filePath));
                        } catch (IOException ignored) {}
                        m.put("filePath", "events/" + fileName);
                    }
                    return m;
                });
    }

    /** Live log SSE: streams new lines appended to logs/fsignal.log */
    @GetMapping(value = "/live-log", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> liveLog() {
        Path logFile = Paths.get(archivePath).toAbsolutePath().normalize()
                .resolve("logs/fsignal.log");
        AtomicLong offset = new AtomicLong(0L);

        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<String>builder().comment("hb").build());

        Flux<ServerSentEvent<String>> lines = Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> Mono.fromCallable(() -> {
                    List<String> result = new ArrayList<>();
                    if (!Files.exists(logFile)) return result;
                    long len = logFile.toFile().length();
                    long pos = offset.get();
                    if (len < pos) { offset.set(0); pos = 0; }
                    if (len <= pos) return result;
                    try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                        raf.seek(pos);
                        byte[] buf = new byte[(int) Math.min(len - pos, 65536)];
                        int read = raf.read(buf);
                        if (read > 0) {
                            offset.set(pos + read);
                            String chunk = new String(buf, 0, read, java.nio.charset.StandardCharsets.UTF_8);
                            for (String line : chunk.split("\n")) {
                                String trimmed = line.stripTrailing();
                                if (!trimmed.isEmpty()) result.add(trimmed);
                            }
                        }
                    }
                    return result;
                }).subscribeOn(Schedulers.boundedElastic()).onErrorReturn(new ArrayList<>()))
                .flatMapIterable(l -> l)
                .map(line -> ServerSentEvent.<String>builder().data(line).build());

        return Flux.merge(lines, heartbeat);
    }

    /** On-demand CSV export for any date (default: today) */
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportDate(
            @RequestParam(defaultValue = "today") String date) {
        LocalDate day = "today".equals(date)
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.parse(date);
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to   = "today".equals(date)
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : day.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        return repository.findByEventTimeBetweenOrderByEventTimeDesc(from, to)
                .collectList()
                .flatMap(events -> Mono.fromCallable(() -> buildCsvBytes(events))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Disposition",
                                "attachment; filename=\"events-" + day + ".csv\"")
                        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                        .body(bytes));
    }

    private byte[] buildCsvBytes(java.util.List<SafetyEvent> events) {
        StringBuilder sb = new StringBuilder(
                "﻿id,event_time,source_addr,severity,message_type," +
                "device_type,device_id,event_code,event_data,event_flags," +
                "description,acknowledged,acknowledged_by\r\n");
        for (SafetyEvent e : events) {
            sb.append(nvl(e.getId())).append(',')
              .append(nvl(e.getEventTime())).append(',')
              .append(nvl(e.getSourceAddr())).append(',')
              .append(nvl(e.getSeverity())).append(',')
              .append(nvl(e.getMessageType())).append(',')
              .append(nvl(e.getDeviceType())).append(',')
              .append(nvl(e.getDeviceId())).append(',')
              .append(nvl(e.getEventCode())).append(',')
              .append(nvl(e.getEventData())).append(',')
              .append(nvl(e.getEventFlags())).append(',')
              .append('"').append(e.getDescription() != null
                      ? e.getDescription().replace("\"", "\"\"") : "").append('"').append(',')
              .append(nvl(e.getAcknowledged())).append(',')
              .append(nvl(e.getAcknowledgedBy())).append("\r\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String nvl(Object o) { return o != null ? o.toString() : ""; }

    /** Download a file by relative path */
    @GetMapping("/download")
    public Mono<ResponseEntity<byte[]>> download(@RequestParam String f) {
        return Mono.fromCallable(() -> {
            Path target = resolveAndValidate(f);
            if (target == null) return ResponseEntity.badRequest().<byte[]>build();
            if (!Files.exists(target)) return ResponseEntity.notFound().<byte[]>build();

            byte[] bytes = Files.readAllBytes(target);
            String filename = target.getFileName().toString();
            boolean isGzip = filename.endsWith(".gz");
            return ResponseEntity.ok()
                    .contentType(isGzip
                            ? MediaType.APPLICATION_OCTET_STREAM
                            : (filename.endsWith(".csv") ? MediaType.parseMediaType("text/csv") : MediaType.TEXT_PLAIN))
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(bytes);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Path resolveAndValidate(String relativePath) {
        try {
            Path base = Paths.get(archivePath).toAbsolutePath().normalize();
            Path target = base.resolve(relativePath).normalize();
            if (!target.startsWith(base)) {
                log.warn("[Archive] Path traversal attempt: {}", relativePath);
                return null;
            }
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> scanDir(Path dir) {
        List<Map<String, Object>> files = new ArrayList<>();
        if (!Files.exists(dir)) return files;
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name",     p.getFileName().toString());
                info.put("size",     attr.size());
                info.put("modified", FMT.format(attr.lastModifiedTime().toInstant()));
                info.put("path",     Paths.get(archivePath).toAbsolutePath().normalize()
                                         .relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/'));
                files.add(info);
            }
        } catch (IOException e) {
            log.warn("[Archive] Tarama hatasi: {}", e.getMessage());
        }
        files.sort(Comparator.comparing(m -> (String) m.get("name"), Comparator.reverseOrder()));
        return files;
    }

    private List<String> tailLines(Path path, int n) throws IOException {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long length = raf.length();
            if (length == 0) return lines;

            // scan backwards collecting newlines
            List<Long> positions = new ArrayList<>();
            positions.add(length);
            long pos = length - 1;
            while (pos >= 0 && positions.size() <= n) {
                raf.seek(pos);
                if (raf.read() == '\n') positions.add(pos + 1);
                pos--;
            }
            if (positions.size() <= n) positions.add(0L);

            Collections.reverse(positions);
            for (int i = 0; i < positions.size() - 1; i++) {
                raf.seek(positions.get(i));
                int len = (int)(positions.get(i + 1) - positions.get(i));
                byte[] buf = new byte[len];
                raf.readFully(buf);
                String line = new String(buf, java.nio.charset.StandardCharsets.UTF_8).stripTrailing();
                if (!line.isEmpty()) lines.add(line);
            }
        }
        return lines;
    }
}
