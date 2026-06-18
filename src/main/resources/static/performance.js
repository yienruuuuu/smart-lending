const state = {
    account: "main",
    range: "30d",
    cashflows: []
};

document.addEventListener("DOMContentLoaded", () => {
    bindControls();
    refresh().catch(renderError);
});

function bindControls() {
    const rangeSelect = byId("range-select");
    if (rangeSelect) {
        rangeSelect.addEventListener("change", (event) => {
            state.range = event.target.value;
            refresh().catch(renderError);
        });
    }

    const syncButton = byId("sync-cashflows");
    if (syncButton) {
        syncButton.addEventListener("click", () => {
            syncCashflows().catch(renderCashflowError);
        });
    }
}

async function refresh() {
    const [summary, series, cashflows] = await Promise.all([
        fetchJson("/api/v1/performance/summary?account=main&range=" + state.range),
        fetchJson("/api/v1/performance/series?account=main&range=" + state.range),
        fetchJson("/api/v1/performance/cashflows?account=main&range=all")
    ]);

    state.cashflows = cashflows;
    window.__performanceSummaryMain = summary;
    updateSummaryCard("main", summary);
    updateChart(series);
    renderCashflowList(cashflows);
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error("Request failed: " + url);
    }
    return response.json();
}

async function fetchJsonWithMethod(url, method) {
    const response = await fetch(url, { method });
    if (!response.ok) {
        throw new Error(await responseErrorMessage(response));
    }
    return response.json();
}

async function responseErrorMessage(response) {
    try {
        const payload = await response.json();
        return payload.message || "Request failed";
    } catch (error) {
        return "Request failed";
    }
}

function updateSummaryCard(prefix, summary) {
    setText(prefix + "-twr", isCashflowTrusted(summary) ? formatPercent(summary.twrAnnualizedReturnPercent) : "待同步");
    setText(prefix + "-xirr", xirrText(summary));
    setText(prefix + "-wallet",
        "資產 " + formatNumber(summary.endValue)
        + " | 利用率 " + formatPercent(Number(summary.utilizationRatio || 0) * 100)
        + " | 淨現金流 " + formatSignedNumber(summary.netCashflow));
    updateCashflowWarning(prefix, summary);
}

function updateCashflowWarning(prefix, summary) {
    const node = byId(prefix + "-cashflow-warning");
    if (!node) {
        return;
    }
    const warning = summary.cashflowWarning || "";
    node.textContent = warning;
    node.hidden = !warning;
}

async function syncCashflows() {
    setCashflowStatus("同步中...");
    const response = await fetchJsonWithMethod("/api/v1/performance/cashflows/sync", "POST");
    const main = response.accounts && response.accounts.length ? response.accounts[0] : null;
    const suffix = main
        ? `ledger ${main.ledgerFetchedCount} 筆，本金事件 ${main.cashflowSyncedCount} 筆，忽略 ${main.ignoredCount} 筆`
        : `${response.syncedCount} 筆`;
    setCashflowStatus("已完成 Bitfinex v2 ledger 同步：" + suffix);
    await refresh();
}

function renderCashflowList(items) {
    const list = byId("cashflow-list");
    if (!list) {
        return;
    }
    const sorted = [...items]
        .sort((left, right) => new Date(right.capturedAt).getTime() - new Date(left.capturedAt).getTime())
        .slice(0, 10);
    setText("cashflow-list-count", sorted.length + " 筆");
    if (!sorted.length) {
        list.innerHTML = `<p class="cashflow-meta">尚無本金事件，請同步 Bitfinex v2 ledger</p>`;
        return;
    }
    list.innerHTML = sorted.map((item) => `
        <div class="cashflow-item">
            <p class="cashflow-title">
                <span>${escapeHtml(cashflowTypeLabel(item.type))}</span>
                <span class="${Number(item.amount || 0) >= 0 ? "amount-positive" : "amount-negative"}">${escapeHtml(formatSignedNumber(item.amount))}</span>
            </p>
            <p class="cashflow-meta">${escapeHtml(formatTimestamp(item.capturedAt))}</p>
            <p class="cashflow-meta">${escapeHtml(item.source || "--")}${item.note ? " / " + escapeHtml(item.note) : ""}</p>
        </div>
    `).join("");
}

function renderCashflowError(error) {
    setCashflowStatus(error.message, true);
}

function setCashflowStatus(message, isError = false) {
    const node = byId("cashflow-status");
    if (!node) {
        return;
    }
    node.textContent = message || "";
    node.classList.toggle("error", isError);
}

function xirrText(summary) {
    if (!isCashflowTrusted(summary)) {
        return "待同步";
    }
    return summary.xirrPercent === null ? "不適用" : formatPercent(summary.xirrPercent);
}

function isCashflowTrusted(summary) {
    return summary && (!summary.cashflowStatus || summary.cashflowStatus === "OK");
}

function updateChart(series) {
    const chartWidth = 800;
    const chartHeight = 300;
    const leftPad = 28;
    const rightPad = 18;
    const topPad = 20;
    const lineBottom = 208;
    const barBaseline = 256;
    const maxBarHeight = 34;
    const bottomPad = 30;
    const plotWidth = chartWidth - leftPad - rightPad;
    const plotHeight = lineBottom - topPad;

    setText("chart-title", "主帳戶績效");
    setText("range-count", "資料點 " + series.pointCount);

    if (!series.points.length) {
        setText("range-return", "報酬 --");
        setText("range-cashflow", "現金流 --");
        setHtml("chart", "");
        hideTooltip();
        return;
    }

    const values = series.points.map((point) => Number(point.twrIndex || 100));
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
        const y = topPad + (plotHeight - (((Number(point.twrIndex || 100) - min) / spread) * plotHeight));
        return { x, y, point };
    });

    const polyline = plotPoints.map((item) => `${item.x},${item.y}`).join(" ");
    const areaPoints = `${polyline} ${leftPad + plotWidth},${lineBottom} ${leftPad},${lineBottom}`;
    const maxCashflow = Math.max(
        ...series.points.map((point) => Math.abs(Number(point.periodCashflow || 0))),
        0
    );
    const tickCount = Math.min(5, Math.max(2, series.points.length));
    const ticks = buildTimeTicks(minTs, maxTs, tickCount);
    const tickMarkup = ticks.map((timestamp) => {
        const x = leftPad + (((timestamp - minTs) / timeSpread) * plotWidth);
        return `
            <line x1="${x}" y1="${topPad}" x2="${x}" y2="${barBaseline + maxBarHeight}" class="chart-grid"></line>
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
    const cashflowBarMarkup = maxCashflow <= 0 ? "" : plotPoints.map((item) => {
        const cashflow = Number(item.point.periodCashflow || 0);
        if (cashflow === 0) {
            return "";
        }
        const height = Math.max(3, (Math.abs(cashflow) / maxCashflow) * maxBarHeight);
        const y = cashflow > 0 ? barBaseline - height : barBaseline;
        return `
            <rect
                x="${item.x - 4}"
                y="${y}"
                width="8"
                height="${height}"
                rx="3"
                class="cashflow-bar ${cashflow > 0 ? "positive" : "negative"}"></rect>
        `;
    }).join("");

    const summary = window.__performanceSummaryMain;
    setText("range-return", "TWR " + (isCashflowTrusted(summary)
        ? formatPercent(summary.twrReturnPercent)
        : "待同步"));
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
            <line x1="${leftPad}" y1="${lineBottom}" x2="${leftPad + plotWidth}" y2="${lineBottom}" class="chart-baseline"></line>
            <line x1="${leftPad}" y1="${barBaseline}" x2="${leftPad + plotWidth}" y2="${barBaseline}" class="cashflow-baseline"></line>
        </g>
        <polyline fill="url(#line-fill)" stroke="none" points="${areaPoints}"></polyline>
        <polyline fill="none" stroke="#16202a" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"></polyline>
        ${cashflowBarMarkup}
        ${pointMarkup}
    `);

    bindTooltip(plotPoints, chartWidth, chartHeight);
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

function cashflowTypeLabel(value) {
    if (value === "INTERNAL_TRANSFER_IN") {
        return "本金轉入";
    }
    if (value === "INTERNAL_TRANSFER_OUT") {
        return "本金轉出";
    }
    if (value === "DEPOSIT") {
        return "外部轉入";
    }
    if (value === "WITHDRAWAL") {
        return "外部轉出";
    }
    return value || "--";
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
                <div>TWR 淨值 ${escapeHtml(formatNumber(item.point.twrIndex))}</div>
                <div>資產 ${escapeHtml(formatNumber(item.point.totalWalletAmount))}</div>
                <div>區間本金 ${escapeHtml(formatSignedNumber(item.point.periodCashflow))}</div>
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
