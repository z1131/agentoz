package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.SessionStreamRegistry;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
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
    private AgentExecutionManager agentExecutionManager;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SessionStreamRegistry sessionStreamRegistry;

    @AgentTool(name = "call_agent", description = "调用另一个Agent执行任务，实现Agent间协作。可以指定目标Agent名称和具体任务。")
    public String callAgent(
            io.modelcontextprotocol.common.McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标Agent的名称（如 PaperSearcher）", required = true) String targetAgentName,
            @AgentParam(name = "task", value = "要执行的任务描述", required = true) String task,
            @AgentParam(name = "context", value = "附加的上下文信息（可选）", required = false) String context
    ) {
        try {
            log.info("[CallAgent] 开始处理调用请求, targetAgentName={}, task={}",
                    targetAgentName, task);

            // 1. 从 MCP Transport Context 获取会话信息和身份信息
            String sourceAgentId = "unknown";
            String sourceAgentName = "Assistant";
            String conversationId = null;

            if (ctx != null) {
                // 调试日志：检查关键请求头是否存在（支持大小写不敏感）
                boolean hasConvId = ctx.get("X-Conversation-ID") != null || ctx.get("x-conversation-id") != null;
                boolean hasAgentId = ctx.get("X-Agent-ID") != null || ctx.get("x-agent-id") != null;
                boolean hasAuth = ctx.get("Authorization") != null || ctx.get("authorization") != null;
                log.info("[CallAgent] MCP Transport Context 检查 - " +
                        "X-Conversation-ID: {}, X-Agent-ID: {}, Authorization: {}",
                        hasConvId, hasAgentId, hasAuth);

                // 从 X-Conversation-ID 请求头获取会话ID（支持大小写不敏感）
                Object convId = ctx.get("X-Conversation-ID");
                if (convId == null) convId = ctx.get("x-conversation-id");

                if (convId != null) {
                    conversationId = convId.toString();
                    log.info("[CallAgent] ✓ 从 X-Conversation-ID 请求头获取会话ID: {}", conversationId);
                } else {
                    log.warn("[CallAgent] ✗ 未找到 X-Conversation-ID 请求头");
                }

                // 从 X-Agent-ID 请求头获取 Agent ID（支持大小写不敏感）
                Object agentId = ctx.get("X-Agent-ID");
                if (agentId == null) agentId = ctx.get("x-agent-id");

                if (agentId != null) {
                    sourceAgentId = agentId.toString();
                    log.info("[CallAgent] ✓ 从 X-Agent-ID 请求头获取 AgentID: {}", sourceAgentId);
                } else {
                    log.warn("[CallAgent] ✗ 未找到 X-Agent-ID 请求头");
                }
            } else {
                log.error("[CallAgent] ✗ MCP Transport Context 为空");
            }

            // 查找发送者名称
            if (sourceAgentId != null && !sourceAgentId.equals("unknown")) {
                try {
                    AgentEntity sourceAgent = agentRepository.selectOne(
                            new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId)
                    );

                    if (sourceAgent != null) {
                        sourceAgentName = sourceAgent.getAgentName();
                        log.info("[CallAgent] ✓ 查找到发送者名称: {}", sourceAgentName);
                    } else {
                        log.warn("[CallAgent] ✗ 未找到 AgentID={} 对应的 Agent 记录", sourceAgentId);
                    }
                } catch (Exception e) {
                    log.warn("[CallAgent] ✗ 查找发送者名称失败: {}", e.getMessage());
                }
            }

            // 验证会话ID
            if (conversationId == null) {
                log.error("[CallAgent] ✗ 无法获取会话ID，调用失败");
                String debugInfo = "Error: 无法获取当前会话ID。\n" +
                       "调试信息:\n" +
                       "- MCP Transport Context: " + (ctx != null ? "存在" : "为空") + "\n";
                if (ctx != null) {
                    debugInfo += String.format("- X-Conversation-ID: %s\n", ctx.get("X-Conversation-ID") != null ? "存在" : "缺失");
                    debugInfo += String.format("- X-Agent-ID: %s\n", ctx.get("X-Agent-ID") != null ? "存在" : "缺失");
                    debugInfo += String.format("- Authorization: %s\n", ctx.get("Authorization") != null ? "存在" : "缺失");
                }
                debugInfo += "请确保 AgentExecutionManager 为 MCP 配置注入了 X-Conversation-ID 请求头。";
                return debugInfo;
            }

            log.info("[CallAgent] ✓ 验证通过 - Source[{}({})] -> TargetName[{}], ConvId={}",
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

            // 5. 构建执行请求上下文
            log.info("[CallAgent] → 开始本地调用 AgentExecutionManager, TargetAgentId={}, Message={}",
                    targetAgentId, finalMessage);

            final String currentConversationId = conversationId;
            final String finalTargetAgentName = targetAgentName;

            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder fullResponse = new StringBuilder();

            // 6. 本地异步调用执行管理器 (不走 Dubbo RPC)
            AgentExecutionManager.ExecutionContext executionContext = new AgentExecutionManager.ExecutionContext(
                    targetAgentId,
                    conversationId,
                    finalMessage,
                    "assistant",
                    sourceAgentName
            );

            agentExecutionManager.executeTask(
                    executionContext,
                    // 事件回调 (InternalCodexEvent)
                    (InternalCodexEvent event) -> {
                        if (event == null) return;

                        // 1. 广播流式事件给主会话 (实时透传)
                        try {
                            // 设置发送者名称（不修改原始 JSON，避免 StackOverflow）
                            event.setSenderName(finalTargetAgentName);
                            sessionStreamRegistry.broadcast(currentConversationId, event);
                        } catch (Exception e) {
                            log.warn("[CallAgent] 广播子任务事件失败: {}", e.getMessage());
                        }

                        // 2. 收集最终文本结果 (用于返回给主智能体)
                        // 使用 TaskResponseConverter 的逻辑提取文本
                        TaskResponse respDto = TaskResponseConverter.toTaskResponse(event);
                        if (respDto != null && respDto.getTextDelta() != null) {
                            fullResponse.append(respDto.getTextDelta());
                        } else if (respDto != null && respDto.getFinalResponse() != null) {
                            // 某些情况下会有最终回复
                            fullResponse.setLength(0);
                            fullResponse.append(respDto.getFinalResponse());
                        }
                    },
                    // 完成回调
                    () -> {
                        log.info("[CallAgent] ✓ 本地调用完成, 总长度: {}", fullResponse.length());
                        resultFuture.complete(fullResponse.toString());
                    },
                    // 错误回调
                    (Throwable throwable) -> {
                        log.error("[CallAgent] ✗ 本地调用异常", throwable);
                        resultFuture.completeExceptionally(throwable);
                    }
            );

            log.info("[CallAgent] → executeTask 本地任务已启动, 等待结果...");

            // 7. 等待结果 (最多5分钟)
            String result = resultFuture.get(5, TimeUnit.MINUTES);
            log.info("[CallAgent] ✓ 最终结果长度: {}, 内容预览: {}",
                    result.length(), result.isEmpty() ? "(空)" : result.substring(0, Math.min(100, result.length())));
            return result;

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