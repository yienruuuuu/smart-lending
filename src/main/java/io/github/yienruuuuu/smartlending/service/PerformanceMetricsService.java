package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.model.PerformanceLatestSnapshotsDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesPointDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSeriesResponseDto;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import io.github.yienruuuuu.smartlending.model.PerformanceSummaryDto;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * 依 snapshots 計算績效摘要與時間序列。
 */
@Service
public class PerformanceMetricsService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final PerformanceSnapshotFileRepository repository;

    public PerformanceMetricsService(PerformanceSnapshotFileRepository repository) {
        this.repository = repository;
    }

    public PerformanceSummaryDto getSummary(String account, String range) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        List<PerformanceSeriesPointDto> points = buildSeriesPoints(normalizedAccount, normalizedRange);
        if (points.isEmpty()) {
            return emptySummary(normalizedAccount, normalizedRange);
        }

        PerformanceSeriesPointDto first = points.get(0);
        PerformanceSeriesPointDto last = points.get(points.size() - 1);
        BigDecimal startValue = nullSafe(first.totalWalletAmount());
        BigDecimal endValue = nullSafe(last.totalWalletAmount());
        BigDecimal absoluteReturn = endValue.subtract(startValue);
        BigDecimal totalReturnRatio = ratio(absoluteReturn, startValue);
        BigDecimal annualizedReturnRatio = annualizedReturn(startValue, endValue, first.capturedAt(), last.capturedAt());

        return new PerformanceSummaryDto(
                normalizedAccount,
                normalizedRange,
                points.size(),
                first.capturedAt(),
                last.capturedAt(),
                startValue,
                endValue,
                absoluteReturn,
                totalReturnRatio,
                percent(totalReturnRatio),
                annualizedReturnRatio,
                percent(annualizedReturnRatio),
                nullSafe(last.idleAmount()),
                nullSafe(last.offerAmount()),
                nullSafe(last.creditAmount()),
                nullSafe(last.loanAmount()),
                nullSafe(last.lentAmount()),
                nullSafe(last.unsettledInterest()),
                ratio(nullSafe(last.lentAmount()), endValue)
        );
    }

    public PerformanceSeriesResponseDto getSeries(String account, String range) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        List<PerformanceSeriesPointDto> points = buildSeriesPoints(normalizedAccount, normalizedRange);
        return new PerformanceSeriesResponseDto(normalizedAccount, normalizedRange, points.size(), points);
    }

    public PerformanceLatestSnapshotsDto getLatestSnapshots() {
        PerformanceSnapshot main = latest(repository.findByAccount("main"));
        PerformanceSnapshot sub = latest(repository.findByAccount("sub"));
        return new PerformanceLatestSnapshotsDto(main, sub, combine(main, sub));
    }

    private List<PerformanceSeriesPointDto> buildSeriesPoints(String account, String range) {
        return "combined".equals(account)
                ? combinedSeries(range)
                : singleAccountSeries(account, range);
    }

    private List<PerformanceSeriesPointDto> singleAccountSeries(String account, String range) {
        List<PerformanceSnapshot> snapshots = repository.findByAccount(account);
        Instant latestTimestamp = latestTimestamp(snapshots);
        return filterByRange(snapshots, range, latestTimestamp).stream()
                .map(this::toPoint)
                .toList();
    }

    private List<PerformanceSeriesPointDto> combinedSeries(String range) {
        List<PerformanceSnapshot> mainSnapshots = repository.findByAccount("main");
        List<PerformanceSnapshot> subSnapshots = repository.findByAccount("sub");
        Instant latestTimestamp = latestTimestamp(mainSnapshots, subSnapshots);
        if (latestTimestamp == null) {
            return List.of();
        }

        List<PerformanceSnapshot> filteredMain = filterByRange(mainSnapshots, range, latestTimestamp);
        List<PerformanceSnapshot> filteredSub = filterByRange(subSnapshots, range, latestTimestamp);
        LinkedHashSet<Instant> timeline = new LinkedHashSet<>();
        filteredMain.stream().map(PerformanceSnapshot::capturedAt).forEach(timeline::add);
        filteredSub.stream().map(PerformanceSnapshot::capturedAt).forEach(timeline::add);

        if (timeline.isEmpty()) {
            return List.of();
        }

        NavigableMap<Instant, PerformanceSnapshot> mainByTime = indexByTime(mainSnapshots);
        NavigableMap<Instant, PerformanceSnapshot> subByTime = indexByTime(subSnapshots);
        return timeline.stream()
                .sorted()
                .map(timestamp -> combine(
                        floorValue(mainByTime, timestamp),
                        floorValue(subByTime, timestamp)
                ))
                .filter(java.util.Objects::nonNull)
                .map(this::toPoint)
                .toList();
    }

    private NavigableMap<Instant, PerformanceSnapshot> indexByTime(List<PerformanceSnapshot> snapshots) {
        NavigableMap<Instant, PerformanceSnapshot> map = new TreeMap<>();
        for (PerformanceSnapshot snapshot : snapshots) {
            map.put(snapshot.capturedAt(), snapshot);
        }
        return map;
    }

    private PerformanceSnapshot floorValue(NavigableMap<Instant, PerformanceSnapshot> map, Instant timestamp) {
        var entry = map.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }

    private List<PerformanceSnapshot> filterByRange(List<PerformanceSnapshot> snapshots, String range, Instant latestTimestamp) {
        if (latestTimestamp == null || snapshots.isEmpty() || "all".equals(range)) {
            return snapshots;
        }

        Duration duration = switch (range) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new IllegalArgumentException("Unsupported range: " + range);
        };

        Instant cutoff = latestTimestamp.minus(duration);
        return snapshots.stream()
                .filter(snapshot -> !snapshot.capturedAt().isBefore(cutoff))
                .toList();
    }

    private PerformanceSeriesPointDto toPoint(PerformanceSnapshot snapshot) {
        return new PerformanceSeriesPointDto(
                snapshot.capturedAt(),
                nullSafe(snapshot.totalWalletAmount()),
                nullSafe(snapshot.idleAmount()),
                nullSafe(snapshot.offerAmount()),
                nullSafe(snapshot.creditAmount()),
                nullSafe(snapshot.loanAmount()),
                nullSafe(snapshot.lentAmount()),
                nullSafe(snapshot.unsettledInterest())
        );
    }

    private PerformanceSnapshot combine(PerformanceSnapshot main, PerformanceSnapshot sub) {
        if (main == null && sub == null) {
            return null;
        }

        Instant capturedAt = main == null ? sub.capturedAt() : sub == null ? main.capturedAt() : laterOf(main.capturedAt(), sub.capturedAt());
        BigDecimal totalWalletAmount = add(main == null ? null : main.totalWalletAmount(), sub == null ? null : sub.totalWalletAmount());
        BigDecimal idleAmount = add(main == null ? null : main.idleAmount(), sub == null ? null : sub.idleAmount());
        BigDecimal offerAmount = add(main == null ? null : main.offerAmount(), sub == null ? null : sub.offerAmount());
        BigDecimal creditAmount = add(main == null ? null : main.creditAmount(), sub == null ? null : sub.creditAmount());
        BigDecimal loanAmount = add(main == null ? null : main.loanAmount(), sub == null ? null : sub.loanAmount());
        BigDecimal lentAmount = add(main == null ? null : main.lentAmount(), sub == null ? null : sub.lentAmount());
        BigDecimal unsettledInterest = add(main == null ? null : main.unsettledInterest(), sub == null ? null : sub.unsettledInterest());

        return new PerformanceSnapshot(
                "combined",
                "fUSD",
                "USD",
                capturedAt,
                totalWalletAmount,
                idleAmount,
                offerAmount,
                creditAmount,
                loanAmount,
                lentAmount,
                unsettledInterest,
                ratio(lentAmount, totalWalletAmount),
                "aggregated"
        );
    }

    private PerformanceSummaryDto emptySummary(String account, String range) {
        return new PerformanceSummaryDto(
                account,
                range,
                0,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private PerformanceSnapshot latest(List<PerformanceSnapshot> snapshots) {
        return snapshots.stream()
                .max(Comparator.comparing(PerformanceSnapshot::capturedAt))
                .orElse(null);
    }

    @SafeVarargs
    private Instant latestTimestamp(List<PerformanceSnapshot>... snapshotsGroups) {
        List<Instant> timestamps = new ArrayList<>();
        for (List<PerformanceSnapshot> group : snapshotsGroups) {
            PerformanceSnapshot latest = latest(group);
            if (latest != null) {
                timestamps.add(latest.capturedAt());
            }
        }
        return timestamps.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private BigDecimal annualizedReturn(BigDecimal startValue, BigDecimal endValue, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return BigDecimal.ZERO;
        }
        if (startValue.compareTo(BigDecimal.ZERO) <= 0 || endValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        double days = Duration.between(startAt, endAt).toSeconds() / 86400d;
        if (days <= 0d) {
            return BigDecimal.ZERO;
        }

        double annualized = Math.pow(endValue.divide(startValue, MATH_CONTEXT).doubleValue(), 365d / days) - 1d;
        if (!Double.isFinite(annualized)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(annualized).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal ratio) {
        return ratio.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nullSafe(numerator).divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return nullSafe(left).add(nullSafe(right));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Instant laterOf(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private String normalizeAccount(String account) {
        String normalized = account == null || account.isBlank() ? "combined" : account.trim().toLowerCase(Locale.ROOT);
        if (!List.of("main", "sub", "combined").contains(normalized)) {
            throw new IllegalArgumentException("account must be one of: main, sub, combined");
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
}
