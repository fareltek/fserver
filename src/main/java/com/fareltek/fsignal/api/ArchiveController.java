package com.fareltek.fsignal.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    private static final Logger log = LoggerFactory.getLogger(ArchiveController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Value("${fsignal.archive.path:./archive}")
    private String archivePath;

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
