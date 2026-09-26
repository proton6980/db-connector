#!/usr/bin/env bash
# Phase 0 MCP 冒烟：SSE 握手 + tools/list + tools/call。
# 前置：应用已在 8080 运行。设置 DM_URL 时额外验证 dm_probe。
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
OUT=$(mktemp)

curl -sN "$BASE/sse" > "$OUT" &
SSE_PID=$!
trap 'kill $SSE_PID 2>/dev/null || true; rm -f "$OUT"' EXIT

# 等待首帧，提取回传端点
ENDPOINT=""
for _ in $(seq 1 20); do
  ENDPOINT=$(grep -m1 '^data:' "$OUT" | sed 's/^data://' || true)
  [ -n "$ENDPOINT" ] && break
  sleep 0.5
done
[ -n "$ENDPOINT" ] || { echo "FAIL: 未收到 SSE endpoint 帧"; cat "$OUT"; exit 1; }
echo "endpoint=$ENDPOINT"

post() { curl -s -X POST "$BASE$ENDPOINT" -H 'Content-Type: application/json' -d "$1" > /dev/null; }

post '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}'
post '{"jsonrpc":"2.0","method":"notifications/initialized"}'
post '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
post '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ping","arguments":{}}}'
[ -n "${DM_URL:-}" ] && post '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"dmProbe","arguments":{}}}'

sleep 3
kill $SSE_PID 2>/dev/null || true

fail=0
check() { grep -q "$1" "$OUT" && echo "PASS: $2" || { echo "FAIL: $2"; fail=1; }; }
check '"serverInfo"' 'initialize 响应'
check '"ping"' 'tools/list 包含 ping'
check 'pong' 'tools/call ping 返回 pong'
[ -n "${DM_URL:-}" ] && check 'dm_probe=' 'dm_probe 返回真实数据'

echo "--- SSE 事件流 ---"
cat "$OUT"
exit $fail
