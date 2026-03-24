# Smart Lending Project Map

## Core Runtime Files

- `src/main/java/io/github/yienruuuuu/smartlending/service/BitfinexFundingMarketRestClient.java`: public market data client for ticker and lendbook queries.
- `src/main/java/io/github/yienruuuuu/smartlending/service/BitfinexAccountRestClient.java`: authenticated signing, nonce handling, and wallet fetch foundation.
- `src/main/java/io/github/yienruuuuu/smartlending/service/BitfinexFundingAccountRestClient.java`: authenticated funding-offer, credits, and loans operations.
- `src/main/java/io/github/yienruuuuu/smartlending/service/FundingAccountSummaryService.java`: funding wallet, open offers, credits, and loans aggregation.
- `src/main/java/io/github/yienruuuuu/smartlending/service/FundingRateThresholdSchedulerService.java`: scheduled strategy that can cancel and recreate offers.
- `src/main/java/io/github/yienruuuuu/smartlending/controller/FundingAccountController.java`: account and offer endpoints.
- `src/main/java/io/github/yienruuuuu/smartlending/controller/FundingMarketController.java`: market and lendbook endpoints.
- `src/main/java/io/github/yienruuuuu/smartlending/config/BitfinexProperties.java`: env-backed configuration binding.
- `src/main/resources/application.yml`: default property values.

## Tests To Reach For First

- `src/test/java/io/github/yienruuuuu/smartlending/service/FundingRateThresholdSchedulerServiceTest.java`: scheduler branch coverage and offer-chunking behavior.
- `src/test/java/io/github/yienruuuuu/smartlending/controller/FundingAccountControllerTest.java`: account API contracts.
- `src/test/java/io/github/yienruuuuu/smartlending/controller/FundingMarketControllerTest.java`: market API contracts.

## Domain Facts

- Public market endpoints can be queried without credentials.
- Account and funding-offer endpoints require `BITFINEX_API_KEY` and `BITFINEX_API_SECRET`.
- Scheduler strategy is the highest-risk area because it can drive offer cancellation and creation decisions.
- Offer pricing uses annual percentage for strategy selection, then converts to Bitfinex daily rate for submission.
- Offer chunking currently uses `150` USD as the base amount and merges any remainder into the last chunk.

## Usual Validation Order

1. Run the narrowest affected test class.
2. Run all controller tests if DTO or endpoint contracts moved.
3. Run full Gradle test suite before finishing cross-cutting changes.
