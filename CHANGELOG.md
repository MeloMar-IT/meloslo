# Changelog

## [Unreleased]
### Changed
- Missing data is shown as "No data" everywhere (dashboard, API, UI and PDF). An SLI or SLO with no samples no longer counts as 100% healthy, and days without data are no longer shown as 100% in charts.
- The PDF report uses the same average, error budget and status rules as the dashboard. Gaps in the PDF are left empty instead of being filled with 0%.
- `DashboardStats` includes a `noDataSlos` count.
- The simulated failure rate is configurable with `app.datasource.simulated-failure-rate` (default `0.03`).

### Fixed
- A failed metric fetch no longer stores a `0.0` sample, which could breach an SLO and fire a false alert. The failure is recorded on the SLI in `last_fetch_failure_time` and `last_fetch_error` instead.

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
