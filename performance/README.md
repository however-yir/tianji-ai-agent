# Hermetic performance profiles

`performance/sse_load_test.py` measures Tianji's own orchestration, SSE contract and
runtime path — **not** the model vendor network. It must be pointed at a locally running
tj-aigc started with the **dev-demo** profile, which serves deterministic fake replies
(no real LLM API key, no external calls).

## How to run

```bash
# terminal 1: start hermetic service
SPRING_PROFILES_ACTIVE=dev-demo mvn -f src/tjxt/pom.xml -pl tj-aigc -am package
cd src/tjxt/tj-aigc && java -jar target/tj-aigc.jar

# terminal 2: load profiles
python3 performance/sse_load_test.py --concurrency 1 --requests 5      # smoke
python3 performance/sse_load_test.py --concurrency 10 --requests 50    # 10 concurrent
python3 performance/sse_load_test.py --concurrency 50 --requests 100   # 50 concurrent
python3 performance/sse_load_test.py --concurrency 100 --requests 200  # 100 concurrent (manual)
```

## Output

```text
request count, error rate, success rate
time-to-first-event P50 / P95 / P99
stream duration P50 / P95 / P99
SSE completion rate (STOP observed)
missing-STOP rate
```

The script exits non-zero when missing-STOP rate > 0 or error rate >= 5%.

## What is and is not claimable

- Measured values are **local, hermetic, fake-model** numbers. They demonstrate that the
  orchestration layer holds under concurrency; they are **not** production QPS numbers.
- Real-model load testing is manual opt-in only (point the script at a service with a real
  model profile) and is not part of CI.

## CI / acceptance

Load tests are **manual/scheduled**, not part of the blocking main CI: they need a running
service. The blocking acceptance suite covers the same contract deterministically without
concurrency load.
