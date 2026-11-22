# Changelog

## [Unreleased]
### Security
- The H2 console (`/h2-console`) is off by default and enabled only under the `dev` profile, where it requires the ADMIN role (#35).
- Live mode no longer creates `admin/admin` or `testuser/testuser`. It creates only `admin`, with a password from `APP_ADMIN_INITIAL_PASSWORD` or a random one logged once (#35).
- Test mode now logs a WARN that the demo accounts are active (#35).

### Removed
- The SQL query console (`POST /api/v1/database/query`) and its UI. Queries using `FILE_READ`, `CSVWRITE` or `CREATE ALIAS` can no longer be run (#35).
- Dependabot turned off; dependency updates go through issues (#51).

### Changed
- The "Query Tool" view is now "Database Tables" and only browses table lists and structures (#35).
- Department scoping now applies to every OpenSLO read, report, create, update and delete, not only list views (#37). Non-admin users see only records in their own departments; a user with no departments sees nothing; records without a department are visible to administrators only.
- Creating a record in, or moving a record to, a department the caller is not in returns 403.
- `POST /api/v1/openslo` with an `id` in the body returns 400.
- `DELETE /api/v1/openslo/{id}` for an unknown id returns 404 instead of 204.
- Test mode seeds demo data only into an empty database. A restart no longer deletes or reloads existing data. New property `app.seed` (default `false`) adds only missing demo records and never deletes anything (#40).
- Missing data is shown as "No data" everywhere (dashboard, API, UI and PDF). An SLI or SLO with no samples no longer counts as 100% healthy, and days without data are no longer shown as 100% in charts.
- The PDF report uses the same average, error budget and status rules as the dashboard. Gaps in the PDF are left empty instead of being filled with 0%.
- `DashboardStats` includes a `noDataSlos` count.
- The simulated failure rate is configurable with `app.datasource.simulated-failure-rate` (default `0.03`).
- Spring Boot is upgraded from the end-of-life 3.2.0 to 3.5.7, which brings Spring Framework 6.2 and Spring Security 6.5. The 3.2.0 line had published CVEs, including in `UriComponentsBuilder` (CVE-2024-22243, CVE-2024-22259, CVE-2024-22262) (#43).
- User management is restricted to administrators. Access rights come from a stored `role` column (`ADMIN` or `USER`) instead of the username `admin` (#36).
- Users can change their own password through `PUT /api/v1/users/me/password`. This is the only user endpoint open to non-admins besides `GET /api/v1/users/me`.
- Creating and updating users validates input. Unknown user ids return 404, duplicate usernames return 409, and demoting or deleting the last administrator returns 409.
- Passwords must be 8–128 characters.
- Changing a user's role, password, or username, or deleting the user, ends that user's open sessions.

### Fixed
- Restarting in test mode no longer wipes all records and users when a demo record was renamed or deleted, the `testuser` account was removed, or an SLI has fewer than 30 metrics, including SLIs created by users (#40).
- A failed metric fetch no longer stores a `0.0` sample, which could breach an SLO and fire a false alert. The failure is recorded on the SLI in `last_fetch_failure_time` and `last_fetch_error` instead.
- A user could take over the administrator account by changing their username to `admin`, or by updating the admin account through the API (#36).

### Migration
- Existing users without a role are migrated at startup. `admin` becomes `ADMIN` and must change its password in live mode. All other users become `USER` (#36).

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
