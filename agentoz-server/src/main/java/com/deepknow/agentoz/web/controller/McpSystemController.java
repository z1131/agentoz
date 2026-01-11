package com.deepknow.agentoz.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.web.mcp.dto.McpProtocol;
import com.deepknow.agentoz.web.mcp.dto.McpProtocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AgentOZ System MCP Server
 */
@Slf4j
@RestController
@RequestMapping("/mcp/sys")
@CrossOrigin(origins = "*") // 允许跨域
public class McpSystemController {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentExecutionService agentExecutionService;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /**
     * 1. 标准 MCP SSE 握手 (GET)
     */
    @GetMapping("/sse")
    @CrossOrigin(origins = "*")
    public SseEmitter connectSse() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String sessionId = java.util.UUID.randomUUID().toString();
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/sys/message?sessionId=" + sessionId));
            activeEmitters.put(sessionId, emitter);
            emitter.onCompletion(() -> activeEmitters.remove(sessionId));
            emitter.onTimeout(() -> activeEmitters.remove(sessionId));
        } catch (IOException e) {
            log.error("Failed to send MCP endpoint event", e);
        }
        return emitter;
    }

    /**
     * 1.1 适配 streamable_http (POST)
     * 某些客户端(如Codex)配置为streamable_http时，会直接向配置的URL发POST请求进行初始化。
     * 我们将其转发给 handleMessage 处理。
     */
    @PostMapping("/sse")
    @CrossOrigin(origins = "*")
    public JsonRpcResponse handleStreamableHttpPost(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody JsonRpcRequest request
    ) {
        // 生成一个临时 sessionId，因为 streamable_http 这种模式下可能是无状态的
        // 或者客户端后续会在 header 里带 session？
        // 为了兼容，我们生成一个新的 sessionId
        String sessionId = "streamable-" + java.util.UUID.randomUUID().toString();
        return handleMessage(authHeader, sessionId, request);
    }

    /**
     * 2. MCP 消息处理端点 (JSON-RPC)
     */

    @PostMapping("/message")
    public JsonRpcResponse handleMessage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String sessionId,
            @RequestBody JsonRpcRequest request
    ) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        Claims claims = null;
        if (token != null) {
            claims = jwtUtils.validateToken(token);
        }

        if (token != null && claims == null) {
            return JsonRpcResponse.error(request.getId(), -32001, "Unauthorized: Invalid token");
        }

        try {
            switch (request.getMethod()) {
                case "initialize":
                    return handleInitialize(request);
                case "notifications/initialized":
                    // 必须返回内容以确保 Content-Type 头存在，否则 Codex 会报错
                    return JsonRpcResponse.success(request.getId(), "ack");
                case "tools/list":
                    return handleListTools(request);
                case "tools/call":
                    return handleCallTool(request, claims);
                case "ping":
                    return JsonRpcResponse.success(request.getId(), "pong");
                default:
                    return JsonRpcResponse.error(request.getId(), -32601, "Method not found");
            }
        } catch (Exception e) {
            log.error("MCP Handle Error", e);
            return JsonRpcResponse.error(request.getId(), -32000, "Internal error: " + e.getMessage());
        }
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        InitializeResult result = InitializeResult.builder()
                .protocolVersion("2024-11-05")
                .capabilities(ServerCapabilities.builder()
                        .tools(Collections.emptyMap())
                        .build())
                .serverInfo(ServerInfo.builder()
                        .name("AgentOZ-System")
                        .version("1.0.0")
                        .build())
                .build();
        return JsonRpcResponse.success(request.getId(), result);
    }

    private JsonRpcResponse handleListTools(JsonRpcRequest request) {
        ObjectNode inputSchema = JsonNodeFactory.instance.objectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = inputSchema.putObject("properties");
        
        properties.putObject("target_agent_role")
                .put("type", "string")
                .put("description", "目标智能体的角色名称 (如 'writer')");
        
        properties.putObject("instruction")
                .put("type", "string")
                .put("description", "具体任务指令");

        inputSchema.putArray("required").add("target_agent_role").add("instruction");

        Tool tool = Tool.builder()
                .name("sys_call_agent")
                .description("委派另一个智能体完成特定任务。")
                .inputSchema(inputSchema)
                .build();

        return JsonRpcResponse.success(request.getId(), ListToolsResult.builder()
                .tools(Collections.singletonList(tool)).build());
    }

    private JsonRpcResponse handleCallTool(JsonRpcRequest request, Claims claims) {
        String name = request.getParams().path("name").asText();
        if (!"sys_call_agent".equals(name)) {
            return JsonRpcResponse.error(request.getId(), -32601, "Unknown tool");
        }

        if (claims == null) {
            return JsonRpcResponse.error(request.getId(), -32001, "Unauthorized");
        }

        String conversationId = (String) claims.get("cid");
        JsonNode args = request.getParams().path("arguments");
        String targetRole = args.path("target_agent_role").asText();
        String instruction = args.path("instruction").asText();

        AgentEntity targetAgent = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getConversationId, conversationId)
                .eq(AgentEntity::getAgentName, targetRole)
                .last("LIMIT 1"));
        
        if (targetAgent == null) {
             return JsonRpcResponse.success(request.getId(), CallToolResult.builder().isError(true)
                     .content(List.of(ToolContent.builder().type("text").text("Agent not found").build())).build());
        }

        String resultText = executeSubTaskSync(targetAgent.getAgentId(), conversationId, instruction);
        return JsonRpcResponse.success(request.getId(), CallToolResult.builder().isError(false)
                .content(List.of(ToolContent.builder().type("text").text(resultText).build())).build());
    }

    private String executeSubTaskSync(String agentId, String conversationId, String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullResponse = new StringBuilder();
        ExecuteTaskRequest taskRequest = new ExecuteTaskRequest();
        taskRequest.setAgentId(agentId);
        taskRequest.setConversationId(conversationId);
        taskRequest.setMessage(message);

        agentExecutionService.executeTask(taskRequest, new StreamObserver<TaskResponse>() {
            @Override
            public void onNext(TaskResponse resp) {
                if (resp.getTextDelta() != null) fullResponse.append(resp.getTextDelta());
            }
            @Override
            public void onError(Throwable t) { future.completeExceptionally(t); }
            @Override
            public void onCompleted() { future.complete(fullResponse.toString()); }
        });

        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}