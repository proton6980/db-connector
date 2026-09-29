#!/usr/bin/env bash
# Phase 1 MCP 冒烟：Streamable HTTP 握手 + tools/list + 4 个正式 tool 实调 + 错误码断言。
# 前置：应用已在 63306 运行，且种子连接存在（默认 dm8-local）。
# 用法：CONN=dm8-local scripts/mcp-smoke.sh
set -euo pipefail
BASE=${BASE:-http://localhost:63306}
CONN=${CONN:-dm8-local}
MCP=${MCP:-$BASE/mcp}
AH="Accept: text/event-stream, application/json"

# Initialize（提取 session ID）
HEADERS=$(curl -s -D - -o /dev/null -X POST "$MCP" \
  -H 'Content-Type: application/json' -H "$AH" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}' 2>&1)
SESSION=$(echo "$HEADERS" | grep -i 'mcp-session-id' | tr -d '\r' | sed 's/.*: //')
[ -n "$SESSION" ] || { echo "FAIL: 未收到 Mcp-Session-Id"; exit 1; }

SH="Mcp-Session-Id: $SESSION"
post() { curl -s -X POST "$MCP" -H 'Content-Type: application/json' -H "$AH" -H "$SH" -d "$1"; }

# initialized notification
post '{"jsonrpc":"2.0","method":"notifications/initialized"}' > /dev/null 2>&1

# Extract JSON from SSE data: line
extract() { sed -n 's/^data://p' | head -1; }

# tools/list
TOOLS_RESP=$(post '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | extract)

# tool calls
CONN_RESP=$(post "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_connections\",\"arguments\":{}}}" | extract)
QUERY_RESP=$(post "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"query_database\",\"arguments\":{\"connection\":\"$CONN\",\"sql\":\"SELECT 1 AS ONE FROM DUAL\"}}}" | extract)
BLOCK_RESP=$(post "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"query_database\",\"arguments\":{\"connection\":\"$CONN\",\"sql\":\"DELETE FROM DUAL\"}}}" | extract)
TABLES_RESP=$(post "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"list_tables\",\"arguments\":{\"connection\":\"$CONN\"}}}" | extract)
DESC_RESP=$(post "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"describe_table\",\"arguments\":{\"connection\":\"$CONN\",\"table\":\"P1_PROBE_T\"}}}" | extract)

fail=0
check() { echo "$3" | grep -q "$1" && echo "PASS: $2" || { echo "FAIL: $2"; fail=1; }; }
check '"query_database"' 'tools/list 包含 query_database' "$TOOLS_RESP"
check '"list_connections"' 'tools/list 包含 list_connections' "$TOOLS_RESP"
check '"list_tables"' 'tools/list 包含 list_tables' "$TOOLS_RESP"
check '"describe_table"' 'tools/list 包含 describe_table' "$TOOLS_RESP"
check 'ONE' 'query_database 返回数据' "$QUERY_RESP"
check 'SQL_REJECTED' 'DELETE 被 SQL_REJECTED 拦截' "$BLOCK_RESP"
check 'P1_PROBE_T' 'list_tables 返回表清单（含探针表）' "$TABLES_RESP"
check 'AGE' 'describe_table 返回字段（探针表）' "$DESC_RESP"

exit $fail