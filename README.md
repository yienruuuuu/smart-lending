# smart-lending

使用 Spring Boot 3.2 建立 Bitfinex 融資放貸第一版骨架，目前改為每 10 秒輪詢一次 Bitfinex 公開 funding book，以及用 RESTful API 查詢帳戶 wallet、公開 funding ticker 與 lendbook 摘要；暫時不包含資料庫、私有 API 下單、部位管理與策略執行。

## 第一版內容

- Spring Boot 3.2 + Gradle 專案骨架
- 使用 Bitfinex 公開 REST API 輪詢 funding book，預設每 10 秒查一次 `fUSD`
- 解析 raw funding 掛單並記錄 order id、方向、利率、天期與數量
- 使用 Logback 紀錄 funding 掛單變化
- 使用 `.env` 讀取 API key / secret
- 提供 RESTful API 查詢 Bitfinex 帳戶 wallet 資料
- 提供 RESTful API 查詢 funding offers、credits、loans
- 提供 RESTful API 查詢目前 funding ticker 與 FRR
- 提供 RESTful API 查詢 lendbook ask 匯總、FRR ask 金額與 FRR 佔比
- 提供 RESTful API 依天數與利率小數位分桶統計 lendbook ask 總額
- 提供 Swagger UI 方便直接測試 API

## 本機設定

請填寫根目錄的 `.env`：

```dotenv
BITFINEX_API_KEY=
BITFINEX_API_SECRET=
```

目前 funding 輪詢與市場查詢 API 不會使用 `BITFINEX_API_KEY` 與 `BITFINEX_API_SECRET`，但帳戶查詢 API 會使用這兩個欄位，且 key 需要有讀取 wallet 權限。

## 可調整參數

可透過 `.env` 或系統環境變數覆寫：

```dotenv
BITFINEX_REST_BASE_URL=https://api.bitfinex.com
BITFINEX_PUBLIC_BASE_URL=https://api-pub.bitfinex.com
BITFINEX_FUNDING_SYMBOL=fUSD
BITFINEX_BOOK_PRECISION=R0
BITFINEX_BOOK_LENGTH=25
BITFINEX_POLLING_INTERVAL_SECONDS=10
```

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

輪詢控制 API：

```http
GET  /api/v1/funding/polling/status
POST /api/v1/funding/polling/start
POST /api/v1/funding/polling/stop
POST /api/v1/funding/polling/run-once?symbol=fUSD
```

市場查詢 API：

```http
GET /api/v1/funding/market/ticker?symbol=fUSD
GET /api/v1/funding/market/lendbook/summary?currency=USD&limitAsks=10000&minPeriodExclusive=30
GET /api/v1/funding/market/lendbook/rate-distribution?currency=USD&period=30&limitAsks=10000&rateScale=1
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

- `period`: 只保留該天數的訂單，例如 `30`
- `limitAsks`: 最多讀取多少筆 asks
- `rateScale`: 利率四捨五入到幾位小數，例如 `1` 會把 `14.64` 歸到 `14.6`
- `buckets[].roundedRate`: 分桶後利率
- `buckets[].totalAmount`: 該利率桶總額
- `buckets[].amountSharePercent`: 該利率桶金額占比

例如回傳可長這樣：

```json
{
  "currency": "USD",
  "period": 30,
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

注意：這些市場 API 反映的是目前查回的 lendbook ask snapshot，不是已成交借出的總額。

帳戶查詢 API：

```http
GET /api/v1/account/wallets
GET /api/v1/account/funding/summary?symbol=fUSD
GET /api/v1/account/funding/offers?symbol=fUSD
GET /api/v1/account/funding/credits?symbol=fUSD
GET /api/v1/account/funding/loans?symbol=fUSD
```

這支 API 會呼叫 Bitfinex 官方 authenticated REST endpoint `POST /v2/auth/r/wallets`，再把結果整理成較易讀的 JSON。
Funding 相關 API 分別對應官方：

- `POST /v2/auth/r/funding/offers/{Symbol}`
- `POST /v2/auth/r/funding/credits/{Symbol}`
- `POST /v2/auth/r/funding/loans/{Symbol}`

`/api/v1/account/funding/summary` 會額外整合 funding wallet、offers、credits、loans，直接回傳總錢包金額、已成交金額、閒置金額與掛單資料。其餘 funding API 仍完整保留 `raw`，並附上 `decoded` 方便在 Swagger 直接閱讀。

## 排程行為

系統啟動後會固定輪詢：

- `GET https://api-pub.bitfinex.com/v2/book/fUSD/R0?len=25`
- 預設每 10 秒一次
- 每次會將原始回應完整 JSON 化後記錄到 log，並附上 decoded 欄位輔助閱讀

## 專案結構

- `src/main/java/.../SmartLendingApplication.java`: 啟動點、`.env` 載入與排程啟用
- `src/main/java/.../config/BitfinexProperties.java`: Bitfinex 設定綁定
- `src/main/java/.../service/BitfinexFundingPollingService.java`: funding book 排程輪詢
- `src/main/java/.../service/BitfinexFundingMarketRestClient.java`: Bitfinex public funding ticker and lendbook client
- `src/main/java/.../service/BitfinexAccountRestClient.java`: Bitfinex authenticated REST client
- `src/main/java/.../controller/FundingMarketController.java`: funding ticker / FRR / lendbook summary REST API
- `src/main/resources/logback-spring.xml`: Logback 設定
- `src/main/resources/application.yml`: 預設設定值

## 未來開發方向

1. 私有 REST API 擴充，補上實際放貸、取消掛單、查詢 funding offers、credits、loans 與 wallet transfer。
2. 建立 application service 層，將輪詢與 REST 原始資料轉成內部領域事件與聚合，避免策略直接依賴交易所 payload。
3. 加入資料庫，先存 funding book、成交、餘額、策略決策與操作紀錄，方便回測、稽核與異常追查。
4. 加入排程與策略模組，例如最小利率門檻、分批放貸、期限配置、風險上限與資金利用率控制。
5. 補上 resilience 設計，包括 polling timeout、rate limit 保護、metrics、告警、request tracing 與 dead-letter 記錄。
6. 建立測試分層，至少補齊 funding parser 測試、簽章測試、controller 測試與 sandbox 整合測試。
7. 未來若要上線，建議加入 secrets 管理、簽章封裝、操作審批、速率限制與關鍵指令雙重保護。
