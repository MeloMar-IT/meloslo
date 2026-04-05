# MeloSlo Documentation

## GitFlow Workflow

This repository follows the **GitFlow** branching model. For more details, see [GITFLOW.md](./GITFLOW.md).

### Core Branches
- `main`: Production-ready code.
- `develop`: Integration branch for features.

---

MeloSlo is a Spring Boot application designed to manage OpenSLO records. It provides a professional interface to create, read, update, and delete Services, SLIs, and SLOs.

## What are OpenSLO Records?

OpenSLO is an open-source specification for defining Service Level Objectives (SLOs) in a common, vendor-neutral format.

### Core Concepts

1.  **Service**: A logical collection of SLOs. It represents a system or component.
2.  **SLI (Service Level Indicator)**: A metric that measures some aspect of the service (e.g., latency, availability). **Important**: All SLI metric values must be in the range of **0 to 100**.
3.  **SLO (Service Level Objective)**: A target value or range of values for an SLI that defines the expected level of service.
4.  **Error Budget**: The amount of service unreliability that is allowed before the SLO is violated.

## How to use this Application

### Navigation

The application features a sidebar with the following views:
- **Dashboard**: Overview of your services and SLOs, including healthy, warning, and breaching status.
- **Services**: Manage your service records and associated SLOs.
- **SLOs**: Manage your service level objectives and view detailed error budget charts.
- **SLIs**: Manage your service level indicators.
- **Data Sources**: Manage your data source records (e.g., Prometheus).
- **Alerting**: Manage your alerting source records (e.g., Slack webhooks) to receive notifications when SLOs are breached.
- **Documentation**: Access this documentation directly in the app.
- **Query Tool**: Execute raw SQL queries against the database (Administrator only). Use this tool to inspect table structures (`api/v1/database/tables`) and verify raw data records across `OPEN_SLO`, `SLI_METRIC`, and `USER` tables. Only `SELECT` queries are permitted for security.
- **User Admin**: Manage user accounts and permissions (Administrator only).
- **Reports**: Generate annual performance reports for Services and SLOs in PDF format, including performance graphs for the last year. Reports are compliance-aware and automatically handle data gaps by visualizing them as zero-performance periods.
- **Logout**: Securely end your current session.

### Authentication and User Management

MeloSlo includes a secure login system and administrative tools to manage users and access control.

- **Default Administrator**: Username: `admin`, Password: `admin` (Full Access)
- **Test User**: Username: `testuser`, Password: `testuser` (Restricted to 'Finance' and 'Engineering' departments)

#### Access Control
- **Full Access**: Administrators (users without departmental restrictions) see all records in the system.
- **Departmental Restriction**: Users can be assigned to one or more departments (separated by commas). These users only see records associated with any of those departments across all views (Dashboard, Services, etc.).
- **Multiple Departments**: A user can be granted access to multiple departments by listing them in their profile (e.g., `Finance,Engineering`). This allows them to monitor and manage records for all assigned departments simultaneously.

### Creating and Editing Records

When creating or editing a record, you will be presented with several options:

- **Display Name**: A human-readable name for the record.
- **System Name (ID)**: A unique identifier used for referencing (cannot be changed after creation).
- **API Version**: The version of the OpenSLO specification (e.g., `openslo/v1`).
- **Kind**: The type of record (`Service`, `SLI`, `SLO`, `DataSource`, or `AlertingSource`).
- **DataSource and Alerting Templates**: When creating a `DataSource` or `AlertingSource`, you can select from pre-defined templates for popular providers (Prometheus, Slack, PagerDuty, etc.) to quickly populate the specification and configuration fields.
- **Refresh Rate**: For `DataSource` records, you can specify a refresh rate in minutes (minimum 15 minutes, defaults to 60). The application automatically fetches and updates metrics according to this schedule.
- **Department**: The organizational unit responsible for this record.
- **Manager**: The person responsible for the IT area.
- **Specification**: The full OpenSLO compliant specification in YAML or JSON format. Content from this field is parsed and displayed in relevant views.

### Relationships

- **SLOs** can be linked to one **Service** and one or more **SLIs**. Linking multiple SLIs to a single SLO allows for a consolidated reliability view across multiple metrics.
- **Services** can be linked to multiple **SLOs**.
- **SLIs** are linked to a **DataSource** which defines where their metrics are fetched from.
- **AlertingSources** can be linked to multiple **SLOs** to receive breach notifications.

### Alerting and Notifications

MeloSlo supports automated alerting when an SLO enters a 'Breaching' state.

- **Alerting Source**: Define a destination (e.g., a Slack Webhook URL) and a JSON payload template.
- **Payload Variables**: You can use variables in your payload template that will be automatically replaced:
  - `${SLO_NAME}`: The display name or system name of the SLO.
  - `${SLO_STATUS}`: The current status (e.g., 'Breaching').
  - `${SLO_VALUE}`: The current performance value of the SLI(s) linked to the SLO.
- **Anti-Spam Protection**: To prevent alert fatigue, the system will only send one alert per SLO every 6 hours while it remains in a breaching state.
- **SLIs** can be linked to a **DataSource** for automated metric collection.
- **SLOs** can be linked to an **AlertingSource** for automated breach notifications.
- **SLIs** can also have multiple **metricSources** in their specification, allowing you to track performance across different platforms or monitoring systems.
- When creating or editing records, you can select these associations using multi-select fields.

## Metric Collection and Refresh Rates

MeloSlo includes an automated scheduler that periodically "fetches" metrics from your defined DataSources.

- **Refresh Rate**: Each `DataSource` can be configured with a refresh rate (minimum 15 minutes, default 60 minutes).
- **Automated Collection**: The background scheduler checks each DataSource and, if the refresh interval has passed, it creates new `SliMetric` records for all SLIs linked to that DataSource.
- **Error Handling**: If a metric cannot be retrieved (simulated in this version), the value is automatically set to `0.0` to reflect a failure in the indicator.
- **Last Sync**: The application tracks and displays the last time each DataSource was successfully refreshed.

## Alerting and Webhooks

MeloSlo supports automated alerting when an SLO enters the **Breaching** status.

- **AlertingSource**: A record of kind `AlertingSource` defines a webhook URL and a JSON payload template.
- **Variables**: The payload template supports the following dynamic variables:
    - `${SLO_NAME}`: The display name (or name) of the breaching SLO.
    - `${SLO_STATUS}`: The current status (e.g., "Breaching").
    - `${SLO_VALUE}`: The current performance value of the SLO (percentage).
- **Association**: Each SLO can be linked to one `AlertingSource`.
- **Trigger**: When the background scheduler fetches metrics and detects that a linked SLO is breaching, it sends a POST request to the configured webhook URL.
- **Spam Prevention**: To prevent excessive notifications, MeloSlo will only send an alert once every hour for a specific SLO while it remains in a breaching state.

## OpenSLO Examples

### SLI with Multiple Metric Sources

An SLI can track metrics from multiple sources (e.g., both Prometheus and Datadog).

```yaml
apiVersion: openslo/v1
kind: SLI
metadata:
  name: multi-cloud-latency
spec:
  metricSources:
    - type: prometheus
      spec:
        query: histogram_quantile(0.95, sum(rate(http_duration_bucket{env="aws"}[5m])) by (le))
    - type: datadog
      spec:
        query: avg:trace.http.request.duration{env:azure}.p95
  description: Latency metrics from both AWS and Azure
```

### Standard SLO

```yaml
apiVersion: openslo/v1
kind: SLO
metadata:
  name: api-availability
spec:
  service: my-service
  description: 99.9% of requests must return 2xx or 3xx
  indicator:
    metricSource:
      type: prometheus
      spec:
        query: sum(rate(http_requests_total{status=~"2..|3.."}[5m])) / sum(rate(http_requests_total[5m]))
  objectives:
    - ratioMetrics:
        target: 0.999
```

### DataSource and Alerting Templates

MeloSlo provides built-in templates for several popular data source and alerting types to help you get started quickly.

#### Prometheus (DataSource)
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: prometheus-datasource
spec:
  type: prometheus
  config:
    url: http://prometheus:9090
```

#### Slack Webhook (AlertingSource)
```yaml
apiVersion: openslo/v1
kind: AlertingSource
metadata:
  name: slack-alerts
spec:
  type: slack-webhook
```

#### PagerDuty (AlertingSource)
```yaml
apiVersion: openslo/v1
kind: AlertingSource
metadata:
  name: pagerduty-alerts
spec:
  type: pagerduty
```

#### OpenTelemetry
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: otel-datasource
spec:
  type: opentelemetry
  config:
    endpoint: otel-collector:4317
```

#### Elasticsearch
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: elasticsearch-datasource
spec:
  type: elasticsearch
  config:
    url: http://elasticsearch:9200
    index: logs-*
```

#### Datadog
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: datadog-datasource
spec:
  type: datadog
  config:
    apiKey: YOUR_API_KEY
    appKey: YOUR_APP_KEY
```

#### Graphite
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: graphite-datasource
spec:
  type: graphite
  config:
    url: http://graphite:8080
```

#### Grafana Loki
```yaml
apiVersion: openslo/v1
kind: DataSource
metadata:
  name: loki-datasource
spec:
  type: loki
  config:
    url: http://loki:3100
```

## Metrics and SLO Calculation

MeloSlo supports storing actual performance metrics and calculating SLOs and Error Budgets over a rolling time window.

## Annual Reports

MeloSlo allows you to generate professional PDF reports for your Services and SLOs.

- **Time Period**: Reports always cover the performance over the last 365 days.
- **Selection**: You can select one or more Services or SLOs to include in a single PDF report.
- **Content**: Each report includes:
  - Average performance (SLO value) over the last year.
  - Defined target for the SLO.
  - Remaining error budget for the year.
  - Current health status (Healthy, Warning, Breaching) based on the annual performance.
  - Performance graphs with area filling (0 to value) and compliance highlighting (Green/Red).
- **Service Aggregation**: If a Service is selected, the report automatically includes all SLOs associated with that service.

## Startup Verification

Upon startup, MeloSlo automatically verifies the state of the database. It checks for:
- **Required Records**: Ensures that essential sample records (Services, DataSources, SLIs, and SLOs) are present.
- **Metric Data**: Confirms that each test SLI has at least 30 days of performance data.
- **Record Connections**: Verifies that all SLOs are correctly linked to a Service and their respective SLIs.

If any of these conditions are not met, the application will automatically reload the test data from scratch to ensure a consistent environment for demonstration and testing.

### Metric Records

Metrics are stored in the database and linked to an SLI and a DataSource. They represent the actual value of an indicator at a specific point in time.

- **Value Range**: All metric values must be between **0 and 100**.
- `POST /api/v1/metrics`: Create a new metric record.
- `GET /api/v1/metrics`: Retrieve all metric records.

### SLO Status and Error Budget

When viewing the details of an SLO, the application automatically calculates its current status and remaining error budget.

- **Status Thresholds**: 
  - **Healthy** (Green): Error Budget >= 25%
  - **Warning** (Yellow): Error Budget 0% to 25%
  - **Breaching** (Red): Error Budget < 0%
- **Rolling Window**: By default, calculations are performed over a 30-day rolling window (customizable via `timeWindow` in the SLO specification).
- **Target**: The target percentage is extracted from the `objectives` section of the SLO specification.
- **Current SLO**: Calculated as the average of all linked SLI metric values within the rolling window.
- **Error Budget**: Calculated as the remaining percentage of allowed unreliability.

#### SLO Specification Example with Calculation Parameters

```yaml
apiVersion: openslo/v1
kind: SLO
metadata:
  name: web-latency
spec:
  service: payment-service
  indicator: latency-sli
  timeWindow:
    - duration: 30d
  objectives:
    - target: 0.99
```

## REST API Reference

The MeloSlo API is accessible under the `/api/v1` base path.

### OpenSLO Records (`/api/v1/openslo`)

Manage Services, SLIs, SLOs, and DataSources.

- **`GET /api/v1/openslo`**: Retrieve all OpenSLO records.
  - **Query Parameters**: `kind` (optional) - Filter records by type (`Service`, `SLI`, `SLO`, `DataSource`).
  - **Response**: List of OpenSlo objects.
- **`GET /api/v1/openslo/dashboard-stats`**: Retrieve aggregated statistics for the dashboard.
  - **Response**: `DashboardStats` object (Total Services, SLOs, Healthy, Warning, Breaching).
- **`GET /api/v1/openslo/{id}`**: Retrieve a specific record by its database ID.
  - **Response**: A single OpenSlo object or 404.
- **`POST /api/v1/openslo`**: Create a new OpenSLO record.
  - **Request Body**: JSON representation of an `OpenSlo` object. Required fields: `apiVersion`, `kind`, `name`, `spec`.
  - **Response**: The created record (201 Created).
- **`PUT /api/v1/openslo/{id}`**: Update an existing record.
  - **Request Body**: JSON representation of the updated `OpenSlo` object.
  - **Response**: The updated record.
- **`DELETE /api/v1/openslo/{id}`**: Delete a record and its associations.
  - **Response**: 204 No Content.
- **`GET /api/v1/openslo/{id}/report`**: (SLO records only) Retrieve a comprehensive status report.
  - **Response**: `SloReport` containing current value, target, status (Healthy/Warning/Breaching), and error budget.
- **`GET /api/v1/openslo/{id}/service-report`**: (Service records only) Retrieve a report for all SLOs in a service.
  - **Response**: `ServiceReport` aggregating data from all child SLOs.
- **`GET /api/v1/openslo/{id}/sli-report`**: (SLI records only) Retrieve a detailed performance report for an SLI.
  - **Response**: `SliReport` containing recent metric history and averages.

### Metrics API (`/api/v1/metrics`)

Manage raw performance data.

- **`GET /api/v1/metrics`**: Retrieve all recorded performance metrics.
  - **Response**: List of `SliMetric` objects.
- **`POST /api/v1/metrics`**: Manually record a new metric value for an SLI.
  - **Request Body**: `SliMetric` object linking to an SLI and DataSource.
  - **Response**: The created metric (201 Created).

### Database Query Tool (`/api/v1/database`) (Administrator Only)

Power user tools for database exploration. Access is restricted to users with the **ADMIN** role.

- **`POST /api/v1/database/query`**: Execute a raw SQL query.
  - **Request Body**: `{"sql": "SELECT * FROM openslo_records"}`.
  - **Constraint**: Only `SELECT` statements are permitted for security reasons.
  - **Response**: List of results as JSON objects.
- **`GET /api/v1/database/tables`**: List all tables in the database.
  - **Response**: List of table name strings.
- **`GET /api/v1/database/tables/{tableName}/structure`**: Retrieve schema information for a specific table.
  - **Response**: List of column metadata (name, type, nullability, default).

## Database Schema

MeloSlo uses the following tables in its relational schema (H2 Database):

### `openslo_records`
Stores all core OpenSLO entities.
- **`id`**: (Long, PK) Unique record identifier.
- **`api_version`**: (String) The OpenSLO specification version.
- **`kind`**: (String) Type of record: `Service`, `SLI`, `SLO`, `DataSource`, or `AlertingSource`.
- **`name`**: (String, Unique) The unique system identifier for the record.
- **`display_name`**: (String) Friendly name for display.
- **`spec`**: (Text) The full OpenSLO specification in YAML/JSON.
- **`department`**: (String) Organizational unit metadata.
- **`manager`**: (String) Manager metadata.
- **`refresh_rate`**: (Integer) Data collection interval for DataSources.
- **`last_refresh_time`**: (DateTime) Last time this DataSource was synced.
- **`alert_url`**: (String) The webhook URL for AlertingSources.
- **`alert_payload`**: (Text) The JSON payload template for AlertingSources.
- **`last_alert_time`**: (DateTime) Last time this SLO triggered an alert.
- **`service_id`**: (Long, FK) Parent service ID (for SLOs).
- **`datasource_id`**: (Long, FK) Associated data source ID (for SLIs).
- **`alerting_source_id`**: (Long, FK) Associated alerting source ID (for SLOs).

### `sli_metric`
Stores time-series performance data for SLIs. Metrics are collected by the scheduler based on the DataSource refresh rate.
- **`id`**: (Long, PK) Unique metric identifier.
- **`timestamp`**: (DateTime) Time the metric was recorded.
- **`metric_value`**: (Double) The actual value observed.
- **`sli_id`**: (Long, FK) The SLI record this metric belongs to.
- **`datasource_id`**: (Long, FK) The DataSource that provided this metric.

### `melo_users`
Manages application users and their access control permissions (Role-Based and Department-Based).
- **`id`**: (Long, PK) Unique user identifier.
- **`username`**: (String, Unique) User login name.
- **`email`**: (String) User email address.
- **`departments`**: (String) CSV list of assigned departments (e.g. `Finance,Engineering`).
- **`password`**: (String) BCrypt encoded user password.

### `slo_slis`
A join table representing the many-to-many relationship between SLOs and SLIs (Objectives and Indicators).
- **`slo_id`**: (Long, FK) Reference to an SLO record.
- **`sli_id`**: (Long, FK) Reference to an SLI record.

## Technical Details

- **Backend**: Spring Boot, Java 26, Maven.
- **Database**: H2 In-memory database (for development/demo).
- **Frontend**: Vue.js 3, Tailwind CSS, ApexCharts.
