package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
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
 * 依 snapshots 與 cashflows 計算績效摘要與時間序列。
 */
@Service
public class PerformanceMetricsService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final int XIRR_MAX_ITERATIONS = 50;
    private static final double XIRR_TOLERANCE = 1.0e-7d;

    private final PerformanceSnapshotFileRepository repository;
    private final PerformanceCashflowFileRepository cashflowRepository;

    public PerformanceMetricsService(
            PerformanceSnapshotFileRepository repository,
            PerformanceCashflowFileRepository cashflowRepository
    ) {
        this.repository = repository;
        this.cashflowRepository = cashflowRepository;
    }

    public PerformanceSummaryDto getSummary(String account, String range) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        List<PerformanceSeriesPointDto> points = buildSeriesPoints(normalizedAccount, normalizedRange);
        List<PerformanceCashflowEvent> cashflows = buildCashflows(normalizedAccount, normalizedRange);
        if (points.isEmpty()) {
            return emptySummary(normalizedAccount, normalizedRange, cashflows.size(), netCashflow(cashflows));
        }

        PerformanceSeriesPointDto first = points.get(0);
        PerformanceSeriesPointDto last = points.get(points.size() - 1);
        BigDecimal startValue = nullSafe(first.totalWalletAmount());
        BigDecimal endValue = nullSafe(last.totalWalletAmount());
        BigDecimal absoluteReturn = endValue.subtract(startValue);
        BigDecimal totalReturnRatio = ratio(absoluteReturn, startValue);
        BigDecimal annualizedReturnRatio = annualizedReturn(startValue, endValue, first.capturedAt(), last.capturedAt());
        BigDecimal twrReturnRatio = calculateTwr(points, cashflows, "combined".equals(normalizedAccount));
        BigDecimal twrAnnualizedReturnRatio = annualizeRatio(twrReturnRatio, first.capturedAt(), last.capturedAt());
        BigDecimal xirrRatio = "combined".equals(normalizedAccount)
                ? null
                : calculateXirr(first.capturedAt(), last.capturedAt(), startValue, endValue, cashflows);

        return new PerformanceSummaryDto(
                normalizedAccount,
                normalizedRange,
                points.size(),
                cashflows.size(),
                first.capturedAt(),
                last.capturedAt(),
                startValue,
                endValue,
                absoluteReturn,
                totalReturnRatio,
                percent(totalReturnRatio),
                annualizedReturnRatio,
                percent(annualizedReturnRatio),
                twrReturnRatio,
                percent(twrReturnRatio),
                twrAnnualizedReturnRatio,
                percent(twrAnnualizedReturnRatio),
                xirrRatio,
                xirrRatio == null ? null : percent(xirrRatio),
                netCashflow(cashflows),
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

    private List<PerformanceCashflowEvent> buildCashflows(String account, String range) {
        List<PerformanceCashflowEvent> cashflows = "combined".equals(account)
                ? combinedCashflows()
                : cashflowRepository.findByAccount(account);
        return filterCashflowsByRange(cashflows, range);
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

    private List<PerformanceCashflowEvent> combinedCashflows() {
        return java.util.stream.Stream.concat(
                        cashflowRepository.findByAccount("main").stream(),
                        cashflowRepository.findByAccount("sub").stream()
                )
                .filter(event -> !event.type().isInternalTransfer())
                .sorted(Comparator.comparing(PerformanceCashflowEvent::capturedAt))
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

        Duration duration = duration(range);
        Instant cutoff = latestTimestamp.minus(duration);
        return snapshots.stream()
                .filter(snapshot -> !snapshot.capturedAt().isBefore(cutoff))
                .toList();
    }

    private List<PerformanceCashflowEvent> filterCashflowsByRange(List<PerformanceCashflowEvent> cashflows, String range) {
        if (cashflows.isEmpty() || "all".equals(range)) {
            return cashflows;
        }

        Instant latestTimestamp = cashflows.stream()
                .map(PerformanceCashflowEvent::capturedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latestTimestamp == null) {
            return cashflows;
        }

        Instant cutoff = latestTimestamp.minus(duration(range));
        return cashflows.stream()
                .filter(event -> !event.capturedAt().isBefore(cutoff))
                .toList();
    }

    private Duration duration(String range) {
        return switch (range) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new IllegalArgumentException("Unsupported range: " + range);
        };
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

    private PerformanceSummaryDto emptySummary(String account, String range, int cashflowCount, BigDecimal netCashflow) {
        return new PerformanceSummaryDto(
                account,
                range,
                0,
                cashflowCount,
                null,
                null,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                null,
                null,
                netCashflow,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO
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
            return ZERO;
        }
        if (startValue.compareTo(ZERO) <= 0 || endValue.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        double days = Duration.between(startAt, endAt).toSeconds() / 86400d;
        if (days <= 0d) {
            return ZERO;
        }

        double annualized = Math.pow(endValue.divide(startValue, MATH_CONTEXT).doubleValue(), 365d / days) - 1d;
        if (!Double.isFinite(annualized)) {
            return ZERO;
        }
        return BigDecimal.valueOf(annualized).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal annualizeRatio(BigDecimal ratio, Instant startAt, Instant endAt) {
        if (ratio == null || startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return ZERO;
        }
        BigDecimal wealthRatio = ONE.add(ratio);
        if (wealthRatio.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        double days = Duration.between(startAt, endAt).toSeconds() / 86400d;
        if (days <= 0d) {
            return ZERO;
        }

        double annualized = Math.pow(wealthRatio.doubleValue(), 365d / days) - 1d;
        if (!Double.isFinite(annualized)) {
            return ZERO;
        }
        return BigDecimal.valueOf(annualized).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTwr(
            List<PerformanceSeriesPointDto> points,
            List<PerformanceCashflowEvent> cashflows,
            boolean ignoreInternalTransfers
    ) {
        if (points.size() < 2) {
            return ZERO;
        }

        BigDecimal wealthRatio = ONE;
        for (int index = 1; index < points.size(); index++) {
            PerformanceSeriesPointDto previous = points.get(index - 1);
            PerformanceSeriesPointDto current = points.get(index);
            BigDecimal startValue = nullSafe(previous.totalWalletAmount());
            BigDecimal endValue = nullSafe(current.totalWalletAmount());
            if (startValue.compareTo(ZERO) <= 0) {
                continue;
            }

            BigDecimal netCashflow = cashflows.stream()
                    .filter(event -> event.capturedAt().isAfter(previous.capturedAt()))
                    .filter(event -> !event.capturedAt().isAfter(current.capturedAt()))
                    .filter(event -> !ignoreInternalTransfers || !event.type().isInternalTransfer())
                    .map(PerformanceCashflowEvent::amount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal periodReturn = endValue.subtract(startValue).subtract(netCashflow)
                    .divide(startValue, 8, RoundingMode.HALF_UP);
            wealthRatio = wealthRatio.multiply(ONE.add(periodReturn), MATH_CONTEXT);
        }
        return wealthRatio.subtract(ONE).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateXirr(
            Instant startAt,
            Instant endAt,
            BigDecimal startValue,
            BigDecimal endValue,
            List<PerformanceCashflowEvent> cashflows
    ) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return null;
        }
        if (startValue.compareTo(ZERO) <= 0 || endValue.compareTo(ZERO) <= 0) {
            return null;
        }

        List<DatedAmount> values = new ArrayList<>();
        values.add(new DatedAmount(startAt, startValue.negate()));
        values.addAll(cashflows.stream()
                .map(event -> new DatedAmount(event.capturedAt(), event.amount().negate()))
                .toList());
        values.add(new DatedAmount(endAt, endValue));

        boolean hasPositive = values.stream().anyMatch(item -> item.amount().compareTo(ZERO) > 0);
        boolean hasNegative = values.stream().anyMatch(item -> item.amount().compareTo(ZERO) < 0);
        if (!hasPositive || !hasNegative) {
            return null;
        }

        Double solved = solveXirr(values);
        if (solved == null || !Double.isFinite(solved)) {
            return null;
        }
        return BigDecimal.valueOf(solved).setScale(8, RoundingMode.HALF_UP);
    }

    private Double solveXirr(List<DatedAmount> values) {
        double guess = 0.10d;
        for (int i = 0; i < XIRR_MAX_ITERATIONS; i++) {
            double npv = xnpv(guess, values);
            double derivative = xnpvDerivative(guess, values);
            if (Math.abs(derivative) < XIRR_TOLERANCE) {
                break;
            }
            double next = guess - (npv / derivative);
            if (Math.abs(next - guess) < XIRR_TOLERANCE && next > -0.999999d) {
                return next;
            }
            if (!Double.isFinite(next) || next <= -0.999999d) {
                break;
            }
            guess = next;
        }

        double low = -0.9999d;
        double high = 10d;
        double lowValue = xnpv(low, values);
        double highValue = xnpv(high, values);
        if (!Double.isFinite(lowValue) || !Double.isFinite(highValue) || Math.signum(lowValue) == Math.signum(highValue)) {
            return null;
        }

        for (int i = 0; i < 200; i++) {
            double mid = (low + high) / 2d;
            double midValue = xnpv(mid, values);
            if (!Double.isFinite(midValue)) {
                return null;
            }
            if (Math.abs(midValue) < XIRR_TOLERANCE) {
                return mid;
            }
            if (Math.signum(midValue) == Math.signum(lowValue)) {
                low = mid;
                lowValue = midValue;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2d;
    }

    private double xnpv(double rate, List<DatedAmount> values) {
        Instant baseDate = values.get(0).timestamp();
        double sum = 0d;
        for (DatedAmount item : values) {
            double years = Duration.between(baseDate, item.timestamp()).toSeconds() / 31536000d;
            sum += item.amount().doubleValue() / Math.pow(1d + rate, years);
        }
        return sum;
    }

    private double xnpvDerivative(double rate, List<DatedAmount> values) {
        Instant baseDate = values.get(0).timestamp();
        double sum = 0d;
        for (DatedAmount item : values) {
            double years = Duration.between(baseDate, item.timestamp()).toSeconds() / 31536000d;
            sum += (-years * item.amount().doubleValue()) / Math.pow(1d + rate, years + 1d);
        }
        return sum;
    }

    private BigDecimal percent(BigDecimal ratio) {
        return ratio == null ? null : ratio.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return nullSafe(numerator).divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return nullSafe(left).add(nullSafe(right));
    }

    private BigDecimal netCashflow(List<PerformanceCashflowEvent> cashflows) {
        return cashflows.stream()
                .map(PerformanceCashflowEvent::amount)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
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

    private record DatedAmount(Instant timestamp, BigDecimal amount) {
    }
}
