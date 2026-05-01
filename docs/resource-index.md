# Resource Index

## Scope
This document clarifies which content is required in the main repository and which content should be managed as external assets.

## Required in main repository
- `src/`
- `README.md`
- `LICENSE`
- `.github/workflows/`
- `docs/resource-index.md`
- `docs/assets/screenshots/` lightweight README screenshots

## Externalized assets (not tracked in main repo)
- `天机AI助手/` (UI design drafts)
- `天机agent V0.3/` (prototype pages and heavy image assets)

## Recommended distribution for external assets
- GitHub Releases (versioned zip packages), or
- A dedicated assets repository, or
- Git LFS for teams that need large binary versioning.

## Suggested package naming
- `tianji-ui-assets-v<version>.zip`
- `tianji-prototype-assets-v<version>.zip`

## Collaboration guidance
- Keep source-code reviews in the main repo.
- Keep large visual iteration files in external packages.
- Reference package version/tag in release notes when updating visual materials.
