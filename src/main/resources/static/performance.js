const state = {
    account: "combined",
    range: "30d"
};

document.addEventListener("DOMContentLoaded", () => {
    bindControls();
    refresh().catch(renderError);
});

function bindControls() {
    document.querySelectorAll("#account-tabs button").forEach((button) => {
        button.addEventListener("click", () => {
            document.querySelectorAll("#account-tabs button").forEach((item) => item.classList.remove("active"));
            button.classList.add("active");
            state.account = button.dataset.account;
            refresh().catch(renderError);
        });
    });

    document.getElementById("range-select").addEventListener("change", (event) => {
        state.range = event.target.value;
        refresh().catch(renderError);
    });
}

async function refresh() {
    const [combinedSummary, mainSummary, subSummary, series] = await Promise.all([
        fetchJson("/api/v1/performance/summary?account=combined&range=" + state.range),
        fetchJson("/api/v1/performance/summary?account=main&range=" + state.range),
        fetchJson("/api/v1/performance/summary?account=sub&range=" + state.range),
        fetchJson("/api/v1/performance/series?account=" + state.account + "&range=" + state.range)
    ]);

    updateSummaryCard("combined", combinedSummary);
    updateSummaryCard("main", mainSummary);
    updateSummaryCard("sub", subSummary);
    updateChart(series);
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error("Request failed: " + url);
    }
    return response.json();
}

function updateSummaryCard(prefix, summary) {
    document.getElementById(prefix + "-annualized").textContent = formatPercent(summary.annualizedReturnPercent);
    document.getElementById(prefix + "-wallet").textContent =
        "資產 " + formatNumber(summary.endValue) + " | 利用率 " + formatPercent(Number(summary.utilizationRatio || 0) * 100);
}

function updateChart(series) {
    document.getElementById("chart-title").textContent = accountLabel(series.account) + "績效";
    document.getElementById("range-count").textContent = "資料點 " + series.pointCount;

    if (!series.points.length) {
        document.getElementById("range-return").textContent = "報酬 --";
        document.getElementById("axis-start").textContent = "--";
        document.getElementById("axis-end").textContent = "--";
        document.getElementById("chart").innerHTML = "";
        return;
    }

    const values = series.points.map((point) => Number(point.totalWalletAmount || 0));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const spread = max - min || 1;

    const polyline = series.points.map((point, index) => {
        const x = (index / Math.max(series.points.length - 1, 1)) * 760 + 20;
        const y = 220 - (((Number(point.totalWalletAmount || 0) - min) / spread) * 180) + 20;
        return `${x},${y}`;
    }).join(" ");

    document.getElementById("range-return").textContent =
        "報酬 " + formatPercent(computeSeriesReturn(series.points));
    document.getElementById("axis-start").textContent = formatTimestamp(series.points[0].capturedAt);
    document.getElementById("axis-end").textContent = formatTimestamp(series.points[series.points.length - 1].capturedAt);
    document.getElementById("chart").innerHTML = `
        <defs>
            <linearGradient id="line-fill" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(22, 32, 42, 0.28)"></stop>
                <stop offset="100%" stop-color="rgba(22, 32, 42, 0.02)"></stop>
            </linearGradient>
        </defs>
        <polyline fill="url(#line-fill)" stroke="none" points="${polyline} 780,240 20,240"></polyline>
        <polyline fill="none" stroke="#16202a" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"></polyline>
    `;
}

function computeSeriesReturn(points) {
    if (points.length < 2) {
        return 0;
    }
    const start = Number(points[0].totalWalletAmount || 0);
    const end = Number(points[points.length - 1].totalWalletAmount || 0);
    if (start <= 0) {
        return 0;
    }
    return ((end - start) / start) * 100;
}

function renderError(error) {
    document.getElementById("chart-title").textContent = "績效資料暫時無法顯示";
    document.getElementById("range-return").textContent = error.message;
}

function formatPercent(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return "--";
    }
    return Number(value).toFixed(2) + "%";
}

function formatNumber(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return "--";
    }
    return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function formatTimestamp(value) {
    if (!value) {
        return "--";
    }
    return new Date(value).toLocaleString();
}

function titleCase(value) {
    return value.charAt(0).toUpperCase() + value.slice(1);
}

function accountLabel(value) {
    if (value === "combined") {
        return "合併";
    }
    if (value === "main") {
        return "主帳戶";
    }
    if (value === "sub") {
        return "子帳戶";
    }
    return titleCase(value);
}
