# Contributing

Thanks for your interest in improving this project.

## Development Setup
1. Install JDK 17+
2. Install Maven 3.9+
3. Validate locally:
   - `mvn -B -ntp test`
   - `mvn -B -ntp verify`

## Branch and Commit
- Branch from `main`
- Keep commits small and focused
- Prefer conventional commits: `feat:`, `fix:`, `docs:`, `chore:`

## Pull Request Guidelines
- Keep each PR scoped to one change set
- Add/update tests when behavior changes
- Update docs when API/config/usage changes
- Ensure CI passes before requesting review
- Do not commit generated artifacts (`target/`, logs, local env files)

## Code Style
- Follow existing project conventions
- Avoid unrelated refactors in feature/fix PRs
- Prefer clear naming and small methods
