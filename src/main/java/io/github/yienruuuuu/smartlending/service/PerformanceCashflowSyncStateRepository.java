package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Repository;

@Repository
public class PerformanceCashflowSyncStateRepository {

    private final PerformanceProperties properties;
    private final ObjectMapper objectMapper;

    public PerformanceCashflowSyncStateRepository(
            PerformanceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public PerformanceCashflowSyncState load() {
        Path file = filePath();
        if (!Files.exists(file)) {
            return new PerformanceCashflowSyncState(null, null);
        }
        try {
            return objectMapper.readValue(file.toFile(), PerformanceCashflowSyncState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load performance cashflow sync state", exception);
        }
    }

    public void save(PerformanceCashflowSyncState state) {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, objectMapper.writeValueAsString(state), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist performance cashflow sync state", exception);
        }
    }

    private Path filePath() {
        return Path.of(properties.getStoragePath()).resolve("cashflow-sync-state.json");
    }
}
