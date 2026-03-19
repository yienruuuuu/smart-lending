# smart-lending

使用 Spring Boot 3.2 建立 Bitfinex 融資放貸第一版骨架，目前改為每 10 秒輪詢一次 Bitfinex 公開 funding book，以及用 RESTful API 查詢帳戶 wallet 與公開 funding ticker；暫時不包含資料庫、私有 API 下單、部位管理與策略執行。

## 第一版內容

- Spring Boot 3.2 + Gradle 專案骨架
- 使用 Bitfinex 公開 REST API 輪詢 funding book，預設每 10 秒查一次 `fUSD`
- 解析 raw funding 掛單並記錄 order id、方向、利率、天期與數量
- 使用 Logback 紀錄 funding 掛單變化
- 使用 `.env` 讀取 API key / secret
- 提供 RESTful API 查詢 Bitfinex 帳戶 wallet 資料
- 提供 RESTful API 查詢 funding offers、credits、loans
- 提供 RESTful API 查詢目前 funding ticker 與 FRR
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
```

這支 API 會呼叫 Bitfinex 官方 public REST endpoint `GET /v2/ticker/{Symbol}`，其中 funding ticker 的 `FRR` 來自回傳陣列第 1 個欄位，`FRR_AMOUNT_AVAILABLE` 來自第 16 個欄位。

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
- `src/main/java/.../service/BitfinexFundingMarketRestClient.java`: Bitfinex public funding ticker client
- `src/main/java/.../service/BitfinexAccountRestClient.java`: Bitfinex authenticated REST client
- `src/main/java/.../controller/FundingMarketController.java`: funding ticker / FRR REST API
- `src/main/java/.../controller/AccountController.java`: 帳戶資料 REST API
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
