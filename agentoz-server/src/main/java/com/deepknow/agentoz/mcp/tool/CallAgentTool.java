package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import com.deepknow.agentoz.starter.util.McpSecurityUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Call Agent Tool - 实现 Agent 间相互调用
 */
@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionService agentExecutionService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AgentRepository agentRepository;

    @AgentTool(name = "call_agent", description = "调用另一个Agent执行任务，实现Agent间协作。可以指定目标Agent名称和具体任务。")
    public String callAgent(
            io.modelcontextprotocol.common.McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标Agent的名称（如 PaperSearcher）", required = true) String targetAgentName,
            @AgentParam(name = "task", value = "要执行的任务描述", required = true) String task,
            @AgentParam(name = "context", value = "附加的上下文信息（可选）", required = false) String context
    ) {
        try {
            // 1. 身份识别 (优先使用通用的 SecurityUtils)
            String token = McpSecurityUtils.getCurrentToken();
            
            // 2. 暴力探测 McpTransportContext (如果 SecurityUtils 失败)
            if (token == null && ctx != null) {
                log.info("🔍 [MCP Debug] 开始探测 McpTransportContext: Class={}", ctx.getClass().getName());
                try {
                    // --- 方案 A: 尝试您指定的 http_headers ---
                    Object hh = ctx.get("http_headers");
                    log.info("🔍 [MCP Debug] ctx.get(\"http_headers\") -> {}", hh);
                    if (hh instanceof java.util.Map) {
                        token = extractTokenFromMap((java.util.Map<?, ?>) hh);
                        if (token != null) log.info("✅ [MCP Debug] 从 http_headers 成功拿到 Token");
                    }

                    // --- 方案 B: 尝试 headers ---
                    if (token == null) {
                        Object h = ctx.get("headers");
                        log.info("🔍 [MCP Debug] ctx.get(\"headers\") -> {}", h);
                        if (h instanceof java.util.Map) {
                            token = extractTokenFromMap((java.util.Map<?, ?>) h);
                            if (token != null) log.info("✅ [MCP Debug] 从 headers 成功拿到 Token");
                        }
                    }

                    // --- 方案 C: 暴力反射私有字段 (终极手段) ---
                    if (token == null) {
                        log.info("🔍 [MCP Debug] 正在反射探测对象结构...");
                        for (java.lang.reflect.Field f : ctx.getClass().getDeclaredFields()) {
                            try {
                                f.setAccessible(true);
                                Object val = f.get(ctx);
                                log.info("🔍 [MCP Debug] Field [{}] -> {}", f.getName(), val);
                                if (val instanceof java.util.Map) {
                                    token = extractTokenFromMap((java.util.Map<?, ?>) val);
                                    if (token != null) log.info("✅ [MCP Debug] 从私有字段 [{}] 成功拿到 Token", f.getName());
                                }
                            } catch (Exception ignored) {}
                        }
                        
                        // 同时探测所有方法返回值
                        for (java.lang.reflect.Method m : ctx.getClass().getMethods()) {
                            if (m.getParameterCount() == 0 && !m.getName().startsWith("wait") && !m.getName().startsWith("notify")) {
                                try {
                                    Object val = m.invoke(ctx);
                                    if (val != null) {
                                        log.info("🔍 [MCP Debug] Method [{}] -> {}", m.getName(), val);
                                        if (val instanceof java.util.Map) {
                                            token = extractTokenFromMap((java.util.Map<?, ?>) val);
                                            if (token != null) log.info("✅ [MCP Debug] 从方法 [{}] 成功拿到 Token", m.getName());
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Throwable e) {
                    log.error("❌ [MCP Debug] 反射探测异常", e);
                }
            }

            String sourceAgentId = "unknown";
            String sourceAgentName = "Assistant";
            String conversationId = null;

            if (token != null) {
                try {
                    Claims claims = jwtUtils.validateToken(token);
                    if (claims != null) {
                        // --- 埋点：打印 Token 内的所有 Claims ---
                        log.info("=== JWT CLAIMS DEBUG ===");
                        claims.forEach((k, v) -> log.info("Claim [{}]: {}", k, v));
                        log.info("========================");
                        
                        sourceAgentId = claims.getSubject();
                        conversationId = claims.get("cid", String.class);
                        log.info("CallAgentTool: Token 解析成功. Subject={}, CID={}", sourceAgentId, conversationId);
                        
                        // 查找发送者名称
                        AgentEntity sourceAgent = agentRepository.selectOne(
                                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId)
                        );
                        if (sourceAgent != null) {
                            sourceAgentName = sourceAgent.getAgentName();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Token 解析或发送者识别异常: {}", e.getMessage());
                }
            }

            if (conversationId == null) {
                return "Error: 无法获取当前会话ID，请确保在有效的会话上下文中调用此工具。";
            }

            log.info(">>> MCP CallAgent 调用: Source[{}({})] -> TargetName[{}], ConvId={}",
                    sourceAgentName, sourceAgentId, targetAgentName, conversationId);

            // 3. 解析目标 Agent ID (Name -> ID)
            AgentEntity targetAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
                            .eq(AgentEntity::getAgentName, targetAgentName)
            );

            if (targetAgent == null) {
                return String.format("Error: 在当前会话中找不到名为 '%s' 的 Agent。请确认目标 Agent 名称是否正确。", targetAgentName);
            }

            String targetAgentId = targetAgent.getAgentId();

            // 4. 构建消息 (合并 context)
            String finalMessage = task;
            if (context != null && !context.isBlank()) {
                finalMessage = String.format("%s\n\n[Context]\n%s", task, context);
            }

            // 5. 构建执行请求
            ExecuteTaskRequest executeRequest = new ExecuteTaskRequest();
            executeRequest.setAgentId(targetAgentId);
            executeRequest.setConversationId(conversationId);
            executeRequest.setMessage(finalMessage);
            executeRequest.setRole("assistant"); 
            executeRequest.setSenderName(sourceAgentName);

            // 6. 同步调用 Agent 服务
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            StreamObserver<TaskResponse> responseObserver = new StreamObserver<TaskResponse>() {
                private final StringBuilder fullResponse = new StringBuilder();
                @Override
                public void onNext(TaskResponse response) {
                    if (response != null && response.getFinalResponse() != null) {
                        fullResponse.append(response.getFinalResponse());
                    }
                }
                @Override
                public void onError(Throwable throwable) {
                    log.error("Agent 调用失败", throwable);
                    resultFuture.completeExceptionally(throwable);
                }
                @Override
                public void onCompleted() {
                    resultFuture.complete(fullResponse.toString());
                }
            };

            agentExecutionService.executeTask(executeRequest, responseObserver);

            // 7. 等待结果 (最多5分钟)
            return resultFuture.get(5, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("CallAgent 工具执行异常", e);
            return "Error: 工具执行失败 - " + e.getMessage();
        }
    }

    private String extractTokenFromMap(java.util.Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toString().equalsIgnoreCase("Authorization")) {
                String val = entry.getValue().toString();
                if (val.startsWith("Bearer ")) {
                    return val.substring(7);
                }
                return val;
            }
        }
        return null;
    }
}
