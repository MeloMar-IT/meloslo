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
- Test mode seeds demo data only into an empty database. A restart no longer deletes or reloads existing data. New property `app.seed` (default `false`) adds only missing demo records and never deletes anything (#40).

### Fixed
- Restarting in test mode no longer wipes all records and users when a demo record was renamed or deleted, the `testuser` account was removed, or an SLI has fewer than 30 metrics, including SLIs created by users (#40).

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
