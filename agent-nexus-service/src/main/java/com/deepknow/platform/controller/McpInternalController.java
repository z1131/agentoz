package com.deepknow.platform.controller;

import com.deepknow.platform.model.AgentEntity;
import com.deepknow.platform.service.AgentManager;
import com.deepknow.platform.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台内置 MCP 服务器
 */
@RestController
@RequestMapping("/mcp/internal")
public class McpInternalController {

    private static final Logger log = LoggerFactory.getLogger(McpInternalController.class);

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentManager agentManager;

    /**
     * MCP 统一入口 (JSON-RPC 2.0)
     */
    @PostMapping
    public Map<String, Object> handleMcpRequest(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody Map<String, Object> request) {
        
        String method = (String) request.get("method");
        Object id = request.get("id");
        log.info("收到 MCP 请求: method={}, sessionId={}", method, sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            if ("tools/list".equals(method)) {
                response.put("result", listTools());
            } else if ("tools/call".equals(method)) {
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                response.put("result", callTool(sessionId, params));
            } else {
                response.put("error", Map.of("code", -32601, "message", "Method not found"));
            }
        } catch (Exception e) {
            log.error("MCP 执行异常", e);
            response.put("error", Map.of("code", -32603, "message", e.getMessage()));
        }

        return response;
    }

    private Map<String, Object> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 工具 1: call_agent
        Map<String, Object> callAgentTool = new HashMap<>();
        callAgentTool.put("name", "call_agent");
        callAgentTool.put("description", "调用当前会话中的另一个智能体协助工作。请输入目标智能体的 agent_name。");
        Map<String, Object> callSchema = new HashMap<>();
        callSchema.put("type", "object");
        callSchema.put("properties", Map.of(
            "target_name", Map.of("type", "string", "description", "目标智能体名称"),
            "message", Map.of("type", "string", "description", "要发送的消息")
        ));
        callSchema.put("required", List.of("target_name", "message"));
        callAgentTool.put("inputSchema", callSchema);
        tools.add(callAgentTool);

        // 工具 2: write_resource (存引用)
        Map<String, Object> writeTool = new HashMap<>();
        writeTool.put("name", "write_resource");
        writeTool.put("description", "在共享空间存储或更新一份数据（如论文、长代码），返回引用 Key。");
        Map<String, Object> writeSchema = new HashMap<>();
        writeSchema.put("type", "object");
        writeSchema.put("properties", Map.of(
            "key", Map.of("type", "string", "description", "资源唯一标识，如 thesis_v1"),
            "content", Map.of("type", "string", "description", "数据全量内容")
        ));
        writeSchema.put("required", List.of("key", "content"));
        writeTool.put("inputSchema", writeSchema);
        tools.add(writeTool);

        // 工具 3: read_resource (读引用)
        Map<String, Object> readTool = new HashMap<>();
        readTool.put("name", "read_resource");
        readTool.put("description", "根据 Key 从共享空间读取全量数据内容。");
        Map<String, Object> readSchema = new HashMap<>();
        readSchema.put("type", "object");
        readSchema.put("properties", Map.of(
            "key", Map.of("type", "string", "description", "资源唯一标识")
        ));
        readSchema.put("required", List.of("key"));
        readTool.put("inputSchema", readSchema);
        tools.add(readTool);

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return result;
    }

    private Map<String, Object> callTool(String sessionId, Map<String, Object> params) {
        String name = (String) params.get("name");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");
        
        if ("call_agent".equals(name)) {
            return handleCallAgent(sessionId, args);
        } else if ("write_resource".equals(name)) {
            return handleWriteResource(sessionId, args);
        } else if ("read_resource".equals(name)) {
            return handleReadResource(sessionId, args);
        }
        
        throw new RuntimeException("Unknown tool: " + name);
    }

    private Map<String, Object> handleCallAgent(String sessionId, Map<String, Object> args) {
        String targetName = (String) args.get("target_name");
        String message = (String) args.get("message");
        AgentEntity targetAgent = conversationService.getAgentByName(sessionId, targetName)
                .orElseThrow(() -> new RuntimeException("未找到智能体: " + targetName));
        String result = agentManager.processTask(targetAgent.getAgentId(), message);
        return wrapMcpResult(result);
    }

    private Map<String, Object> handleWriteResource(String sessionId, Map<String, Object> args) {
        String key = (String) args.get("key");
        String content = (String) args.get("content");
        log.info("写入资源: sessionId={}, key={}", sessionId, key);
        // 这里直接使用 JDBC 或现有 Repository 存储
        // 演示目的，我们直接注入 jdbcTemplate 或使用简单的逻辑
        String sql = "INSERT INTO session_resources (session_id, resource_key, content) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE content = ?, updated_at = NOW()";
        // 注意：实际项目中应使用 Repository 封装
        jdbcTemplate.update(sql, sessionId, key, content, content);
        return wrapMcpResult("资源已成功存储，Key: " + key);
    }

    private Map<String, Object> handleReadResource(String sessionId, Map<String, Object> args) {
        String key = (String) args.get("key");
        log.info("读取资源: sessionId={}, key={}", sessionId, key);
        String sql = "SELECT content FROM session_resources WHERE session_id = ? AND resource_key = ?";
        String content = jdbcTemplate.queryForObject(sql, String.class, sessionId, key);
        return wrapMcpResult(content);
    }

    private Map<String, Object> wrapMcpResult(String text) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        return Map.of("content", content);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
}