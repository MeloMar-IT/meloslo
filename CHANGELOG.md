# Changelog

## [Unreleased]
### Changed
- Department scoping now applies to every OpenSLO read, report, create, update and delete, not only list views (#37). Non-admin users see only records in their own departments; a user with no departments sees nothing; records without a department are visible to administrators only.
- Creating a record in, or moving a record to, a department the caller is not in returns 403.
- `POST /api/v1/openslo` with an `id` in the body returns 400.
- `DELETE /api/v1/openslo/{id}` for an unknown id returns 404 instead of 204.

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
