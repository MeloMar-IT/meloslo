# MeloSlo Comprehensive Documentation

MeloSlo is a powerful, professional Spring Boot application designed to manage **OpenSLO** records. It provides a comprehensive interface for defining, tracking, and analyzing Service Level Objectives (SLOs), Service Level Indicators (SLIs), and Business Services.

## 📑 Table of Contents

1. [Key Features](#-key-features)
2. [Core Concepts](#-core-concepts)
3. [User Guide](#-user-guide)
4. [API Reference](#-api-reference)
5. [Database Schema](#-database-schema)
6. [Data Collection & Sources](#-data-collection--sources)
7. [Predictive Analysis](#-predictive-analysis)
8. [Architecture & Technology](#-architecture--technology)
9. [Developer Guide](#-developer-guide)
10. [License](#-license)

---

## 🚀 Key Features

*   **OpenSLO Management**: Create, read, update, and delete Services, SLIs, and SLOs in a vendor-neutral format.
*   **Predictive Analysis**: Forecast error budget depletion using linear regression and weekly seasonality.
*   **Interactive Dashboard**: Get a real-time overview of your services' health.
*   **PDF Reporting**: Generate compliance-ready annual performance reports with visualization for performance and error budgets.
*   **Alerting & Monitoring**: Integrate with external data sources (e.g., Prometheus) and alerting channels (e.g., Slack).
*   **Access Control**: Role-based access with departmental restrictions for secure multi-tenant management.

---

## 🌟 Core Concepts

To effectively use MeloSlo, it's essential to understand the core concepts of Service Level Objectives (SLOs) and the **OpenSLO** specification.

### What are OpenSLO Records?

**OpenSLO** is an open-source specification for defining SLOs in a common, vendor-neutral format. MeloSlo implements this specification to provide a standard way to manage service reliability across different monitoring and alerting systems.

### Core Entities in MeloSlo

The system is built around four primary entities:

1. **Business Service**: A logical collection of SLOs. It represents a high-level system, application, or component (e.g., "Finance API," "Engineering Frontend").
2. **SLI (Service Level Indicator)**: A metric that measures a specific aspect of a service's performance (e.g., Latency, Availability, Throughput). **Important**: All SLI metric values in MeloSlo are normalized to a range of **0 to 100**.
3. **SLO (Service Level Objective)**: Defines the target performance level (e.g., 99.9%) for an SLI over a rolling window (e.g., last 30 days).
4. **Error Budget**: The amount of unreliability allowed before an SLO is violated (`100% - SLO Target`).

### Status Definitions

| Status | Description |
| :--- | :--- |
| **🟢 Healthy** | Performance is within the target, and the error budget is largely intact. |
| **🟡 Warning** | The error budget is being consumed at a rate that could lead to a breach. |
| **🔴 Breaching** | The current performance has fallen below the target, or the error budget is exhausted. |

---

## 📖 User Guide

The MeloSlo interface is designed for intuitive management of OpenSLO records.

### Navigation

The application features a sidebar providing access to:
- **Dashboard**: High-level health overview.
- **Services/SLOs/SLIs**: Management of OpenSLO records.
- **Data/Alerting Sources**: Integration configurations.
- **Reports**: Annual PDF performance reports.
- **Administrative Tools**: User admin and SQL query tool (Admin only).

### Managing SLO Records

- **Business Services**: Define logical groupings, optionally assigned to departments.
- **SLO Details**: View performance charts, error budget consumption, and predictive trends.

### Access Control & Departments

- **Administrator**: Full system access.
- **Standard User**: Access limited to assigned departments. Users only see records matching their department (e.g., `Finance, Engineering`).

---

## 🔌 API Reference

MeloSlo provides a RESTful API for programmatic management of OpenSLO records, metric data, and reporting. All endpoints are prefixed with `/api/v1`.

### Authentication

The API uses standard HTTP Basic Authentication or session-based cookies. Ensure you provide valid credentials with each request.

### Core Endpoints

| Category | Endpoint | Method | Description |
| :--- | :--- | :--- | :--- |
| **OpenSLO** | `/openslo` | `GET` | List all records (Services, SLOs, SLIs, Data/Alerting Sources). |
| | `/openslo` | `POST` | Create a new OpenSLO record. |
| | `/openslo/{id}` | `GET` | Retrieve a specific record by ID. |
| | `/openslo/{id}` | `PUT` | Update an existing record. |
| | `/openslo/{id}` | `DELETE` | Delete a record. |
| **Analytics** | `/openslo/dashboard-stats` | `GET` | Get aggregated health and count statistics. |
| | `/openslo/{id}/report` | `GET` | Get detailed performance data for an SLO. |
| | `/openslo/{id}/service-report` | `GET` | Get an aggregated report for a Business Service. |
| **Metrics** | `/metrics` | `GET` | List all recorded metric data points. |
| | `/metrics` | `POST` | Record a new SLI metric value. |
| **Reports** | `/reports/pdf` | `POST` | Generate and download a PDF performance report. |
| **Users** | `/users/me` | `GET` | Get details of the currently authenticated user. |
| | `/users` | `GET` | List all users (Admin only). |
| | `/users` | `POST` | Create a new user (Admin only). |
| **Database** | `/database/tables` | `GET` | List all internal database tables (Admin only). |
| | `/database/query` | `POST` | Execute a raw SQL `SELECT` query (Admin only). |

### Example Request (Creating an SLI)

```bash
curl -X POST http://localhost:8080/api/v1/openslo \
     -u admin:admin \
     -H "Content-Type: application/json" \
     -d '{
       "apiVersion": "openslo/v1",
       "kind": "SLI",
       "name": "api-latency",
       "displayName": "API Latency SLI",
       "spec": "thresholdMetric"
     }'
```

---

## 🗄️ Database Schema

MeloSlo uses an H2 in-memory database to store configuration and performance data. The schema consists of three primary tables.

### 1. `OPEN_SLO` (The Core Repository)
Stores all OpenSLO-related entities (Services, SLIs, SLOs, Sources).

| Column | Type | Description |
| :--- | :--- | :--- |
| `ID` | `BIGINT` | Primary Key. |
| `KIND` | `VARCHAR` | The entity type (e.g., `Service`, `SLI`, `SLO`). |
| `NAME` | `VARCHAR` | Unique identifier name. |
| `DISPLAY_NAME` | `VARCHAR` | Human-readable label. |
| `SPEC` | `VARCHAR` | YAML/JSON specification details. |
| `DEPARTMENT` | `VARCHAR` | Assigned department for access control. |
| `STATUS` | `VARCHAR` | Current health status (Healthy, Warning, Breaching). |
| `ERROR_BUDGET` | `DOUBLE` | Remaining error budget percentage. |

### 2. `SLI_METRIC` (Performance Data)
Stores historical metric data points collected for SLIs.

| Column | Type | Description |
| :--- | :--- | :--- |
| `ID` | `BIGINT` | Primary Key. |
| `TIMESTAMP` | `TIMESTAMP` | When the metric was recorded. |
| `METRIC_VALUE` | `DOUBLE` | The value (normalized 0-100). |
| `SLI_ID` | `BIGINT` | Foreign Key to `OPEN_SLO`. |
| `DATASOURCE_ID`| `BIGINT` | Foreign Key to the source `OPEN_SLO`. |

### 3. `MELO_USERS` (Access Management)
Stores user accounts and permissions.

| Column | Type | Description |
| :--- | :--- | :--- |
| `ID` | `BIGINT` | Primary Key. |
| `USERNAME` | `VARCHAR` | Login identifier. |
| `PASSWORD` | `VARCHAR` | BCrypt encoded password. |
| `EMAIL` | `VARCHAR` | User email address. |
| `DEPARTMENTS` | `VARCHAR` | Comma-separated list of allowed departments. |

---

## 📡 Data Collection & Sources

MeloSlo includes an automated background scheduler that handles metric collection from external systems. This ensures that your SLOs are always evaluated against the latest performance data.

### DataSources

A **DataSource** defines the connection to an external monitoring system (e.g., Prometheus, Datadog, or Elasticsearch).

-   **Type**: The provider type (e.g., `prometheus`).
-   **URL**: The base URL of the monitoring system API.
-   **Refresh Rate**: The frequency at which MeloSlo should fetch new metrics. The minimum refresh rate is **15 minutes** (default is 60 minutes).

### How Data is Fetched

The `MetricFetchScheduler` component runs every **15 minutes** and performs the following steps for each active DataSource:

1.  **Check Interval**: It verifies if the time since the `lastRefreshTime` exceeds the DataSource's configured `refreshRate`.
2.  **Asynchronous Execution**: If an update is due, it spawns an asynchronous task via the `TaskManagementService`.
3.  **Fetch Process**:
    -   It identifies all **SLIs** linked to that specific DataSource.
    -   It "fetches" the current value for each SLI (currently using a simulation that generates values between 0.9 and 1.0, representing high reliability).
    -   In a production environment, this would involve executing queries against the external system's API (e.g., a PromQL query for Prometheus).
4.  **Persistence**: The fetched values are normalized (0-100) and stored in the `SLI_METRIC` table with a current timestamp.
5.  **Immediate Evaluation**: After metrics are saved, the system immediately checks all **SLOs** linked to those SLIs for potential breaches and triggers alerts if necessary.

### Linking SLIs to DataSources

To automate data collection, an SLI must be linked to a DataSource during creation or editing. If an SLI is not linked to any DataSource, its metrics must be provided manually via the API or another external process.

---

## 📈 Predictive Analysis

MeloSlo includes an advanced trend prediction engine designed to forecast when an SLO's error budget will be depleted.

### The Model: Additive Modeling

The engine decomposes data into:
1. **Linear Trend**: A linear regression trajectory of error budget consumption.
2. **Weekly Seasonality**: Adjustments for day-of-week patterns (e.g., weekend traffic spikes).
3. **Residuals**: Refinements based on recent variance.

### Forecast Horizon

MeloSlo provides a **30-day forecast**. If the projected line crosses the 0% threshold, it indicates a likely SLO breach within the next month.

---

## 🏗️ Architecture & Technology

### Technology Stack

- **Backend**: Spring Boot 3.2.0 (Java 17)
- **Database**: H2 Database (In-memory)
- **Security**: Spring Security (RBAC)
- **Reporting**: OpenPDF and JFreeChart

### Core Components

- **OpenSloService**: Central logic and predictive analysis engine.
- **TaskManagementService (The Watchdog)**: Manages async background tasks (e.g., metric collection) and terminates any task exceeding **5 minutes** to ensure stability.
- **Alerting Engine**: Monitors thresholds and triggers notifications (e.g., Slack).

---

## 🚀 Developer Guide

### Setting Up the Environment

1.  **Clone & Build**:
    ```bash
    git clone https://github.com/MeloMar-IT/meloslo.git
    mvn clean install
    ```
2.  **Run**: `mvn spring-boot:run`
3.  **Access**: `http://localhost:8080` (Default: `admin` / `admin`)

### GitFlow Workflow

- `main`: Production state.
- `develop`: Integration branch.
- `feature/*`: Feature development (branch from `develop`).
- `hotfix/*`: Production patches (branch from `main`).

### Testing Strategy

- **Unit Tests**: Services and components.
- **Functional Tests**: E2E features like PDF reporting.
- **Trend Analysis Tests**: Validation of predictive modeling.
- **Run all tests**: `mvn test`

---

## ⚖️ License

MeloSlo is licensed under the [GNU General Public License v3.0](https://github.com/MeloMar-IT/meloslo/blob/main/LICENSE).

> **Disclaimer**: MeloSlo is provided as an open-source project "as is" and on your own risk.
