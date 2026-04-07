const logsState = {
    account: "combined",
    range: "30d",
    type: "all",
    q: "",
    page: 0,
    size: 50
};

document.addEventListener("DOMContentLoaded", () => {
    bindLogsControls();
    refreshLogs().catch(renderLogsError);
});

function bindLogsControls() {
    byId("account-select").addEventListener("change", (event) => {
        logsState.account = event.target.value;
        logsState.page = 0;
        refreshLogs().catch(renderLogsError);
    });
    byId("range-select").addEventListener("change", (event) => {
        logsState.range = event.target.value;
        logsState.page = 0;
        refreshLogs().catch(renderLogsError);
    });
    byId("type-select").addEventListener("change", (event) => {
        logsState.type = event.target.value;
        logsState.page = 0;
        refreshLogs().catch(renderLogsError);
    });
    byId("query-input").addEventListener("change", (event) => {
        logsState.q = event.target.value.trim();
        logsState.page = 0;
        refreshLogs().catch(renderLogsError);
    });
    byId("prev-page").addEventListener("click", () => {
        if (logsState.page > 0) {
            logsState.page -= 1;
            refreshLogs().catch(renderLogsError);
        }
    });
    byId("next-page").addEventListener("click", () => {
        logsState.page += 1;
        refreshLogs().catch(renderLogsError);
    });
}

async function refreshLogs() {
    const params = new URLSearchParams({
        account: logsState.account,
        range: logsState.range,
        type: logsState.type,
        page: String(logsState.page),
        size: String(logsState.size)
    });
    if (logsState.q) {
        params.set("q", logsState.q);
    }
    const response = await fetch("/api/v1/performance/logs?" + params.toString());
    if (!response.ok) {
        throw new Error("Request failed: /api/v1/performance/logs");
    }
    const payload = await response.json();
    renderSummary(payload.summary);
    renderPageLabel(payload.page, payload.size, payload.totalCount);
    renderTable(payload.items);
    renderLogsChart(payload.items, payload.summary);
    byId("prev-page").disabled = payload.page <= 0;
    byId("next-page").disabled = ((payload.page + 1) * payload.size) >= payload.totalCount;
}

function renderSummary(summary) {
    setText("summary-events", String(summary.eventCount));
    setText("summary-snapshots", String(summary.snapshotCount));
    setText("summary-cashflows", String(summary.cashflowCount));
    setText("summary-net-cashflow", formatSignedNumber(summary.netCashflow));
    setText("summary-start-value", "起始資產 " + formatNumber(summary.startValue));
    setText("summary-end-value", "結束資產 " + formatNumber(summary.endValue));
}

function renderPageLabel(page, size, totalCount) {
    const from = totalCount === 0 ? 0 : (page * size) + 1;
    const to = Math.min((page + 1) * size, totalCount);
    setText("page-label", `${from}-${to} / ${totalCount}`);
}

function renderTable(items) {
    const body = byId("logs-body");
    if (!body) {
        return;
    }
    if (!items.length) {
        body.innerHTML = "<tr><td colspan=\"9\">沒有符合條件的資料</td></tr>";
        return;
    }
    body.innerHTML = items.map((item) => `
        <tr>
            <td>${escapeHtml(formatTimestamp(item.capturedAt))}</td>
            <td>${escapeHtml(accountLabel(item.account))}</td>
            <td>${escapeHtml(item.type)}</td>
            <td>${escapeHtml(item.title)}${item.note ? `<br><small>${escapeHtml(item.note)}</small>` : ""}</td>
            <td class="${amountClass(item.amount)}">${escapeHtml(item.amount === null ? "--" : formatSignedNumber(item.amount))}</td>
            <td>${escapeHtml(item.totalWalletAmount === null ? "--" : formatNumber(item.totalWalletAmount))}</td>
            <td>${escapeHtml(item.utilizationRatio === null ? "--" : formatPercent(Number(item.utilizationRatio) * 100))}</td>
            <td>${escapeHtml(item.source || "--")}</td>
            <td>${escapeHtml(item.referenceId || "--")}</td>
        </tr>
    `).join("");
}

function renderLogsChart(items, summary) {
    const chart = byId("logs-chart");
    if (!chart) {
        return;
    }
    if (!items.length) {
        chart.innerHTML = "";
        hideLogsTooltip();
        return;
    }

    const width = 960;
    const height = 320;
    const leftPad = 30;
    const rightPad = 20;
    const topPad = 20;
    const bottomPad = 50;
    const plotWidth = width - leftPad - rightPad;
    const plotHeight = height - topPad - bottomPad;
    const snapshotItems = items.filter((item) => item.kind === "snapshot" && item.totalWalletAmount !== null)
        .sort((a, b) => new Date(a.capturedAt) - new Date(b.capturedAt));
    const cashflowItems = items.filter((item) => item.kind === "cashflow" && item.amount !== null)
        .sort((a, b) => new Date(a.capturedAt) - new Date(b.capturedAt));
    const allTs = items.map((item) => new Date(item.capturedAt).getTime());
    const minTs = Math.min(...allTs);
    const maxTs = Math.max(...allTs);
    const timeSpread = maxTs - minTs || 1;

    const snapshotValues = snapshotItems.map((item) => Number(item.totalWalletAmount));
    const minValue = snapshotValues.length ? Math.min(...snapshotValues) : Number(summary.startValue || 0);
    const maxValue = snapshotValues.length ? Math.max(...snapshotValues) : Number(summary.endValue || 0);
    const valueSpread = maxValue - minValue || 1;
    const cashflowMax = cashflowItems.length ? Math.max(...cashflowItems.map((item) => Math.abs(Number(item.amount)))) : 1;
    const baselineY = topPad + plotHeight;

    const ticks = buildTicks(minTs, maxTs, Math.min(5, Math.max(2, items.length)));
    const tickMarkup = ticks.map((timestamp) => {
        const x = leftPad + (((timestamp - minTs) / timeSpread) * plotWidth);
        return `
            <line x1="${x}" y1="${topPad}" x2="${x}" y2="${baselineY}" class="chart-grid"></line>
            <text x="${x}" y="${height - 14}" text-anchor="middle" class="chart-axis-text">${escapeHtml(formatAxisTimestamp(timestamp))}</text>
        `;
    }).join("");

    const snapshotPoints = snapshotItems.map((item) => {
        const x = leftPad + (((new Date(item.capturedAt).getTime() - minTs) / timeSpread) * plotWidth);
        const y = topPad + (plotHeight - (((Number(item.totalWalletAmount) - minValue) / valueSpread) * plotHeight));
        return { x, y, item };
    });
    const polyline = snapshotPoints.map((point) => `${point.x},${point.y}`).join(" ");
    const area = snapshotPoints.length
        ? `${polyline} ${leftPad + plotWidth},${baselineY} ${leftPad},${baselineY}`
        : "";
    const snapshotMarkup = snapshotPoints.map((point, index) => `
        <circle cx="${point.x}" cy="${point.y}" r="8" class="chart-hit snapshot-hit" data-kind="snapshot" data-index="${index}" fill="transparent"></circle>
        <circle cx="${point.x}" cy="${point.y}" r="2.5" class="snapshot-dot"></circle>
    `).join("");

    const cashflowMarkup = cashflowItems.map((item, index) => {
        const x = leftPad + (((new Date(item.capturedAt).getTime() - minTs) / timeSpread) * plotWidth);
        const barHeight = (Math.abs(Number(item.amount)) / cashflowMax) * (plotHeight * 0.35);
        const y = Number(item.amount) >= 0 ? baselineY - barHeight : baselineY;
        return `
            <rect x="${x - 4}" y="${y}" width="8" height="${barHeight}" class="cashflow-bar ${Number(item.amount) >= 0 ? "positive" : "negative"} chart-hit"
                data-kind="cashflow" data-index="${index}"></rect>
        `;
    }).join("");

    chart.innerHTML = `
        <defs>
            <linearGradient id="logs-line-fill" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="rgba(22, 32, 42, 0.26)"></stop>
                <stop offset="100%" stop-color="rgba(22, 32, 42, 0.03)"></stop>
            </linearGradient>
        </defs>
        ${tickMarkup}
        <line x1="${leftPad}" y1="${baselineY}" x2="${leftPad + plotWidth}" y2="${baselineY}" class="chart-baseline"></line>
        ${area ? `<polyline fill="url(#logs-line-fill)" stroke="none" points="${area}"></polyline>` : ""}
        ${polyline ? `<polyline fill="none" stroke="#16202a" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"></polyline>` : ""}
        ${cashflowMarkup}
        ${snapshotMarkup}
    `;

    bindLogsTooltip(snapshotPoints, cashflowItems, width, height);
}

function bindLogsTooltip(snapshotPoints, cashflowItems, chartWidth, chartHeight) {
    const chart = byId("logs-chart");
    const tooltip = byId("logs-chart-tooltip");
    if (!chart || !tooltip) {
        return;
    }

    chart.querySelectorAll(".chart-hit").forEach((node) => {
        node.addEventListener("mouseenter", (event) => {
            const kind = event.target.dataset.kind;
            const index = Number(event.target.dataset.index);
            if (kind === "snapshot") {
                const point = snapshotPoints[index];
                if (!point) {
                    return;
                }
                tooltip.hidden = false;
                tooltip.innerHTML = `
                    <div>${escapeHtml(formatTimestamp(point.item.capturedAt))}</div>
                    <div>資產 ${escapeHtml(formatNumber(point.item.totalWalletAmount))}</div>
                    <div>閒置 ${escapeHtml(formatNumber(point.item.idleAmount))}</div>
                    <div>已借出 ${escapeHtml(formatNumber(point.item.lentAmount))}</div>
                `;
                tooltip.style.left = `${(point.x / chartWidth) * 100}%`;
                tooltip.style.top = `${(point.y / chartHeight) * 100}%`;
                return;
            }

            const item = cashflowItems[index];
            if (!item) {
                return;
            }
            const x = 30 + (((new Date(item.capturedAt).getTime() - Math.min(...cashflowItems.concat(snapshotPoints.map((point) => point.item)).map((entry) => new Date(entry.capturedAt).getTime()))) / Math.max((Math.max(...cashflowItems.concat(snapshotPoints.map((point) => point.item)).map((entry) => new Date(entry.capturedAt).getTime())) - Math.min(...cashflowItems.concat(snapshotPoints.map((point) => point.item)).map((entry) => new Date(entry.capturedAt).getTime()))), 1)) * (chartWidth - 50));
            tooltip.hidden = false;
            tooltip.innerHTML = `
                <div>${escapeHtml(formatTimestamp(item.capturedAt))}</div>
                <div>${escapeHtml(item.title)}</div>
                <div>金額 ${escapeHtml(formatSignedNumber(item.amount))}</div>
                <div>${escapeHtml(item.note || item.source || "--")}</div>
            `;
            tooltip.style.left = `${(x / chartWidth) * 100}%`;
            tooltip.style.top = `${((chartHeight - 80) / chartHeight) * 100}%`;
        });
    });

    chart.addEventListener("mouseleave", hideLogsTooltip);
}

function hideLogsTooltip() {
    const tooltip = byId("logs-chart-tooltip");
    if (tooltip) {
        tooltip.hidden = true;
    }
}

function buildTicks(minTs, maxTs, count) {
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

function amountClass(value) {
    if (value === null || value === undefined) {
        return "";
    }
    if (Number(value) > 0) {
        return "amount-positive";
    }
    if (Number(value) < 0) {
        return "amount-negative";
    }
    return "";
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

function formatPercent(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return "--";
    }
    return Number(value).toFixed(2) + "%";
}

function formatTimestamp(value) {
    return value ? new Date(value).toLocaleString() : "--";
}

function formatAxisTimestamp(value) {
    return new Date(value).toLocaleString(undefined, {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
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

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

function renderLogsError(error) {
    const body = byId("logs-body");
    if (body) {
        body.innerHTML = `<tr><td colspan="9">${escapeHtml(error.message)}</td></tr>`;
    }
    setText("page-label", "--");
}
