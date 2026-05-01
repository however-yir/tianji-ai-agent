#!/usr/bin/env bash
# E2E 冒烟测试骨架
# 用法: BASE_URL=http://localhost:8094 bash tests/e2e/smoke_test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8094}"
PASS=0
FAIL=0

assert_status() {
    local label="$1"
    local url="$2"
    local expected="$3"
    local actual
    actual=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    if [ "$actual" = "$expected" ]; then
        echo "  PASS: $label (HTTP $actual)"
        ((PASS++))
    else
        echo "  FAIL: $label (expected $expected, got $actual)"
        ((FAIL++))
    fi
}

assert_contains() {
    local label="$1"
    local url="$2"
    local pattern="$3"
    local body
    body=$(curl -s "$url" 2>/dev/null || echo "")
    if echo "$body" | grep -q "$pattern"; then
        echo "  PASS: $label"
        ((PASS++))
    else
        echo "  FAIL: $label (pattern '$pattern' not found)"
        ((FAIL++))
    fi
}

echo "=== tianji-ai-agent E2E Smoke Tests ==="
echo "Target: $BASE_URL"
echo ""

echo "-- Health Endpoints --"
assert_status "Actuator health" "$BASE_URL/actuator/health" "200"
assert_contains "Health status UP" "$BASE_URL/actuator/health" '"status":"UP"'

echo ""
echo "-- API Endpoints --"
assert_status "Chat text endpoint (no auth)" "$BASE_URL/chat/text" "200"  # dev-demo profile
assert_status "Swagger UI" "$BASE_URL/doc.html" "200"  # if enabled

echo ""
echo "-- Security Headers --"
HEADERS=$(curl -sI "$BASE_URL/actuator/health" 2>/dev/null)
if echo "$HEADERS" | grep -qi "x-content-type-options"; then
    echo "  PASS: X-Content-Type-Options header present"
    ((PASS++))
else
    echo "  FAIL: X-Content-Type-Options header missing"
    ((FAIL++))
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
