package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.BitfinexBalanceHistoryEntry;
import io.github.yienruuuuu.smartlending.model.BitfinexMovementHistoryEntry;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncState;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowType;
import io.github.yienruuuuu.smartlending.model.PerformanceSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PerformanceCashflowService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceCashflowService.class);
    private static final String TARGET_SYMBOL = "fUSD";
    private static final String TARGET_CURRENCY = "USD";
    private static final Duration SYNC_OVERLAP = Duration.ofDays(1);
    private static final Duration DEFAULT_BOOTSTRAP_LOOKBACK = Duration.ofDays(180);

    private final BitfinexProperties bitfinexProperties;
    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final SubBitfinexAccountRestClient subBitfinexAccountRestClient;
    private final PerformanceSnapshotFileRepository snapshotRepository;
    private final PerformanceCashflowFileRepository cashflowRepository;
    private final PerformanceCashflowSyncStateRepository syncStateRepository;

    public PerformanceCashflowService(
            BitfinexProperties bitfinexProperties,
            BitfinexAccountRestClient bitfinexAccountRestClient,
            SubBitfinexAccountRestClient subBitfinexAccountRestClient,
            PerformanceSnapshotFileRepository snapshotRepository,
            PerformanceCashflowFileRepository cashflowRepository,
            PerformanceCashflowSyncStateRepository syncStateRepository
    ) {
        this.bitfinexProperties = bitfinexProperties;
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
        this.subBitfinexAccountRestClient = subBitfinexAccountRestClient;
        this.snapshotRepository = snapshotRepository;
        this.cashflowRepository = cashflowRepository;
        this.syncStateRepository = syncStateRepository;
    }

    public List<PerformanceCashflowEvent> getCashflows(String account, String range) {
        String normalizedAccount = normalizeQueryAccount(account);
        String normalizedRange = normalizeRange(range);
        return filterByRange(loadCashflows(normalizedAccount), normalizedRange).stream()
                .sorted(Comparator.comparing(PerformanceCashflowEvent::capturedAt)
                        .thenComparing(PerformanceCashflowEvent::referenceId))
                .toList();
    }

    public int syncAll() {
        PerformanceCashflowSyncState previousState = syncStateRepository.load();
        Instant now = Instant.now();
        int mainCount = syncAccount("main", previousState.mainLastSyncedAt(), now);
        int subCount = syncAccount("sub", previousState.subLastSyncedAt(), now);
        syncStateRepository.save(new PerformanceCashflowSyncState(
                bitfinexProperties.hasMainAccountCredentials() ? now : previousState.mainLastSyncedAt(),
                bitfinexProperties.hasSubAccountCredentials() ? now : previousState.subLastSyncedAt()
        ));
        return mainCount + subCount;
    }

    List<PerformanceCashflowEvent> loadCashflows(String account) {
        if ("combined".equals(account)) {
            return java.util.stream.Stream.concat(
                            cashflowRepository.findByAccount("main").stream(),
                            cashflowRepository.findByAccount("sub").stream()
                    )
                    .sorted(Comparator.comparing(PerformanceCashflowEvent::capturedAt)
                            .thenComparing(PerformanceCashflowEvent::referenceId))
                    .toList();
        }
        return cashflowRepository.findByAccount(account);
    }

    List<PerformanceCashflowEvent> filterByRange(List<PerformanceCashflowEvent> events, String range) {
        if (events.isEmpty() || "all".equals(range)) {
            return events;
        }
        Instant latestTimestamp = events.stream()
                .map(PerformanceCashflowEvent::capturedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latestTimestamp == null) {
            return events;
        }
        Duration duration = switch (range) {
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> throw new IllegalArgumentException("Unsupported range: " + range);
        };
        Instant cutoff = latestTimestamp.minus(duration);
        return events.stream()
                .filter(event -> !event.capturedAt().isBefore(cutoff))
                .toList();
    }

    private int syncAccount(String account, Instant lastSyncedAt, Instant now) {
        if ("main".equals(account) && !bitfinexProperties.hasMainAccountCredentials()) {
            log.debug("略過 main cashflow sync：未設定主帳戶 API 憑證");
            return 0;
        }
        if ("sub".equals(account) && !bitfinexProperties.hasSubAccountCredentials()) {
            log.debug("略過 sub cashflow sync：未設定 sub account API 憑證");
            return 0;
        }

        Instant since = resolveSince(account, lastSyncedAt, now);
        List<PerformanceCashflowEvent> mergedEvents = new ArrayList<>();
        mergedEvents.addAll(fetchMovements(account, since, now));
        mergedEvents.addAll(fetchInternalTransfers(account, since, now));
        cashflowRepository.merge(account, mergedEvents);
        log.info("已完成 performance cashflow 同步。account={}, since={}, until={}, syncedCount={}", account, since, now, mergedEvents.size());
        return mergedEvents.size();
    }

    private Instant resolveSince(String account, Instant lastSyncedAt, Instant now) {
        if (lastSyncedAt != null) {
            return lastSyncedAt.minus(SYNC_OVERLAP);
        }
        return earliestSnapshotAt(account)
                .map(value -> value.minus(SYNC_OVERLAP))
                .orElse(now.minus(DEFAULT_BOOTSTRAP_LOOKBACK));
    }

    private java.util.Optional<Instant> earliestSnapshotAt(String account) {
        return snapshotRepository.findByAccount(account).stream()
                .map(PerformanceSnapshot::capturedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    private List<PerformanceCashflowEvent> fetchMovements(String account, Instant since, Instant until) {
        List<BitfinexMovementHistoryEntry> movements = "main".equals(account)
                ? bitfinexAccountRestClient.getMovementHistory(TARGET_CURRENCY, since, until)
                : subBitfinexAccountRestClient.getMovementHistory(TARGET_CURRENCY, since, until);

        return movements.stream()
                .filter(this::isSettledMovement)
                .map(entry -> toMovementEvent(account, entry))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<PerformanceCashflowEvent> fetchInternalTransfers(String account, Instant since, Instant until) {
        List<BitfinexBalanceHistoryEntry> entries = "main".equals(account)
                ? bitfinexAccountRestClient.getBalanceHistory(TARGET_CURRENCY, since, until)
                : subBitfinexAccountRestClient.getBalanceHistory(TARGET_CURRENCY, since, until);

        return entries.stream()
                .filter(this::isFundingTransferEntry)
                .map(entry -> toInternalTransferEvent(account, entry))
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isSettledMovement(BitfinexMovementHistoryEntry entry) {
        String status = normalizeText(entry.status());
        return status != null && status.contains("completed");
    }

    private PerformanceCashflowEvent toMovementEvent(String account, BitfinexMovementHistoryEntry entry) {
        Instant capturedAt = entry.updatedAt();
        if (capturedAt == null || entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        String typeText = normalizeText(entry.type());
        PerformanceCashflowType type;
        if (typeText != null && typeText.contains("withdraw")) {
            type = PerformanceCashflowType.WITHDRAWAL;
        } else if (typeText != null && typeText.contains("deposit")) {
            type = PerformanceCashflowType.DEPOSIT;
        } else {
            return null;
        }

        return new PerformanceCashflowEvent(
                account,
                TARGET_SYMBOL,
                TARGET_CURRENCY,
                capturedAt,
                type.toSignedAmount(entry.amount()),
                type,
                movementReferenceId(account, entry),
                null,
                "bitfinex-v1-movements",
                entry.type(),
                entry.method()
        );
    }

    private boolean isFundingTransferEntry(BitfinexBalanceHistoryEntry entry) {
        if (entry.timestamp() == null || entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        String description = normalizeText(entry.description());
        if (description == null || !description.contains("transfer")) {
            return false;
        }
        return description.contains("funding") || description.contains("deposit wallet");
    }

    private PerformanceCashflowEvent toInternalTransferEvent(String account, BitfinexBalanceHistoryEntry entry) {
        PerformanceCashflowType type = entry.amount().compareTo(BigDecimal.ZERO) > 0
                ? PerformanceCashflowType.INTERNAL_TRANSFER_IN
                : PerformanceCashflowType.INTERNAL_TRANSFER_OUT;
        return new PerformanceCashflowEvent(
                account,
                TARGET_SYMBOL,
                TARGET_CURRENCY,
                entry.timestamp(),
                type.toSignedAmount(entry.amount().abs()),
                type,
                balanceHistoryReferenceId(account, entry),
                null,
                "bitfinex-v1-history",
                "transfer",
                entry.description()
        );
    }

    private String movementReferenceId(String account, BitfinexMovementHistoryEntry entry) {
        String txId = entry.transactionId() == null ? "-" : entry.transactionId();
        return "%s:movement:%s:%s:%s".formatted(
                account,
                nullSafe(entry.id()),
                txId,
                entry.updatedAt() == null ? "-" : entry.updatedAt().toEpochMilli()
        );
    }

    private String balanceHistoryReferenceId(String account, BitfinexBalanceHistoryEntry entry) {
        return "%s:history:%s:%s:%s".formatted(
                account,
                entry.timestamp() == null ? "-" : entry.timestamp().toEpochMilli(),
                entry.amount() == null ? "-" : entry.amount().stripTrailingZeros().toPlainString(),
                Integer.toHexString(nullSafe(entry.description()).hashCode())
        );
    }

    private String normalizeQueryAccount(String account) {
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

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
