# smart-lending

使用 Spring Boot 3.2 建立 Bitfinex 融資放貸第一版骨架，目前提供 RESTful API 查詢帳戶 wallet、公開 funding ticker 與 lendbook 摘要，以及使用私有 REST API 查詢 / 建立 / 取消 funding offers；暫時不包含資料庫、部位管理與策略執行。

## 第一版內容

- Spring Boot 3.2 + Gradle 專案骨架
- 使用 `.env` 讀取 API key / secret
- 提供 RESTful API 查詢 Bitfinex 帳戶 wallet 資料
- 提供 RESTful API 查詢 funding offers、credits、loans
- 提供 RESTful API 建立與取消 funding offer
- 提供 RESTful API 查詢目前 funding ticker 與 FRR
- 提供 RESTful API 查詢 lendbook ask 匯總、FRR ask 金額與 FRR 佔比
- 提供 RESTful API 依期間區間與利率小數位分桶統計 lendbook ask 總額
- 每 10 分鐘自動查詢一次特定 funding rate distribution，並記錄門檻利率
- 提供 Swagger UI 方便直接測試 API

## 本機設定

請填寫根目錄的 `.env`：

```dotenv
BITFINEX_API_KEY=
BITFINEX_API_SECRET=
```

目前市場查詢 API 不會使用 `BITFINEX_API_KEY` 與 `BITFINEX_API_SECRET`，但帳戶查詢與 funding offer 操作 API 會使用這兩個欄位，且 key 需要有對應權限。

## 可調整參數

可透過 `.env` 或系統環境變數覆寫：

```dotenv
BITFINEX_REST_BASE_URL=https://api.bitfinex.com
BITFINEX_PUBLIC_BASE_URL=https://api-pub.bitfinex.com
BITFINEX_MARKET_CONNECT_TIMEOUT_SECONDS=10
BITFINEX_MARKET_READ_TIMEOUT_SECONDS=30
```

如果 `lendbook` 在 `limit_asks=10000` 時偶爾 timeout，可以優先調高 `BITFINEX_MARKET_READ_TIMEOUT_SECONDS`。

## 執行方式

```bash
./gradlew bootRun
```

Windows PowerShell：

```powershell
.\gradlew.bat bootRun
```

## Swagger 與 REST API

啟動後可開：

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

市場查詢 API：

```http
GET /api/v1/funding/market/ticker?symbol=fUSD
GET /api/v1/funding/market/lendbook/summary?currency=USD&limitAsks=10000&minPeriodExclusive=30
GET /api/v1/funding/market/lendbook/rate-distribution?currency=USD&minPeriod=2&maxPeriod=30&limitAsks=10000&rateScale=1
```

`/api/v1/funding/market/ticker` 會呼叫 Bitfinex 官方 public REST endpoint `GET /v2/ticker/{Symbol}`，其中 funding ticker 的 `FRR` 來自回傳陣列第 1 個欄位，`FRR_AMOUNT_AVAILABLE` 來自第 16 個欄位。

`/api/v1/funding/market/lendbook/summary` 會呼叫 Bitfinex v1 public REST endpoint `GET /v1/lendbook/{currency}`，並額外參考 `GET /v2/ticker/{Symbol}`。回傳欄位分成兩種口徑：

- `minPeriodExclusive`: 只統計 `period > 這個值` 的 asks；例如 `30` 代表只保留 `period > 30`
- `frrAskAmountFromBook`: 直接依 `lendbook asks` 內 `frr=yes` 加總
- `fixedRateAskAmountFromBook`: 直接依 `lendbook asks` 內 `frr!=yes` 加總
- `amountBeforeFirstFrrAsk`: 依利率排序的 asks 中，第一筆 FRR 之前所有 amount 的累計
- `frrAmountAvailableFromTicker`: funding ticker 的 `FRR_AMOUNT_AVAILABLE`
- `nonFrrOrderBookAmountByTicker`: `totalAskAmount - frrAmountAvailableFromTicker`

`/api/v1/funding/market/lendbook/rate-distribution` 會直接統計 `v1 lendbook` asks：

- `minPeriod`: 只保留 `period >= minPeriod` 的訂單，例如 `2`
- `maxPeriod`: 只保留 `period <= maxPeriod` 的訂單，例如 `30`
- `limitAsks`: 最多讀取多少筆 asks
- `rateScale`: 利率四捨五入到幾位小數，例如 `1` 會把 `14.64` 歸到 `14.6`
- `buckets[].roundedRate`: 分桶後利率
- `buckets[].totalAmount`: 該利率桶總額
- `buckets[].amountSharePercent`: 該利率桶金額占比

例如回傳可長這樣：

```json
{
  "currency": "USD",
  "minPeriod": 2,
  "maxPeriod": 30,
  "limitAsks": 10000,
  "rateScale": 1,
  "matchedAskCount": 5,
  "matchedTotalAmount": 11000000,
  "buckets": [
    {
      "roundedRate": 14.6,
      "orderCount": 2,
      "totalAmount": 3000000,
      "amountShareRatio": 0.27272727,
      "amountSharePercent": 27.27
    },
    {
      "roundedRate": 14.7,
      "orderCount": 3,
      "totalAmount": 8000000,
      "amountShareRatio": 0.72727273,
      "amountSharePercent": 72.73
    }
  ]
}
```

## 內部排程

系統啟動後每 10 分鐘會在程式內部直接呼叫 funding rate-distribution 邏輯，不經過 HTTP：

- `currency=USD`
- `minPeriod=60`
- `maxPeriod=120`
- `limitAsks=10000`
- `rateScale=1`

排程會：

- `log.info` 印出整份查詢結果
- 找到第一筆 `cumulativeSharePercent > 5.0` 的 bucket
- 取前一筆 bucket，並以 `log.info` 記錄該筆利率

如果第一筆 bucket 就已經大於 `5.0`，則會記錄目前第一筆已超過門檻，沒有前一筆可取。

注意：這些市場 API 反映的是目前查回的 lendbook ask snapshot，不是已成交借出的總額。

帳戶查詢與操作 API：

```http
GET  /api/v1/account/wallets
GET  /api/v1/account/funding/summary?symbol=fUSD
GET  /api/v1/account/funding/offers?symbol=fUSD
POST /api/v1/account/funding/offers
POST /api/v1/account/funding/offers/cancel
GET  /api/v1/account/funding/credits?symbol=fUSD
GET  /api/v1/account/funding/loans?symbol=fUSD
```

這些 API 會呼叫 Bitfinex 官方 authenticated REST endpoints：

- `POST /v2/auth/r/wallets`
- `POST /v2/auth/r/funding/offers/{Symbol}`
- `POST /v2/auth/r/funding/credits/{Symbol}`
- `POST /v2/auth/r/funding/loans/{Symbol}`
- `POST /v2/auth/w/funding/offer/submit`
- `POST /v2/auth/w/funding/offer/cancel`

建立 funding offer request 範例：

```json
{
  "symbol": "fUSD",
  "amount": "1000",
  "rate": "0.0002",
  "period": 30,
  "type": "LIMIT",
  "flags": 0
}
```

取消 funding offer request 範例：

```json
{
  "offerId": 123456789
}
```

`/api/v1/account/funding/summary` 會額外整合 funding wallet、offers、credits、loans，直接回傳總錢包金額、已成交金額、閒置金額與掛單資料。其餘 funding API 仍完整保留 `raw`，並附上 `decoded` 方便在 Swagger 直接閱讀。

## 專案結構

- `src/main/java/.../SmartLendingApplication.java`: 啟動點、`.env` 載入與排程啟用
- `src/main/java/.../config/BitfinexProperties.java`: Bitfinex 設定綁定
- `src/main/java/.../service/BitfinexFundingMarketRestClient.java`: Bitfinex public funding ticker and lendbook client
- `src/main/java/.../service/BitfinexAccountRestClient.java`: Bitfinex authenticated REST client
- `src/main/java/.../service/FundingRateThresholdSchedulerService.java`: 每 10 分鐘查詢 funding rate distribution 並記錄門檻利率
- `src/main/java/.../controller/FundingMarketController.java`: funding ticker / FRR / lendbook summary REST API
- `src/main/resources/logback-spring.xml`: Logback 設定
- `src/main/resources/application.yml`: 預設設定值

## 未來開發方向

1. 私有 REST API 擴充，補上 wallet transfer 等操作。
2. 建立 application service 層，將 REST 原始資料轉成內部領域事件與聚合，避免策略直接依賴交易所 payload。
3. 加入資料庫，先存 funding book、成交、餘額、策略決策與操作紀錄，方便回測、稽核與異常追查。
4. 加入排程與策略模組，例如最小利率門檻、分批放貸、期限配置、風險上限與資金利用率控制。
5. 補上 resilience 設計，包括 rate limit 保護、metrics、告警、request tracing 與 dead-letter 記錄。
6. 建立測試分層，至少補齊簽章測試、controller 測試與 sandbox 整合測試。
7. 未來若要上線，建議加入 secrets 管理、簽章封裝、操作審批、速率限制與關鍵指令雙重保護。
