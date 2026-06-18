package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceLogRowDto;
import io.github.yienruuuuu.smartlending.model.PerformanceLogsResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceLogsSummaryDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PerformanceLogsService {

    private final PerformanceSnapshotFileRepository snapshotRepository;
    private final PerformanceCashflowService performanceCashflowService;

    public PerformanceLogsService(
            PerformanceSnapshotFileRepository snapshotRepository,
            PerformanceCashflowService performanceCashflowService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.performanceCashflowService = performanceCashflowService;
    }

    public PerformanceLogsResponseDto getLogs(String account, String range, String type, String q, Integer page, Integer size) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        String normalizedType = normalizeType(type);
        String normalizedQuery = normalizeQuery(q);
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedSize = size == null ? 50 : Math.min(Math.max(size, 1), 200);

        List<PerformanceLogRowDto> allRows = buildRows(normalizedAccount, normalizedRange).stream()
                .filter(row -> matchesType(row, normalizedType))
                .filter(row -> matchesQuery(row, normalizedQuery))
                .sorted(Comparator.comparing(PerformanceLogRowDto::capturedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PerformanceLogRowDto::referenceId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(normalizedPage * normalizedSize, allRows.size());
        int toIndex = Math.min(fromIndex + normalizedSize, allRows.size());
        List<PerformanceLogRowDto> pagedRows = allRows.subList(fromIndex, toIndex);

        return new PerformanceLogsResponseDto(
                normalizedAccount,
                normalizedRange,
                normalizedType,
                normalizedQuery,
                normalizedPage,
                normalizedSize,
                allRows.size(),
                summarize(allRows),
                pagedRows
        );
    }

    private List<PerformanceLogRowDto> buildRows(String account, String range) {
        List<PerformanceLogRowDto> rows = new ArrayList<>();
        rows.addAll(snapshotRows(account, range));
        rows.addAll(cashflowRows(account, range));
        return rows;
    }

    private List<PerformanceLogRowDto> snapshotRows(String account, String range) {
        return singleSnapshotRows(account, range);
    }

    private List<PerformanceLogRowDto> singleSnapshotRows(String account, String range) {
        List<PerformanceSnapshot> snapshots = snapshotRepository.findByAccount(account);
        Instant latest = latestSnapshotTime(snapshots);
        return filterSnapshotsByRange(snapshots, range, latest).stream()
                .map(this::snapshotRow)
                .toList();
    }

    private List<PerformanceLogRowDto> cashflowRows(String account, String range) {
        return performanceCashflowService.getCashflows(account, range).stream()
                .map(this::cashflowRow)
                .toList();
    }

    private PerformanceLogRowDto snapshotRow(PerformanceSnapshot snapshot) {
        return new PerformanceLogRowDto(
                "snapshot",
                snapshot.account(),
                snapshot.capturedAt(),
                "資產快照",
                "snapshot",
                null,
                snapshot.totalWalletAmount(),
                snapshot.idleAmount(),
                snapshot.lentAmount(),
                snapshot.utilizationRatio(),
                "snapshot:" + snapshot.account() + ":" + snapshot.capturedAt().toEpochMilli(),
                snapshot.source(),
                "snapshot",
                "wallet=" + snapshot.currency()
        );
    }

    private PerformanceLogRowDto cashflowRow(PerformanceCashflowEvent event) {
        return new PerformanceLogRowDto(
                "cashflow",
                event.account(),
                event.capturedAt(),
                cashflowTitle(event.type().name().toLowerCase(Locale.ROOT)),
                event.type().name().toLowerCase(Locale.ROOT),
                event.amount(),
                null,
                null,
                null,
                null,
                event.referenceId(),
                event.source(),
                event.rawEventType(),
                event.note()
        );
    }

    private String cashflowTitle(String type) {
        return switch (type) {
            case "deposit" -> "入金";
            case "withdrawal" -> "出金";
            case "internal_transfer_in" -> "內部轉入";
            case "internal_transfer_out" -> "內部轉出";
            default -> "現金流";
        };
    }

    private PerformanceLogsSummaryDto summarize(List<PerformanceLogRowDto> rows) {
        List<PerformanceLogRowDto> snapshots = rows.stream().filter(row -> "snapshot".equals(row.kind()))
                .sorted(Comparator.comparing(PerformanceLogRowDto::capturedAt))
                .toList();
        List<PerformanceLogRowDto> cashflows = rows.stream().filter(row -> "cashflow".equals(row.kind())).toList();
        PerformanceLogRowDto firstSnapshot = snapshots.isEmpty() ? null : snapshots.get(0);
        PerformanceLogRowDto lastSnapshot = snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
        BigDecimal netCashflow = cashflows.stream()
                .map(PerformanceLogRowDto::amount)
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right == null ? BigDecimal.ZERO : right));
        return new PerformanceLogsSummaryDto(
                rows.size(),
                snapshots.size(),
                cashflows.size(),
                netCashflow,
                firstSnapshot == null ? BigDecimal.ZERO : nullSafe(firstSnapshot.totalWalletAmount()),
                lastSnapshot == null ? BigDecimal.ZERO : nullSafe(lastSnapshot.totalWalletAmount()),
                firstSnapshot == null ? null : firstSnapshot.capturedAt(),
                lastSnapshot == null ? null : lastSnapshot.capturedAt()
        );
    }

    private boolean matchesType(PerformanceLogRowDto row, String type) {
        return "all".equals(type) || type.equals(row.type());
    }

    private boolean matchesQuery(PerformanceLogRowDto row, String query) {
        if (query == null) {
            return true;
        }
        return contains(row.referenceId(), query)
                || contains(row.source(), query)
                || contains(row.rawEventType(), query)
                || contains(row.note(), query)
                || contains(row.title(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private List<PerformanceSnapshot> filterSnapshotsByRange(List<PerformanceSnapshot> snapshots, String range, Instant latestTimestamp) {
        if (latestTimestamp == null || snapshots.isEmpty() || "all".equals(range)) {
            return snapshots;
        }
        Instant cutoff = latestTimestamp.minus(duration(range));
        return snapshots.stream()
                .filter(snapshot -> !snapshot.capturedAt().isBefore(cutoff))
                .toList();
    }

    @SafeVarargs
    private final Instant latestSnapshotTime(List<PerformanceSnapshot>... groups) {
        List<Instant> times = new ArrayList<>();
        for (List<PerformanceSnapshot> group : groups) {
            group.stream().map(PerformanceSnapshot::capturedAt).max(Comparator.naturalOrder()).ifPresent(times::add);
        }
        return times.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private Duration duration(String range) {
        return switch (range) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new IllegalArgumentException("range must be one of: 7d, 30d, 90d, all");
        };
    }

    private String normalizeAccount(String account) {
        String normalized = account == null || account.isBlank() ? "main" : account.trim().toLowerCase(Locale.ROOT);
        if (!"main".equals(normalized)) {
            throw new IllegalArgumentException("account must be main");
        }
        return normalized;
    }

    private String normalizeRange(String range) {
        String normalized = range == null || range.isBlank() ? "30d" : range.trim().toLowerCase(Locale.ROOT);
        if (!List.of("7d", "30d", "90d", "all").contains(normalized)) {
            throw new IllegalArgumentException("range must be one of: 7d, 30d, 90d, all");
        }
        return normalized;
    }

    private String normalizeType(String type) {
        String normalized = type == null || type.isBlank() ? "all" : type.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "snapshot", "deposit", "withdrawal", "internal_transfer_in", "internal_transfer_out").contains(normalized)) {
            throw new IllegalArgumentException("type must be one of: all, snapshot, deposit, withdrawal, internal_transfer_in, internal_transfer_out");
        }
        return normalized;
    }

    private String normalizeQuery(String q) {
        return q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
