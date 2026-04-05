# GitFlow Workflow

This repository follows the **GitFlow** branching model, a strict and structured approach designed for scheduled releases.

## Core Branches

### `main`
- **Purpose**: Production-ready code.
- **Status**: Always stable and reflects the latest released version.
- **Deployment**: Any push/merge to `main` should trigger a production deployment.

### `develop`
- **Purpose**: Integration branch for features.
- **Status**: Latest delivered development changes for the next release.
- **Base Branch**: Created from `main`.

## Supporting Branches

### Feature Branches (`feature/*`)
- **Purpose**: Developing new features.
- **Base Branch**: `develop`.
- **Merge To**: `develop`.
- **Naming Convention**: `feature/short-description` (e.g., `feature/user-auth`).

### Release Branches (`release/*`)
- **Purpose**: Preparing for a new production release.
- **Base Branch**: `develop`.
- **Merge To**: `main` AND `develop`.
- **Naming Convention**: `release/vX.Y.Z` (e.g., `release/v1.0.0`).
- **Activities**: Bug fixes for the release, documentation updates.

### Hotfix Branches (`hotfix/*`)
- **Purpose**: Quickly fixing critical bugs in production.
- **Base Branch**: `main`.
- **Merge To**: `main` AND `develop`.
- **Naming Convention**: `hotfix/vX.Y.Z` (e.g., `hotfix/v1.0.1`).

## Workflow Steps

### 1. Starting a New Feature
```bash
git checkout develop
git pull origin develop
git checkout -b feature/my-new-feature
```

### 2. Finishing a Feature
- Create a Pull Request (PR) from `feature/my-new-feature` to `develop`.
- Once reviewed and merged:
```bash
git checkout develop
git pull origin develop
git branch -d feature/my-new-feature
```

### 3. Creating a Release
- When `develop` has reached the desired state for a release:
```bash
git checkout develop
git checkout -b release/v1.0.0
```
- Perform final bug fixes on this branch.

### 4. Finishing a Release
- Merge `release/v1.0.0` into `main` (and tag it).
- Merge `release/v1.0.0` back into `develop` to ensure it has any fixes made.

### 5. Creating a Hotfix
- When a bug is found in `main`:
```bash
git checkout main
git checkout -b hotfix/urgent-fix
```

### 6. Finishing a Hotfix
- Merge `hotfix/urgent-fix` into `main` (and tag it).
- Merge `hotfix/urgent-fix` into `develop`.
