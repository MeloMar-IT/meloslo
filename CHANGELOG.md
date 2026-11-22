# Changelog

## [Unreleased]
### Changed
- User management is restricted to administrators. Access rights come from a stored `role` column (`ADMIN` or `USER`) instead of the username `admin`.
- Users can change their own password through `PUT /api/v1/users/me/password`. This is the only user endpoint open to non-admins besides `GET /api/v1/users/me`.
- Creating and updating users validates input. Unknown user ids return 404, duplicate usernames return 409, and demoting or deleting the last administrator returns 409.
- Passwords must be 8–128 characters.
- Changing a user's role, password, or username, or deleting the user, ends that user's open sessions.

### Migration
- Existing users without a role are migrated at startup. `admin` becomes `ADMIN` and must change its password in live mode. All other users become `USER`.

### Fixed
- A user could take over the administrator account by changing their username to `admin`, or by updating the admin account through the API.

## [0.1.0] - 2026-04-05
### Added
- First beta release of the project.
