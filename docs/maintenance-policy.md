# Maintenance Policy

The project is feature-frozen at the Final RC. Future changes are limited to:

- security fixes and dependency maintenance (patches, known-CVE updates)
- bug fixes with a failing-test reproduction
- real user feedback or measured performance issues with evidence
- verified model/prompt upgrades that pass the offline gate + golden replay

Not accepted: new Agent types, new microservices, framework migrations, platform-style
extensions (marketplace, workflow engine, generic gateway) without a documented business
requirement and a real evidence chain.
