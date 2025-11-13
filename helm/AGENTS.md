# Repository Guidelines

## Project Structure & Module Organization
- `para/Chart.yaml` defines chart metadata and required Kubernetes version.
- `para/templates/` contains manifests (deployment, service, ingress)
- `para/templates/tests/test-connection.yaml` holds the Helm test pod.
- `para/values.yaml` centralizes defaults; mirror its structure when adding new settings to keep overrides predictable.

## Build, Test, and Development Commands
- `helm lint para` checks schema compliance and template syntax; run before every PR.
- `helm template para --values values.yaml` renders manifests locally for quick validation.
- `helm install dev ./para` deploys to a cluster; pair with `--dry-run` during review or use a temporary namespace.
- `helm upgrade dev ./para -f custom.yaml` exercises upgrades against personalized values files.

## Coding Style & Naming Conventions
- Use two-space YAML indentation and keep keys alphabetized within related blocks for readability.
- Prefer expressive value names (e.g., `paraEndpoint`, `imagePullSecrets`) that match Para’s configuration vocabulary.
- Place reusable snippets in `_helpers.tpl`; reference via `{{ include }}` rather than duplicating logic.

## Testing Guidelines
- Maintain the smoke test pod in `templates/tests`; extend it when adding services that need reachability checks.
- Run `helm test <release>` after installations or upgrades to confirm the chart’s live health checks.
- Keep any new template conditionals covered by unit-style render checks in CI (e.g., add cases to a makeshift `helm template` script if chart-testing is introduced).

## Commit & Pull Request Guidelines
- Commit subjects should be imperative and scoped (e.g., “add ingress annotations hint”); keep body wrapped at 72 characters.
- Reference chart paths in commit bodies when touching multiple manifests to ease review.
- PRs should describe the scenario they enable, link to related Para or infrastructure issues, list tested commands, and attach relevant manifest diffs or screenshots.

## Security & Configuration Tips
- Never hardcode secrets; point to existing Kubernetes Secrets via `values.yaml` keys and document expected keys.
