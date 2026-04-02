package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.FundingStateBaseline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Repository;

/**
 * 保存 funding 狀態通知的 baseline。
 */
@Repository
public class FundingStateBaselineRepository {

    private final PerformanceProperties performanceProperties;
    private final ObjectMapper objectMapper;

    public FundingStateBaselineRepository(
            PerformanceProperties performanceProperties,
            ObjectMapper objectMapper
    ) {
        this.performanceProperties = performanceProperties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public FundingStateBaseline load() {
        Path file = filePath();
        if (!Files.exists(file)) {
            return new FundingStateBaseline(null, null);
        }

        try {
            return objectMapper.readValue(file.toFile(), FundingStateBaseline.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load funding state baseline", exception);
        }
    }

    public void save(FundingStateBaseline baseline) {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, objectMapper.writeValueAsString(baseline), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist funding state baseline", exception);
        }
    }

    private Path filePath() {
        Path performanceDir = Path.of(performanceProperties.getStoragePath());
        Path dataDir = performanceDir.getParent() == null ? Path.of("data") : performanceDir.getParent();
        return dataDir.resolve("notifications").resolve("funding-state.json");
    }
}
