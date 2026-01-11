#!/bin/bash

# AgentOZ MCP Server 测试脚本
# 用于测试 MCP Server 的各种功能

BASE_URL="http://localhost:8003"
MCP_ENDPOINT="$BASE_URL/mcp/agent"

echo "========================================="
echo "AgentOZ MCP Server 测试脚本"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 1. 健康检查
echo -e "${BLUE}测试 1: 健康检查${NC}"
echo "GET $MCP_ENDPOINT/health"
curl -s "$MCP_ENDPOINT/health" | jq .
echo ""
echo ""

# 2. 初始化 MCP 连接
echo -e "${BLUE}测试 2: 初始化 MCP 连接${NC}"
echo "POST $MCP_ENDPOINT/message"
curl -s -X POST "$MCP_ENDPOINT/message" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {
        "tools": {}
      },
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }' | jq .
echo ""
echo ""

# 3. 列出可用工具
echo -e "${BLUE}测试 3: 列出可用工具${NC}"
echo "POST $MCP_ENDPOINT/message"
curl -s -X POST "$MCP_ENDPOINT/message" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list"
  }' | jq .
echo ""
echo ""

# 4. 调用 call_agent 工具（简单示例）
echo -e "${BLUE}测试 4: 调用 call_agent 工具${NC}"
echo "POST $MCP_ENDPOINT/message"
curl -s -X POST "$MCP_ENDPOINT/message" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "call_agent",
      "arguments": {
        "targetAgentId": "test-agent-001",
        "task": "请介绍一下你自己",
        "context": "{\"userId\": \"test-user\"}",
        "conversationId": "test-conv-001"
      }
    }
  }' | jq .
echo ""
echo ""

# 5. Ping 测试
echo -e "${BLUE}测试 5: Ping 测试${NC}"
echo "POST $MCP_ENDPOINT/message"
curl -s -X POST "$MCP_ENDPOINT/message" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "ping"
  }' | jq .
echo ""
echo ""

# 6. 测试 SSE 连接（使用 timeout 避免挂起）
echo -e "${BLUE}测试 6: SSE 连接测试${NC}"
echo "GET $MCP_ENDPOINT/sse"
echo "提示: 按 Ctrl+C 退出 SSE 连接"
echo ""
timeout 3 curl -N "$MCP_ENDPOINT/sse" -H "Accept: text/event-stream" || true
echo ""
echo ""

echo "========================================="
echo -e "${GREEN}测试完成！${NC}"
echo "========================================="
