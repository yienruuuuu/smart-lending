package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PerformanceCashflowSyncSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceCashflowSyncSchedulerService.class);

    private final PerformanceProperties properties;
    private final PerformanceCashflowService performanceCashflowService;

    public PerformanceCashflowSyncSchedulerService(
            PerformanceProperties properties,
            PerformanceCashflowService performanceCashflowService
    ) {
        this.properties = properties;
        this.performanceCashflowService = performanceCashflowService;
    }

    @Scheduled(initialDelay = 18000L, fixedDelayString = "${performance.cashflow-sync-fixed-delay-millis:600000}")
    public void syncCashflows() {
        if (!properties.isSnapshotEnabled()) {
            log.debug("略過 performance cashflow sync：snapshotEnabled=false");
            return;
        }

        int count = performanceCashflowService.syncAll();
        log.info("已完成 performance cashflow 同步排程。syncedCount={}", count);
    }
}
