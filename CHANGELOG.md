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

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
