.PHONY: test eval replay manifest verify acceptance

test:
	cd web/chat-ui && npm run lint && npm run test:run && npm run build
	python3 -m pytest -q tests
	mvn -B -ntp -f src/tjxt/tj-aigc/pom.xml verify checkstyle:check spotbugs:check

eval:
	python3 scripts/evaluation/evaluate_route_agent.py

replay:
	python3 scripts/replay_agent_run.py

manifest:
	python3 scripts/generate_agent_manifest.py --verify

acceptance:
	bash scripts/acceptance.sh

verify:
	bash scripts/verify.sh
