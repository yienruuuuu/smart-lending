package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

/**
 * 以 JSONL 檔案保存 performance snapshots，方便直接納入 Git 版本管理。
 */
@Repository
public class PerformanceSnapshotFileRepository {

    private final PerformanceProperties properties;
    private final ObjectMapper objectMapper;

    public PerformanceSnapshotFileRepository(
            PerformanceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void append(PerformanceSnapshot snapshot) {
        Path file = filePath(snapshot.account());
        try {
            Files.createDirectories(file.getParent());
            String line = objectMapper.writeValueAsString(snapshot) + System.lineSeparator();
            Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist performance snapshot for account: " + snapshot.account(), exception);
        }
    }

    public List<PerformanceSnapshot> findByAccount(String account) {
        Path file = filePath(account);
        if (!Files.exists(file)) {
            return List.of();
        }

        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                    .filter(line -> !line.isBlank())
                    .map(this::readLine)
                    .sorted(Comparator.comparing(PerformanceSnapshot::capturedAt))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read performance snapshots for account: " + account, exception);
        }
    }

    private PerformanceSnapshot readLine(String line) {
        try {
            return objectMapper.readValue(line, PerformanceSnapshot.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse performance snapshot line", exception);
        }
    }

    private Path filePath(String account) {
        return Path.of(properties.getStoragePath()).resolve(account + "-fusd-snapshots.jsonl");
    }
}
