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
- Errors from the OpenSLO API are JSON objects with `status`, `error` and `message`. A missing or hidden record returns 404, a conflict returns 409 and invalid input returns 400. The `message` says what is wrong (#41).
- Deleting an SLI that an SLO uses, a DataSource that an SLI uses, or a Business Service that has SLOs returns 409 and deletes nothing. The message lists the links to remove first (#41).
- A name that another record already uses returns 409 on create and on update (#41).
- `POST /api/v1/reports/pdf` validates its options and returns 400 with a `message` when they are invalid. `ids` must contain at least one record, `timeValue` must be at least 1, and the period may be at most 10 years (3650 days, 120 months or 10 years) (#41).
- **Breaking API change:** `timeUnit` is required for `POST /api/v1/reports/pdf`. It no longer defaults to `YEARS`. Accepted values are `DAYS`, `MONTHS` and `YEARS`, in any letter case (#41).
- `lastRefreshTime` in a request is ignored on create and update. On create, `lastAlertTime`, `lastFetchFailureTime` and `lastFetchError` are cleared as well (#41).
- The SLOs chosen for a Business Service are now saved on create and update. A PUT replaces the list: SLOs left out are unlinked, and `slos: []` unlinks all of them. An SLO that belongs to another Business Service is moved to this one. A Business Service request must include `slos`. Unknown or hidden ids, non-SLO records and a missing `slos` return 400 and change nothing (#41).
- The UI shows the server's message when saving, generating a report or deleting fails (#41).

### Fixed
- Restarting in test mode no longer wipes all records and users when a demo record was renamed or deleted, the `testuser` account was removed, or an SLI has fewer than 30 metrics, including SLIs created by users (#40).
- A failed metric fetch no longer stores a `0.0` sample, which could breach an SLO and fire a false alert. The failure is recorded on the SLI in `last_fetch_failure_time` and `last_fetch_error` instead.
- A user could take over the administrator account by changing their username to `admin`, or by updating the admin account through the API (#36).
- Update, delete and report requests returned 404 for every error. A delete blocked by a link, a duplicate name and unexpected server errors all looked like "not found". Now only a missing or hidden record returns 404, and other errors return their own status (#41).
- A bad request on create returned 400 with no explanation. It now returns a JSON `message` (#41).
- A database constraint violation, such as a race on a unique name, returns 409 with a fixed message. The SQL text is never sent to the client (#41).

### Migration
- Existing users without a role are migrated at startup. `admin` becomes `ADMIN` and must change its password in live mode. All other users become `USER` (#36).

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
