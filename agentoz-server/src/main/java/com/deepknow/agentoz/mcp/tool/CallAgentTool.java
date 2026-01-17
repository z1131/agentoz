package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.A2ATaskRegistry;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Call Agent Tool - 实现 Agent 间相互调用 (A2A 协作版)
 */
@Slf4j
@Component
public class CallAgentTool {

    @Autowired
    private AgentExecutionManager agentExecutionManager;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private A2ATaskRegistry a2aTaskRegistry;

    @AgentTool(name = "call_agent", description = "调用另一个Agent执行任务，实现Agent间协作。可以指定目标Agent名称和具体任务。")
    public String callAgent(
            io.modelcontextprotocol.common.McpTransportContext ctx,
            @AgentParam(name = "targetAgentName", value = "目标Agent的名称（如 PaperSearcher）", required = true) String targetAgentName,
            @AgentParam(name = "task", value = "要执行的任务描述", required = true) String task,
            @AgentParam(name = "context", value = "附加的上下文信息（可选）", required = false) String context
    ) {
        String subTaskId = UUID.randomUUID().toString();
        try {
            log.info("[CallAgent] 开始委派任务, targetAgentName={}, subTaskId={}", targetAgentName, subTaskId);

            // 1. 提取 A2A 上下文
            String sourceAgentId = getHeader(ctx, "X-Agent-ID");
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            String traceId = getHeader(ctx, "X-A2A-Trace-ID");
            String originAgentId = getHeader(ctx, "X-A2A-Origin-Agent-ID");
            String parentTaskIdFromHeader = getHeader(ctx, "X-A2A-Parent-Task-ID");
            int currentDepth = 0;
            String depthStr = getHeader(ctx, "X-A2A-Depth");
            if (depthStr != null) {
                try {
                    currentDepth = Integer.parseInt(depthStr);
                } catch (NumberFormatException ignored) {}
            }

            // 2. 递归深度检查
            if (currentDepth >= 5) {
                return "Error: 任务嵌套层次太深，为了系统安全已拦截。";
            }

            // 3. 查找目标 Agent
            AgentEntity targetAgent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
                            .eq(AgentEntity::getAgentName, targetAgentName)
            );
            if (targetAgent == null) return String.format("Error: 找不到 Agent '%s'。", targetAgentName);

            // 查找源 Agent 名称
            String sourceAgentName = "Assistant";
            if (sourceAgentId != null) {
                AgentEntity sourceAgent = agentRepository.selectOne(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, sourceAgentId));
                if (sourceAgent != null) sourceAgentName = sourceAgent.getAgentName();
            }

            // 5. 构建 A2A 上下文接力
            A2AContext parentA2aContext = A2AContext.builder()
                    .traceId(traceId != null ? traceId : UUID.randomUUID().toString())
                    .parentTaskId(parentTaskIdFromHeader)
                    .depth(currentDepth)
                    .originAgentId(originAgentId != null ? originAgentId : sourceAgentId)
                    .build();
            
            // 派生下一级
            A2AContext childA2aContext = parentA2aContext.next(parentTaskIdFromHeader != null ? parentTaskIdFromHeader : sourceAgentId);

            // 6. 执行子任务
            final CompletableFuture<String> resultFuture = new CompletableFuture<>();
            final StringBuilder fullResponse = new StringBuilder();
            final String parentTaskId = parentTaskIdFromHeader != null ? parentTaskIdFromHeader : conversationId;

            AgentExecutionManager.ExecutionContextExtended executionContext =
                    new AgentExecutionManager.ExecutionContextExtended(
                            targetAgent.getAgentId(),
                            conversationId,
                            task + (context != null ? "\n\nContext:\n" + context : ""),
                            "assistant",
                            sourceAgentName,
                            true,
                            childA2aContext
                    );

            agentExecutionManager.executeTaskExtended(
                    executionContext,
                    (InternalCodexEvent event) -> {
                        if (event == null) return;
                        event.setSenderName(targetAgentName);
                        
                        // ⭐ 关键修正：将事件推给父任务，而不是推给自己（防止无限递归）
                        a2aTaskRegistry.sendEvent(parentTaskId, event);

                        TaskResponse respDto = TaskResponseConverter.toTaskResponse(event);
                        if (respDto != null && respDto.getTextDelta() != null) {
                            fullResponse.append(respDto.getTextDelta());
                        }
                    },
                    () -> {
                        // 子任务完成，resultFuture 拿到最终结果返回给主智能体
                        resultFuture.complete(fullResponse.toString());
                    },
                    (Throwable t) -> {
                        resultFuture.completeExceptionally(t);
                    }
            );


            // 7. 暂时保留阻塞 (待第三阶段重构为完全异步)
            return resultFuture.get(30, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("CallAgent 执行异常", e);
            return "Error: " + e.getMessage();
        }
    }

    private String getHeader(McpTransportContext ctx, String headerName) {
        if (ctx == null) return null;
        Object val = ctx.get(headerName);
        if (val == null) val = ctx.get(headerName.toLowerCase());
        return val != null ? val.toString() : null;
    }
}
