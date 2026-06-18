package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.BitfinexProperties;
import io.github.yienruuuuu.smartlending.model.BitfinexLedgerEntry;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowEvent;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncAccountResultDto;
import io.github.yienruuuuu.smartlending.model.PerformanceCashflowSyncResponseDto;
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
    private static final int LEDGER_SYNC_LIMIT = 250;
    private static final int IGNORED_SAMPLE_LIMIT = 8;

    private final BitfinexProperties bitfinexProperties;
    private final BitfinexAccountRestClient bitfinexAccountRestClient;
    private final PerformanceSnapshotFileRepository snapshotRepository;
    private final PerformanceCashflowFileRepository cashflowRepository;
    private final PerformanceCashflowSyncStateRepository syncStateRepository;

    public PerformanceCashflowService(
            BitfinexProperties bitfinexProperties,
            BitfinexAccountRestClient bitfinexAccountRestClient,
            PerformanceSnapshotFileRepository snapshotRepository,
            PerformanceCashflowFileRepository cashflowRepository,
            PerformanceCashflowSyncStateRepository syncStateRepository
    ) {
        this.bitfinexProperties = bitfinexProperties;
        this.bitfinexAccountRestClient = bitfinexAccountRestClient;
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

    public PerformanceCashflowSyncResponseDto syncAll() {
        PerformanceCashflowSyncState previousState = syncStateRepository.load();
        Instant now = Instant.now();
        PerformanceCashflowSyncAccountResultDto mainResult = syncMain(previousState.mainLastSyncedAt(), now);
        syncStateRepository.save(new PerformanceCashflowSyncState(
                bitfinexProperties.hasMainAccountCredentials() ? now : previousState.mainLastSyncedAt()
        ));
        return new PerformanceCashflowSyncResponseDto(
                now,
                mainResult.cashflowSyncedCount(),
                "cashflow sync completed",
                List.of(mainResult)
        );
    }

    List<PerformanceCashflowEvent> loadCashflows(String account) {
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

    private PerformanceCashflowSyncAccountResultDto syncMain(Instant lastSyncedAt, Instant now) {
        if (!bitfinexProperties.hasMainAccountCredentials()) {
            log.debug("略過 main cashflow sync：未設定主帳戶 API 憑證");
            return new PerformanceCashflowSyncAccountResultDto("main", 0, 0, 0, List.of());
        }

        Instant since = resolveSince("main", lastSyncedAt, now);
        List<BitfinexLedgerEntry> ledgers = bitfinexAccountRestClient.getLedgerHistory(TARGET_CURRENCY, since, now, LEDGER_SYNC_LIMIT);
        List<PerformanceCashflowEvent> mergedEvents = new ArrayList<>();
        List<String> ignoredSamples = new ArrayList<>();
        int ignoredCount = 0;
        for (BitfinexLedgerEntry ledger : ledgers) {
            PerformanceCashflowEvent event = toLedgerCashflow("main", ledger);
            if (event == null) {
                ignoredCount++;
                if (ignoredSamples.size() < IGNORED_SAMPLE_LIMIT) {
                    ignoredSamples.add("%s %s %s".formatted(ledger.wallet(), ledger.amount(), nullSafe(ledger.description())));
                }
            } else {
                mergedEvents.add(event);
            }
        }
        cashflowRepository.merge("main", mergedEvents);
        log.info("已完成 performance cashflow 同步。account=main, since={}, until={}, ledgerCount={}, syncedCount={}, ignoredCount={}",
                since, now, ledgers.size(), mergedEvents.size(), ignoredCount);
        return new PerformanceCashflowSyncAccountResultDto(
                "main",
                ledgers.size(),
                mergedEvents.size(),
                ignoredCount,
                ignoredSamples
        );
    }

    private Instant resolveSince(String account, Instant lastSyncedAt, Instant now) {
        if (cashflowRepository.findByAccount(account).isEmpty()) {
            return earliestSnapshotAt(account)
                    .map(value -> value.minus(SYNC_OVERLAP))
                    .orElse(now.minus(DEFAULT_BOOTSTRAP_LOOKBACK));
        }
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

    private PerformanceCashflowEvent toLedgerCashflow(String account, BitfinexLedgerEntry entry) {
        if (entry.timestamp() == null || entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        String wallet = normalizeText(entry.wallet());
        String description = normalizeText(entry.description());
        if (!"funding".equals(wallet) || description == null || !description.contains("transfer")) {
            return null;
        }
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
                ledgerReferenceId(account, entry),
                null,
                "bitfinex-v2-ledger",
                "ledger",
                entry.description()
        );
    }

    private String ledgerReferenceId(String account, BitfinexLedgerEntry entry) {
        return "%s:ledger:%s".formatted(account, entry.id());
    }

    private String normalizeQueryAccount(String account) {
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

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
