# MeloSlo User Guide

Welcome to the **MeloSlo** user guide. MeloSlo is a comprehensive platform designed for managing OpenSLO records (Business Services, SLIs, and SLOs) through a professional interface. This guide will walk you through the key features and workflows of the application.

## Terms of Use & Disclaimer
MeloSlo is an open-source tool provided **"as is" and on your own risk**. The developers are not liable for any data loss, system instability, or other issues resulting from its use. By using this platform, you agree to these terms.

---

## 1. Getting Started

### 1.1 Authentication
To access MeloSlo, you must first log in using your credentials. The platform supports role-based access control to ensure that only authorized users can view or manage specific departmental records.

![Login Screen](Screenshots/Screenshot%202026-04-06%20at%2008.42.34.png)

*   **Default Administrator**: `admin` / `admin`
*   **Test User**: `testuser` / `testuser`

### 1.2 Configuration Modes (Test vs Live)

The application features a configuration option to toggle between a test environment and a live environment. This is controlled by the `app.datasource.mode` property.

- **Test Mode**: (`app.datasource.mode=test`) Use this mode to explore the application with simulated data. The system will automatically populate the database with sample SLOs, SLIs, and metrics.
- **Live Mode**: (`app.datasource.mode=live`) Use this mode for real-world monitoring. In live mode, the system expects to connect to the actual endpoints defined in your DataSources to fetch real-time metrics. Automated sample data generation is disabled to preserve your production configuration.

To change the mode, modify `src/main/resources/application.properties`:
```properties
# Toggle between 'test' (simulated) and 'live' (real endpoints)
app.datasource.mode=live
```

### 1.3 DTAP Environments (Dev, Test, Acceptance, Prod)

MeloSlo supports multi-environment configuration (DTAP) using Spring Boot's built-in profile mechanism.

#### Working with Profiles

Environment-specific settings can be defined in separate property files:
- `application-dev.properties`: Settings for local development.
- `application-prod.properties`: Settings for production deployments.

To activate a specific profile at startup:
```bash
java -Dspring.profiles.active=prod -jar meloslo.jar
```

#### Custom External Properties

To use an external property file at startup, use the `--spring.config.location` argument:
```bash
java -jar meloslo.jar --spring.config.location=file:/etc/meloslo/custom-application.properties
```

---

## 2. Navigation & Dashboard

### 2.1 The Dashboard
After logging in, you are presented with the **Dashboard**, which provides a high-level overview of the health of all monitored services and SLOs.

![Dashboard Overview](Screenshots/Screenshot%202026-04-06%20at%2008.46.22.png)

The Dashboard shows:
- **Total Business Services**: The count of all logical collections of SLOs.
- **Total SLOs**: The total number of objectives being tracked.
- **Healthy/Warning/Breaching**: Real-time status indicators based on error budget consumption.

---

## 3. Managing OpenSLO Records

### 3.1 Business Services
The **Business Services** view allows you to manage logical collections of SLOs that represent your systems or components.

![Services View](Screenshots/Screenshot%202026-04-06%20at%2008.42.54.png)

### 3.2 Service Level Indicators (SLIs)
SLIs define the metrics that measure the performance of your services. All SLI values in MeloSlo are normalized to a range of **0 to 100**.

![SLI Management](Screenshots/Screenshot%202026-04-06%20at%2008.43.15.png)

### 3.3 Service Level Objectives (SLOs)
SLOs define your performance targets. You can view detailed information about each SLO, including its current status and remaining error budget.

![SLO Management](Screenshots/Screenshot%202026-04-06%20at%2008.43.04.png)

#### 3.3.1 Creating/Editing an SLO
When creating or editing an SLO, you can use the sidebar to navigate and the top right button to initiate the creation process.

#### 3.3.2 SLO Details & Charts
MeloSlo provides visualization of performance history and error budget consumption for each SLO.

![SLO Details](Screenshots/Screenshot%202026-04-06%20at%2008.43.10.png)

---

## 4. Infrastructure & Integrations

### 4.1 Data Sources
Manage your external monitoring systems like Prometheus, Datadog, or Elasticsearch.

![Data Sources](Screenshots/Screenshot%202026-04-06%20at%2008.43.39.png)

#### 4.1.1 Adding a Data Source
Use pre-defined templates to quickly configure connection details for various providers.

![Data Source Details](Screenshots/Screenshot%202026-04-06%20at%2008.43.43.png)

### 4.2 Alerting Sources
Configure notification channels like Slack webhooks or PagerDuty to receive alerts when SLOs are breached.

![Alerting Sources](Screenshots/Screenshot%202026-04-06%20at%2008.43.47.png)

---

## 5. Analytics & Reporting

### 5.1 Performance Reports
Generate professional PDF reports that aggregate performance data over the last year. These reports include compliance-aware graphs and detailed error budget analysis. You can customize the report content and period.

![Report Options](Screenshots/Screenshot%202026-04-06%20at%2008.43.56.png)

![Reports Selection](Screenshots/Screenshot%202026-04-06%20at%2008.43.52.png)

---

## 6. Administrative Tools

### 6.1 User Administration
Administrators can manage user accounts, roles, and departmental access restrictions.

![User Admin](Screenshots/Screenshot%202026-04-06%20at%2008.44.13.png)

### 6.2 Database Query Tool
A tool for administrators to execute raw SQL `SELECT` queries for deep inspection of system data.

![Database Query Tool](Screenshots/Screenshot%202026-04-06%20at%2008.44.07.png)

---

---

## 7. Documentation & In-App Help
The platform includes built-in documentation to assist users in understanding OpenSLO concepts and platform features.

![Documentation View](Screenshots/Screenshot%202026-04-06%20at%2008.44.03.png)

---

## 8. Secure Logout
Ensure your session is securely closed by using the Logout feature.

![Logout Confirmation](Screenshots/Screenshot%202026-04-05%20at%2011.26.28.png)
