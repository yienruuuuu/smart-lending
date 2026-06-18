package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowStatus;
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
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 依 snapshots 與 cashflows 計算績效摘要與時間序列。
 */
@Service
public class PerformanceMetricsService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWR_INDEX_BASE = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final Duration CASHFLOW_STALE_AFTER = Duration.ofMinutes(30);
    private static final int XIRR_MAX_ITERATIONS = 50;
    private static final double XIRR_TOLERANCE = 1.0e-7d;

    private final PerformanceSnapshotFileRepository repository;
    private final PerformanceCashflowFileRepository cashflowRepository;
    private final PerformanceCashflowSyncStateRepository syncStateRepository;

    public PerformanceMetricsService(
            PerformanceSnapshotFileRepository repository,
            PerformanceCashflowFileRepository cashflowRepository,
            PerformanceCashflowSyncStateRepository syncStateRepository
    ) {
        this.repository = repository;
        this.cashflowRepository = cashflowRepository;
        this.syncStateRepository = syncStateRepository;
    }

    public PerformanceSummaryDto getSummary(String account, String range) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        List<PerformanceCashflowEvent> cashflows = buildCashflows(normalizedAccount, normalizedRange);
        List<PerformanceSeriesPointDto> points = buildSeriesPoints(normalizedAccount, normalizedRange, cashflows);
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
        BigDecimal twrReturnRatio = calculateTwr(points, cashflows, false);
        BigDecimal twrAnnualizedReturnRatio = annualizeRatio(twrReturnRatio, first.capturedAt(), last.capturedAt());
        BigDecimal xirrRatio = calculateXirr(first.capturedAt(), last.capturedAt(), startValue, endValue, cashflows);
        CashflowHealth cashflowHealth = cashflowHealth(
                normalizedAccount,
                first.capturedAt(),
                last.capturedAt(),
                absoluteReturn
        );

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
                ratio(nullSafe(last.lentAmount()), endValue),
                cashflowHealth.status(),
                cashflowHealth.warning()
        );
    }

    public PerformanceSeriesResponseDto getSeries(String account, String range) {
        String normalizedAccount = normalizeAccount(account);
        String normalizedRange = normalizeRange(range);
        List<PerformanceCashflowEvent> cashflows = buildCashflows(normalizedAccount, normalizedRange);
        List<PerformanceSeriesPointDto> points = buildSeriesPoints(normalizedAccount, normalizedRange, cashflows);
        return new PerformanceSeriesResponseDto(normalizedAccount, normalizedRange, points.size(), points);
    }

    public PerformanceLatestSnapshotsDto getLatestSnapshots() {
        PerformanceSnapshot main = latest(repository.findByAccount("main"));
        return new PerformanceLatestSnapshotsDto(main);
    }

    private List<PerformanceSeriesPointDto> buildSeriesPoints(
            String account,
            String range,
            List<PerformanceCashflowEvent> cashflows
    ) {
        return singleAccountSeries(account, range, cashflows);
    }

    private List<PerformanceCashflowEvent> buildCashflows(String account, String range) {
        List<PerformanceCashflowEvent> cashflows = cashflowRepository.findByAccount(account);
        return filterCashflowsByRange(cashflows, range);
    }

    private List<PerformanceSeriesPointDto> singleAccountSeries(
            String account,
            String range,
            List<PerformanceCashflowEvent> cashflows
    ) {
        List<PerformanceSnapshot> snapshots = repository.findByAccount(account);
        Instant latestTimestamp = latestTimestamp(snapshots);
        return toSeriesPoints(filterByRange(snapshots, range, latestTimestamp), cashflows);
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

    private List<PerformanceSeriesPointDto> toSeriesPoints(
            List<PerformanceSnapshot> snapshots,
            List<PerformanceCashflowEvent> cashflows
    ) {
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<PerformanceSeriesPointDto> points = new ArrayList<>();
        BigDecimal twrIndex = TWR_INDEX_BASE;
        for (int index = 0; index < snapshots.size(); index++) {
            PerformanceSnapshot current = snapshots.get(index);
            BigDecimal periodCashflow = ZERO;
            if (index > 0) {
                PerformanceSnapshot previous = snapshots.get(index - 1);
                periodCashflow = netCashflowBetween(cashflows, previous.capturedAt(), current.capturedAt());
                BigDecimal startValue = nullSafe(previous.totalWalletAmount());
                BigDecimal endValue = nullSafe(current.totalWalletAmount());
                if (startValue.compareTo(ZERO) > 0) {
                    BigDecimal periodReturn = endValue.subtract(startValue).subtract(periodCashflow)
                            .divide(startValue, 8, RoundingMode.HALF_UP);
                    twrIndex = twrIndex.multiply(ONE.add(periodReturn), MATH_CONTEXT)
                            .setScale(8, RoundingMode.HALF_UP);
                }
            }
            points.add(toPoint(current, twrIndex, periodCashflow));
        }
        return points;
    }

    private PerformanceSeriesPointDto toPoint(
            PerformanceSnapshot snapshot,
            BigDecimal twrIndex,
            BigDecimal periodCashflow
    ) {
        return new PerformanceSeriesPointDto(
                snapshot.capturedAt(),
                nullSafe(snapshot.totalWalletAmount()),
                nullSafe(snapshot.idleAmount()),
                nullSafe(snapshot.offerAmount()),
                nullSafe(snapshot.creditAmount()),
                nullSafe(snapshot.loanAmount()),
                nullSafe(snapshot.lentAmount()),
                nullSafe(snapshot.unsettledInterest()),
                twrIndex,
                periodCashflow
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
                ZERO,
                cashflowCount == 0 ? PerformanceCashflowStatus.EMPTY : PerformanceCashflowStatus.OK,
                cashflowCount == 0 ? "尚未同步 cashflow ledger，績效可能把入金或轉出誤算成報酬。" : null
        );
    }

    private CashflowHealth cashflowHealth(
            String account,
            Instant startAt,
            Instant endAt,
            BigDecimal absoluteReturn
    ) {
        if (hasNoStoredCashflows(account) && absoluteReturn.compareTo(ZERO) != 0) {
            return new CashflowHealth(
                    PerformanceCashflowStatus.EMPTY,
                    "尚未同步 cashflow ledger，績效可能把入金或轉出誤算成報酬。"
            );
        }
        if (isCashflowSyncStale(account, endAt)) {
            return new CashflowHealth(
                    PerformanceCashflowStatus.STALE,
                    "cashflow 同步時間早於最新快照，近期入金或轉出可能尚未納入。"
            );
        }
        if (startAt == null || endAt == null) {
            return new CashflowHealth(PerformanceCashflowStatus.OK, null);
        }
        return new CashflowHealth(PerformanceCashflowStatus.OK, null);
    }

    private boolean hasNoStoredCashflows(String account) {
        return cashflowRepository.findByAccount(account).isEmpty();
    }

    private boolean isCashflowSyncStale(String account, Instant endAt) {
        if (endAt == null) {
            return false;
        }
        var state = syncStateRepository.load();
        return isAccountSyncStale(state.mainLastSyncedAt(), endAt);
    }

    private boolean isAccountSyncStale(Instant lastSyncedAt, Instant endAt) {
        return lastSyncedAt != null && endAt.isAfter(lastSyncedAt.plus(CASHFLOW_STALE_AFTER));
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

    private BigDecimal netCashflow(List<PerformanceCashflowEvent> cashflows) {
        return cashflows.stream()
                .map(PerformanceCashflowEvent::amount)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal netCashflowBetween(
            List<PerformanceCashflowEvent> cashflows,
            Instant exclusiveStart,
            Instant inclusiveEnd
    ) {
        if (exclusiveStart == null || inclusiveEnd == null) {
            return ZERO;
        }
        return cashflows.stream()
                .filter(event -> event.capturedAt().isAfter(exclusiveStart))
                .filter(event -> !event.capturedAt().isAfter(inclusiveEnd))
                .map(PerformanceCashflowEvent::amount)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
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

    private record DatedAmount(Instant timestamp, BigDecimal amount) {
    }

    private record CashflowHealth(PerformanceCashflowStatus status, String warning) {
    }
}
