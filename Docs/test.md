# MeloSlo Testing Documentation

This document provides an overview of the testing strategy and the types of tests implemented in the MeloSlo project.

## 1. Unit Tests

Unit tests focus on individual components in isolation, using mocks for dependencies. They are located in `src/test/java/com/example/meloslo/`.

### 1.1 Controllers
Controller unit tests use `@WebMvcTest` and `MockMvc` to verify REST API endpoints, request mapping, status codes, and JSON responses without starting the full application context.
- `OpenSloControllerUnitTest`: Verifies CRUD operations for OpenSLO records and dashboard statistics.
- `ReportControllerUnitTest`: Tests PDF report generation endpoints.
- `UserControllerUnitTest`: Tests user management and profile endpoints.
- `MetricControllerUnitTest`: Verifies SLI metric retrieval.
- `DatabaseControllerUnitTest`: Tests the database table endpoints for administrators. Checks that `FILE_READ`, `CSVWRITE` and `CREATE ALIAS` queries are refused and that `JdbcTemplate` is never called.

### 1.2 Services
Service unit tests verify the business logic, calculations, and interactions with repositories using Mockito.
- `OpenSloServiceTest`: Validates logic for filtering records by department, parsing OpenSLO specifications, and managing records.
- `SloCalculationTest`: Focuses on error budget calculations and status determination (Healthy, Warning, Breaching).
- `ReportServiceTest`: Tests the generation of PDF reports and data aggregation for reports.
- `AlertingServiceTest`: Verifies the webhook alerting logic, payload variable replacement, and anti-spam protection.
- `MetricFetchSchedulerTest`: Tests the background scheduling of metric collection from DataSources.
- `TaskManagementServiceTest`: Verifies the thread pool management, asynchronous task execution, and the 5-minute watchdog/cleanup logic.
- `TrendAnalysisTest`: Validates the predictive trend analysis logic using additive modeling (Linear Trend + Seasonality).

### 1.3 Repositories
Repository tests use `@DataJpaTest` to verify database interactions, custom queries, and entity mapping.
- `OpenSloRepositoryTest`: Tests persistence and retrieval of OpenSLO records.
- `MetricRepositoryTest`: Verifies SLI metric storage and historical data queries.
- `UserRepositoryTest`: Tests user account persistence and department-based filtering queries.
- `EncryptionTest`: Ensures that sensitive data (like credentials in spec) is correctly encrypted/decrypted.

## 2. Functional & Integration Tests

Functional tests verify the application as a whole or large parts of it, often involving a real database (H2 in-memory) and the full Spring context.

### 2.1 API Functional Tests
These tests inherit from `BaseFunctionalTest` and use `TestRestTemplate` to perform end-to-end HTTP requests.
- `OpenSloFunctionalTest`: Verifies full workflows for creating, updating, and deleting OpenSLO records.
- `UserFunctionalTest`: Tests user registration, login, and departmental access control.
- `MetricFunctionalTest`: Validates the full flow of metric collection and retrieval.
- `ReportFunctionalTest`: Ensures that the PDF reporting system works correctly end-to-end.
- `DatabaseFunctionalTest`: Tests the administrative database endpoints against the real database schema, including that `FILE_READ`, `CSVWRITE` and `CREATE ALIAS` are refused.

### 2.2 Integration & Security
- `SecurityIntegrationTest`: Verifies the authentication and authorization filters, ensuring that endpoints are protected and departmental restrictions are enforced.
- `H2ConsoleDefaultProfileTest`: With no profile set, `/h2-console` returns 404 for anonymous users and for `admin/admin`.
- `H2ConsoleDevProfileTest`: Under `dev`, anonymous requests get 401, `testuser` gets 403, and `admin` gets a 2xx with `X-Frame-Options: SAMEORIGIN`.
- `ProdProfileSecurityTest`: Under `prod`, `/h2-console` returns 404, no `testuser` exists, and `admin` cannot log in with `admin`.
- `DataInitializerTest` covers live-mode admin creation: a random password of at least 20 characters, a configured password, a blank password falling back to random, an existing admin left unchanged, and two random passwords differing.
- `MeloSloApplicationTests`: A simple sanity check to ensure the Spring application context loads correctly.

## 3. Running Tests

To run all tests in the project, use the following Maven command:

```bash
./mvnw test
```

To run a specific test class:

```bash
./mvnw test -Dtest=OpenSloServiceTest
```

## 4. Continuous Integration

The workflow `.github/workflows/ci.yml` builds and tests the project on every push and every pull request. It:

- checks out the repository (`actions/checkout@v4`);
- installs Temurin JDK 17, matching `<java.version>` in `pom.xml`, with the Maven cache enabled (`actions/setup-java@v4`);
- runs `./mvnw -B verify` on `ubuntu-latest`, which compiles the code and runs the full test suite.

A failing compilation or failing test fails the check, so a broken build cannot be merged unnoticed. Test failures are not skipped or ignored. Runs for the same branch or pull request are cancelled when a newer commit is pushed. The job has a 20-minute timeout.

To reproduce the CI build locally, run the same command from the project root:

```bash
./mvnw -B verify
```
