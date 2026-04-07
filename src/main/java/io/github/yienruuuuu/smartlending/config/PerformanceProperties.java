package io.github.yienruuuuu.smartlending.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Performance tracking 相關設定。
 */
@Validated
@ConfigurationProperties(prefix = "performance")
public class PerformanceProperties {

    private boolean snapshotEnabled = true;
    private boolean dashboardEnabled = true;

    @NotBlank
    private String storagePath = "data/performance";

    @Min(1000)
    private long snapshotFixedDelayMillis = 600000L;

    @Min(1000)
    private long cashflowSyncFixedDelayMillis = 600000L;

    public boolean isSnapshotEnabled() {
        return snapshotEnabled;
    }

    public void setSnapshotEnabled(boolean snapshotEnabled) {
        this.snapshotEnabled = snapshotEnabled;
    }

    public boolean isDashboardEnabled() {
        return dashboardEnabled;
    }

    public void setDashboardEnabled(boolean dashboardEnabled) {
        this.dashboardEnabled = dashboardEnabled;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public long getSnapshotFixedDelayMillis() {
        return snapshotFixedDelayMillis;
    }

    public void setSnapshotFixedDelayMillis(long snapshotFixedDelayMillis) {
        this.snapshotFixedDelayMillis = snapshotFixedDelayMillis;
    }

    public long getCashflowSyncFixedDelayMillis() {
        return cashflowSyncFixedDelayMillis;
    }

    public void setCashflowSyncFixedDelayMillis(long cashflowSyncFixedDelayMillis) {
        this.cashflowSyncFixedDelayMillis = cashflowSyncFixedDelayMillis;
    }
}
