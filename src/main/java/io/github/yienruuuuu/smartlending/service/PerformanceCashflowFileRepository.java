package io.github.yienruuuuu.smartlending.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

@Repository
public class PerformanceCashflowFileRepository {

    private static final Comparator<PerformanceCashflowEvent> CASHFLOW_ORDER = Comparator
            .comparing(PerformanceCashflowEvent::capturedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PerformanceCashflowEvent::referenceId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final PerformanceProperties properties;
    private final ObjectMapper objectMapper;

    public PerformanceCashflowFileRepository(
            PerformanceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void merge(String account, List<PerformanceCashflowEvent> events) {
        Map<String, PerformanceCashflowEvent> merged = new LinkedHashMap<>();
        findByAccount(account).forEach(event -> merged.put(stableReferenceId(event), event));
        events.stream()
                .map(this::withFallbackReferenceId)
                .forEach(event -> merged.put(stableReferenceId(event), event));
        writeAll(account, merged.values().stream()
                .sorted(CASHFLOW_ORDER)
                .toList());
    }

    public List<PerformanceCashflowEvent> findByAccount(String account) {
        Path file = filePath(account);
        if (!Files.exists(file)) {
            return List.of();
        }

        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                    .filter(line -> !line.isBlank())
                    .map(this::readLine)
                    .map(this::withFallbackReferenceId)
                    .sorted(CASHFLOW_ORDER)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read performance cashflows for account: " + account, exception);
        }
    }

    private void writeAll(String account, List<PerformanceCashflowEvent> events) {
        Path file = filePath(account);
        try {
            Files.createDirectories(file.getParent());
            StringBuilder builder = new StringBuilder();
            for (PerformanceCashflowEvent event : events) {
                builder.append(objectMapper.writeValueAsString(event)).append(System.lineSeparator());
            }
            Files.writeString(
                    file,
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist performance cashflows for account: " + account, exception);
        }
    }

    private PerformanceCashflowEvent readLine(String line) {
        try {
            return objectMapper.readValue(line, PerformanceCashflowEvent.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse performance cashflow line", exception);
        }
    }

    private PerformanceCashflowEvent withFallbackReferenceId(PerformanceCashflowEvent event) {
        if (event.referenceId() != null && !event.referenceId().isBlank()) {
            return event;
        }
        return new PerformanceCashflowEvent(
                event.account(),
                event.symbol(),
                event.currency(),
                event.capturedAt(),
                event.amount(),
                event.type(),
                stableReferenceId(event),
                event.counterparty(),
                event.source(),
                event.rawEventType(),
                event.note()
        );
    }

    private String stableReferenceId(PerformanceCashflowEvent event) {
        if (event.referenceId() != null && !event.referenceId().isBlank()) {
            return event.referenceId();
        }
        return "%s:%s:%s:%s:%s".formatted(
                nullSafe(event.account()),
                event.capturedAt() == null ? "-" : event.capturedAt().toEpochMilli(),
                event.amount() == null ? "-" : event.amount().stripTrailingZeros().toPlainString(),
                event.type() == null ? "-" : event.type().name(),
                Integer.toHexString(Objects.hash(event.source(), event.rawEventType(), event.note(), event.counterparty()))
        );
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Path filePath(String account) {
        return Path.of(properties.getStoragePath()).resolve(account + "-fusd-cashflows.jsonl");
    }
}
