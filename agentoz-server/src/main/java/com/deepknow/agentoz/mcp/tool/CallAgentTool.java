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
import io.jsonwebtoken.Claims;
import io.modelcontextprotocol.common.McpTransportContext;
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
            // 1. 身份识别 (从 McpTransportContext 获取，由 Starter 自动注入)
            String token = null;
            if (ctx != null) {
                Object securityToken = ctx.get("SECURITY_TOKEN");
                if (securityToken != null) {
                    token = securityToken.toString();
                } else {
                    // 备选：尝试直接获取 Authorization Key
                    Object auth = ctx.get("Authorization");
                    if (auth == null) auth = ctx.get("authorization");
                    if (auth != null) token = auth.toString();
                }
                
                // 清理 Bearer 前缀
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
            }

            String sourceAgentId = "unknown";
            String sourceAgentName = "Assistant";
            String conversationId = null;

                if (token != null) {
                    try {
                        Claims claims = jwtUtils.validateToken(token);
                        if (claims != null) {
                            sourceAgentId = claims.getSubject();
                            conversationId = claims.get("cid", String.class);
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