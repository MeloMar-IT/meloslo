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
- `DatabaseControllerUnitTest`: Tests the SQL query tool for administrators.

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
- `DatabaseFunctionalTest`: Tests the administrative SQL query tool against the real database schema.

### 2.2 Integration & Security
- `SecurityIntegrationTest`: Verifies the authentication and authorization filters, ensuring that endpoints are protected and departmental restrictions are enforced.
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
