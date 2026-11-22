# Changelog

## [Unreleased]
### Security
- The H2 console (`/h2-console`) is off by default and enabled only under the `dev` profile, where it requires the ADMIN role (#35).
- Live mode no longer creates `admin/admin` or `testuser/testuser`. It creates only `admin`, with a password from `APP_ADMIN_INITIAL_PASSWORD` or a random one logged once (#35).
- Test mode now logs a WARN that the demo accounts are active (#35).

### Removed
- The SQL query console (`POST /api/v1/database/query`) and its UI. Queries using `FILE_READ`, `CSVWRITE` or `CREATE ALIAS` can no longer be run (#35).

### Changed
- The "Query Tool" view is now "Database Tables" and only browses table lists and structures (#35).
- Department scoping now applies to every OpenSLO read, report, create, update and delete, not only list views (#37). Non-admin users see only records in their own departments; a user with no departments sees nothing; records without a department are visible to administrators only.
- Creating a record in, or moving a record to, a department the caller is not in returns 403.
- `POST /api/v1/openslo` with an `id` in the body returns 400.
- `DELETE /api/v1/openslo/{id}` for an unknown id returns 404 instead of 204.

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
