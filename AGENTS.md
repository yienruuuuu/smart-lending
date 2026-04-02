# Repository Guidelines

## Project Structure & Module Organization
This is a Gradle-based Spring Boot 3.2 service targeting Java 17. Application code lives under `src/main/java/io/github/yienruuuuu/smartlending`, split by responsibility into `controller`, `service`, `model`, and `config`. Runtime configuration is in `src/main/resources`, especially `application.yml` and `logback-spring.xml`. Tests mirror the main package structure under `src/test/java`. Container files are at the repo root: `Dockerfile`, `compose.yaml`, and `.env`.

## Build, Test, and Development Commands
Use the Gradle wrapper so local tool versions stay consistent.

- `.\gradlew.bat bootRun`: start the API locally on port `8085`.
- `.\gradlew.bat test --no-daemon`: run the JUnit 5 test suite.
- `.\gradlew.bat build`: compile, test, and package the application.
- `docker compose up -d --build`: build and run the containerized service with values from `.env`.

For Unix-like shells, replace `.\gradlew.bat` with `./gradlew`.

## Coding Style & Naming Conventions
Follow existing Spring and Java conventions: 4-space indentation, one public class per file, and package names in lowercase. Use `PascalCase` for classes, `camelCase` for methods and fields, and descriptive DTO/service names such as `FundingAccountSummaryService`. Keep controllers thin, push exchange logic into services, and place configuration binding in `config`. Match the current style for Javadoc and validation annotations when adding public APIs or request models.

## Testing Guidelines
Tests use `spring-boot-starter-test`, JUnit 5, Mockito, and MockMvc. Name test classes `*Test` and use behavior-focused method names such as `shouldReturnFundingTicker`. Prefer `@WebMvcTest` for controller slices and focused unit tests for service logic. Run `.\gradlew.bat test --no-daemon` before opening a PR. No coverage gate is configured, so add tests for new endpoints, scheduler branches, and Bitfinex mapping logic you change.

## Commit & Pull Request Guidelines
Recent history uses short subjects like `init` and `skill`; raise the bar for new work with imperative, specific commit messages such as `Add funding offer cancel validation`. Keep each commit scoped to one change. PRs should summarize behavior changes, list verification steps, note any `.env` or API contract changes, and include Swagger or sample request/response snippets when an endpoint changes.

## Security & Configuration Tips
Never commit real Bitfinex credentials. Keep secrets in `.env` or environment variables only. If you change `bitfinex.*` properties or scheduler behavior, update both `README.md` and example configuration expectations in the PR description.
