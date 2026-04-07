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

    window.__performanceSummaryCombined = combinedSummary;
    window.__performanceSummaryMain = mainSummary;
    window.__performanceSummarySub = subSummary;
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
    setText(prefix + "-twr", formatPercent(summary.twrAnnualizedReturnPercent));
    const xirrNode = byId(prefix + "-xirr");
    if (xirrNode) {
        xirrNode.textContent = summary.xirrPercent === null ? "不適用" : formatPercent(summary.xirrPercent);
    }
    setText(prefix + "-wallet",
        "資產 " + formatNumber(summary.endValue)
        + " | 利用率 " + formatPercent(Number(summary.utilizationRatio || 0) * 100)
        + " | 淨現金流 " + formatSignedNumber(summary.netCashflow));
}

function updateChart(series) {
    const chartWidth = 800;
    const chartHeight = 300;
    const leftPad = 28;
    const rightPad = 18;
    const topPad = 20;
    const bottomPad = 48;
    const plotWidth = chartWidth - leftPad - rightPad;
    const plotHeight = chartHeight - topPad - bottomPad;

    setText("chart-title", accountLabel(series.account) + "績效");
    setText("range-count", "資料點 " + series.pointCount);

    if (!series.points.length) {
        setText("range-return", "報酬 --");
        setText("range-cashflow", "現金流 --");
        setHtml("chart", "");
        hideTooltip();
        return;
    }

    const values = series.points.map((point) => Number(point.totalWalletAmount || 0));
    const timestamps = series.points.map((point) => new Date(point.capturedAt).getTime());
    const min = Math.min(...values);
    const max = Math.max(...values);
    const spread = max - min || 1;
    const minTs = Math.min(...timestamps);
    const maxTs = Math.max(...timestamps);
    const timeSpread = maxTs - minTs || 1;

    const plotPoints = series.points.map((point) => {
        const timestamp = new Date(point.capturedAt).getTime();
        const x = leftPad + (((timestamp - minTs) / timeSpread) * plotWidth);
        const y = topPad + (plotHeight - (((Number(point.totalWalletAmount || 0) - min) / spread) * plotHeight));
        return { x, y, point };
    });

    const polyline = plotPoints.map((item) => `${item.x},${item.y}`).join(" ");
    const areaPoints = `${polyline} ${leftPad + plotWidth},${topPad + plotHeight} ${leftPad},${topPad + plotHeight}`;
    const tickCount = Math.min(5, Math.max(2, series.points.length));
    const ticks = buildTimeTicks(minTs, maxTs, tickCount);
    const tickMarkup = ticks.map((timestamp) => {
        const x = leftPad + (((timestamp - minTs) / timeSpread) * plotWidth);
        return `
            <line x1="${x}" y1="${topPad}" x2="${x}" y2="${topPad + plotHeight}" class="chart-grid"></line>
            <text x="${x}" y="${chartHeight - 14}" text-anchor="middle" class="chart-axis-text">${escapeHtml(formatAxisTimestamp(timestamp))}</text>
        `;
    }).join("");
    const pointMarkup = plotPoints.map((item, index) => `
        <circle
            cx="${item.x}"
            cy="${item.y}"
            r="10"
            class="chart-hit"
            data-index="${index}"
            fill="transparent"
            stroke="transparent"></circle>
        <circle cx="${item.x}" cy="${item.y}" r="2.5" class="chart-dot"></circle>
    `).join("");

    const summary = currentSummaryForAccount(series.account);
    setText("range-return",
        "TWR " + (summary ? formatPercent(summary.twrReturnPercent) : formatPercent(computeSeriesReturn(series.points))));
    setText("range-cashflow", "現金流 " + (summary ? formatSignedNumber(summary.netCashflow) : "--"));
    setHtml("chart", `
        <defs>
            <linearGradient id="line-fill" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(22, 32, 42, 0.28)"></stop>
                <stop offset="100%" stop-color="rgba(22, 32, 42, 0.02)"></stop>
            </linearGradient>
        </defs>
        <g>
            ${tickMarkup}
            <line x1="${leftPad}" y1="${topPad + plotHeight}" x2="${leftPad + plotWidth}" y2="${topPad + plotHeight}" class="chart-baseline"></line>
        </g>
        <polyline fill="url(#line-fill)" stroke="none" points="${areaPoints}"></polyline>
        <polyline fill="none" stroke="#16202a" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"></polyline>
        ${pointMarkup}
    `);

    bindTooltip(plotPoints, chartWidth, chartHeight);
}

function currentSummaryForAccount(account) {
    if (account === "combined") {
        return window.__performanceSummaryCombined;
    }
    if (account === "main") {
        return window.__performanceSummaryMain;
    }
    if (account === "sub") {
        return window.__performanceSummarySub;
    }
    return null;
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
    setText("chart-title", "績效資料暫時無法顯示");
    setText("range-return", error.message);
    setText("range-cashflow", "--");
    setHtml("chart", "");
    hideTooltip();
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

function formatSignedNumber(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return "--";
    }
    const number = Number(value);
    const rendered = Math.abs(number).toLocaleString(undefined, { maximumFractionDigits: 2 });
    if (number > 0) {
        return "+" + rendered;
    }
    if (number < 0) {
        return "-" + rendered;
    }
    return "0";
}

function formatTimestamp(value) {
    if (!value) {
        return "--";
    }
    return new Date(value).toLocaleString();
}

function formatAxisTimestamp(value) {
    return new Date(value).toLocaleString(undefined, {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
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

function byId(id) {
    return document.getElementById(id);
}

function setText(id, value) {
    const node = byId(id);
    if (node) {
        node.textContent = value;
    }
}

function setHtml(id, value) {
    const node = byId(id);
    if (node) {
        node.innerHTML = value;
    }
}

function buildTimeTicks(minTs, maxTs, count) {
    if (count <= 1 || minTs === maxTs) {
        return [minTs, maxTs];
    }
    const ticks = [];
    for (let index = 0; index < count; index += 1) {
        const ratio = index / (count - 1);
        ticks.push(Math.round(minTs + ((maxTs - minTs) * ratio)));
    }
    return ticks;
}

function bindTooltip(plotPoints, chartWidth, chartHeight) {
    const chart = byId("chart");
    const tooltip = byId("chart-tooltip");
    if (!chart || !tooltip) {
        return;
    }

    chart.querySelectorAll(".chart-hit").forEach((node) => {
        node.addEventListener("mouseenter", (event) => {
            const index = Number(event.target.dataset.index);
            const item = plotPoints[index];
            if (!item) {
                return;
            }

            tooltip.hidden = false;
            tooltip.innerHTML = `
                <div>${escapeHtml(formatTimestamp(item.point.capturedAt))}</div>
                <div>資產 ${escapeHtml(formatNumber(item.point.totalWalletAmount))}</div>
                <div>閒置 ${escapeHtml(formatNumber(item.point.idleAmount))}</div>
                <div>已借出 ${escapeHtml(formatNumber(item.point.lentAmount))}</div>
            `;
            tooltip.style.left = `${(item.x / chartWidth) * 100}%`;
            tooltip.style.top = `${(item.y / chartHeight) * 100}%`;
        });
    });

    chart.addEventListener("mouseleave", hideTooltip);
}

function hideTooltip() {
    const tooltip = byId("chart-tooltip");
    if (tooltip) {
        tooltip.hidden = true;
    }
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}
