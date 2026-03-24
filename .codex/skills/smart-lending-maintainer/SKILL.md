---
name: smart-lending-maintainer
description: Maintain the Smart Lending Spring Boot service that reads Bitfinex market data and can create or cancel funding offers. Use when changing controllers, REST clients, scheduler strategy logic, Bitfinex request or response mapping, `.env` or `application.yml` configuration, or tests for funding summary, lendbook distribution, and live-offer workflows.
---

# Smart Lending Maintainer

Keep changes conservative because this repo can hit authenticated Bitfinex endpoints and trigger real offer actions.

## Workflow

1. Read `README.md` first to confirm public API behavior, scheduler intent, and env vars.
2. Read only the files on the change path before editing.
3. Treat any change under account clients, scheduler flow, or request models as potentially money-moving.
4. Prefer extending unit and controller tests before trusting behavior changes.
5. Validate with targeted Gradle tests after edits.

## Risk Boundaries

- Assume `BitfinexAccountRestClient` and `BitfinexFundingAccountRestClient` are live-operation boundaries.
- Do not introduce automatic offer creation or cancellation from new code paths unless the user explicitly asks.
- Keep credentials in `.env` or environment variables only. Do not hardcode keys, secrets, symbols, or base URLs meant to vary by environment.
- Preserve nonce, signature, and request-body behavior unless a test or upstream API change requires otherwise.
- When changing scheduler logic, check each early-return branch so the strategy still stops safely when rates, idle funds, or offer states do not qualify.

## Change Rules

### Controller and DTO changes

- Keep Swagger-exposed request and response contracts consistent with README examples unless the user asks for an API break.
- If a controller response changes, update or add controller tests.

### Bitfinex client changes

- Preserve exact endpoint paths and authenticated/public separation.
- Map raw exchange payloads defensively; prefer explicit validation over silent assumptions about array positions and numeric fields.
- If you change timeout or retry-related behavior, verify the corresponding property names still match `application.yml` and README.

### Scheduler changes

- Read `FundingRateThresholdSchedulerService` and its test together.
- Preserve the distinction between annual rate, daily rate, idle amount, offer amount, and chunked order sizes.
- Add or update tests for every changed decision branch: threshold detection, no-op branches, cancel-and-recreate flow, and same-rate handling.

## Validation

- Start with the narrowest relevant test class.
- For scheduler logic, run `.\gradlew.bat test --no-daemon --tests *FundingRateThresholdSchedulerServiceTest`.
- For controller contract changes, run the matching controller tests.
- If the change is cross-cutting, run the full `.\gradlew.bat test --no-daemon`.
- If tests cannot be run, state that explicitly and identify the unverified risk.

## Repo References

Read `references/project-map.md` when you need file routing, domain vocabulary, or the usual validation targets.
