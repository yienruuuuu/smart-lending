package io.github.yienruuuuu.smartlending.service;

import io.github.yienruuuuu.smartlending.config.PerformanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定時抓取 performance snapshots。
 */
@Service
public class PerformanceSnapshotSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceSnapshotSchedulerService.class);

    private final PerformanceProperties properties;
    private final PerformanceSnapshotCollectorService collectorService;

    public PerformanceSnapshotSchedulerService(
            PerformanceProperties properties,
            PerformanceSnapshotCollectorService collectorService
    ) {
        this.properties = properties;
        this.collectorService = collectorService;
    }

    @Scheduled(initialDelay = 15000L, fixedDelayString = "${performance.snapshot-fixed-delay-millis:600000}")
    public void captureSnapshots() {
        if (!properties.isSnapshotEnabled()) {
            log.debug("略過 performance snapshot：snapshotEnabled=false");
            return;
        }

        int count = collectorService.captureAll().size();
        log.info("已完成 performance snapshot 收集。capturedCount={}", count);
    }
}
