package com.deepknow.agentoz.orchestrator;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.OrchestrationSession;
import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Agent 编排器 - 中心节点
 *
 * <p>职责：</p>
 * <ul>
 *   <li>调度主 Agent 处理用户请求</li>
 *   <li>接收子任务请求，调度子 Agent</li>
   * <li>管理事件转发（子 Agent → 父 Agent → 用户）</li>
   *   <li>管理会话生命周期</li>
 *   <li>集成 Redis 队列处理并发调用</li>
 * </ul>
 */
@Slf4j
@Component
@DubboService(protocol = "tri", timeout = 3600000)
@RequiredArgsConstructor
public class AgentOrchestrator implements AgentExecutionService {

    private final AgentRepository agentRepository;
    private final AgentExecutionManager agentExecutionManager;
    private final RedisAgentTaskQueue redisAgentTaskQueue;

    /**
     * 会话管理器
     */
    private final OrchestrationSessionManager sessionManager = new OrchestrationSessionManager();

    /**
     * 启动主会话（处理用户请求）
     *
     * @param conversationId 会话 ID
     * @param mainAgentId 主 Agent ID
     * @param userMessage 用户消息
     * @param eventConsumer SSE 事件消费者
     * @return OrchestrationSession
     */
    public OrchestrationSession startMainSession(
            String conversationId,
            String mainAgentId,
            String userMessage,
            Consumer<InternalCodexEvent> eventConsumer
    ) {
        log.info("[Orchestrator] 启动主会话: convId={}, agentId={}", conversationId, mainAgentId);

        // 创建会话
        OrchestrationSession session = OrchestrationSession.builder()
                .sessionId(conversationId)
                .mainTaskId("main-" + conversationId)
                .currentAgentId(mainAgentId)
                .status(OrchestrationSession.SessionStatus.ACTIVE)
                .eventConsumer(eventConsumer)
                .build();

        // 注册会话
        sessionManager.registerSession(session);

        // 启动主 Agent
        agentExecutionManager.executeTaskExtended(
            new AgentExecutionManager.ExecutionContextExtended(
                mainAgentId,
                conversationId,
                userMessage,
                "user",
                "Orchestrator",
                false  // 主任务
            ),
            event -> {
                // 主 Agent 事件直接发送到前端
                session.sendEvent(event);
            },
            () -> {
                // 主 Agent 完成
                log.info("[Orchestrator] 主 Agent 完成: convId={}", conversationId);
                session.setStatus(OrchestrationSession.SessionStatus.IDLE);
            },
            error -> {
                // 主 Agent 失败
                log.error("[Orchestrator] 主 Agent 失败: convId={}, error={}",
                    conversationId, error.getMessage());
                session.setStatus(OrchestrationSession.SessionStatus.FAILED);
            }
        );

        return session;
    }

    /**
     * 提交子任务请求
     *
     * @param parentConversationId 父会话 ID
     * @param parentTaskId 父任务 ID
     * @param targetAgentId 目标 Agent ID
     * @param targetAgentName 目标 Agent 名称
     * @param taskDescription 任务描述
     * @param priority 优先级
     * @return 子任务 ID
     */
    public String submitSubTask(
            String parentConversationId,
            String parentTaskId,
            String targetAgentId,
            String targetAgentName,
            String taskDescription,
            String priority
    ) {
        log.info("[Orchestrator] 提交子任务: convId={}, parent={}, target={}",
            parentConversationId, parentTaskId, targetAgentName);

        // 检查目标 Agent 是否存在
        AgentEntity targetAgent = agentRepository.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getAgentId, targetAgentId)
        );

        if (targetAgent == null) {
            throw new RuntimeException("目标 Agent 不存在: " + targetAgentName);
        }

        // 获取会话
        OrchestrationSession session = sessionManager.getSession(parentConversationId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + parentConversationId);
        }

        // 检查目标 Agent 是否忙碌
        if (redisAgentTaskQueue.isAgentBusy(targetAgentId)) {
            // 提交到 Redis 队列
            log.info("[Orchestrator] Agent 忙碌，提交到队列: agent={}", targetAgentName);
            String taskId = redisAgentTaskQueue.enqueue(
                targetAgentId,
                targetAgentName,
                parentConversationId,
                parentTaskId,  // 作为 caller
                taskDescription,
                priority
            );

            // 记录调用关系
            session.addChildTask(parentTaskId, taskId);
            session.incrementActiveTasks();

            return taskId;
        } else {
            // 立即执行
            log.info("[Orchestrator] Agent 空闲，立即执行: agent={}", targetAgentName);
            return executeSubTask(
                session,
                parentTaskId,
                targetAgentId,
                taskDescription
            );
        }
    }

    /**
     * 立即执行子任务
     */
    private String executeSubTask(
            OrchestrationSession session,
            String parentTaskId,
            String agentId,
            String taskDescription
    ) {
        String taskId = UUID.randomUUID().toString();
        String conversationId = session.getSessionId();

        log.info("🚀 [Orchestrator] 执行子任务: taskId={}, agentId={}", taskId, agentId);

        // 记录调用关系
        session.addChildTask(parentTaskId, taskId);

        // 异步执行子任务
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🧵 [Orchestrator] 子任务开始: taskId={}", taskId);

                // 标记 Agent 为忙碌
                redisAgentTaskQueue.markAgentBusy(agentId, taskId);

                // 执行 Agent
                StringBuilder resultBuilder = new StringBuilder();

                agentExecutionManager.executeTaskExtended(
                    new AgentExecutionManager.ExecutionContextExtended(
                        agentId,
                        conversationId,
                        taskDescription,
                        "assistant",
                        "Orchestrator",
                        true  // 子任务
                    ),
                    event -> {
                        // 子 Agent 事件转发到会话
                        log.info("📡 [Orchestrator] 子任务事件: taskId={}, eventType={}",
                            taskId, event.getEventType());

                        // 转发事件到前端
                        session.sendEvent(event);

                        // 收集结果
                        if (event != null) {
                            String text = extractTextFromEvent(event);
                            if (text != null && !text.isEmpty()) {
                                resultBuilder.append(text);
                            }
                        }
                    },
                    () -> {
                        // 子任务完成
                        String result = resultBuilder.toString();
                        log.info("✅ [Orchestrator] 子任务完成: taskId={}, resultLength={}",
                            taskId, result.length());

                        // 处理队列中的下一个任务
                        redisAgentTaskQueue.processNextTask(agentId,
                            nextTaskId -> {
                                // 执行队列中的下一个任务
                                executeSubTask(session, taskId, agentId, nextTaskId);
                            });

                        // 标记完成
                        session.completeSubTask(taskId);
                        redisAgentTaskQueue.markAgentFree(agentId);
                    },
                    error -> {
                        // 子任务失败
                        log.error("❌ [Orchestrator] 子任务失败: taskId={}, error={}",
                            taskId, error.getMessage());

                        redisAgentTaskQueue.markAgentFree(agentId);
                        session.completeSubTask(taskId);
                    }
                );

            } catch (Exception e) {
                log.error("❌ [Orchestrator] 子任务异常: taskId={}, error={}",
                    taskId, e.getMessage(), e);
            }
        });

        return taskId;
    }

    /**
     * 提取事件中的文本内容
     */
    private String extractTextFromEvent(InternalCodexEvent event) {
        try {
            if (event.getRawEventJson() != null) {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(event.getRawEventJson());
                if (node.has("content")) {
                    return node.get("content").asText();
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return null;
    }

    /**
     * 获取会话
     */
    public OrchestrationSession getSession(String conversationId) {
        return sessionManager.getSession(conversationId);
    }

    /**
     * 结束会话
     */
    public void endSession(String conversationId) {
        log.info("[Orchestrator] 结束会话: convId={}", conversationId);
        sessionManager.unregisterSession(conversationId);
    }

    // ========== 实现 AgentExecutionService 接口 ==========

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + request.getConversationId();

        try {
            log.info("[AgentOrchestrator-API] 收到任务请求: {}, Role={}, AgentId={}",
                traceInfo, request.getRole(), request.getAgentId());

            // 使用 startMainSession 启动主会话
            OrchestrationSession session = startMainSession(
                request.getConversationId(),
                request.getAgentId(),
                request.getMessage(),
                event -> {
                    // 转换并发送事件
                    TaskResponse dto = TaskResponseConverter.toTaskResponse(event);
                    if (dto != null) {
                        responseObserver.onNext(dto);
                    }
                }
            );

            log.info("[AgentOrchestrator-API] 主会话已启动: sessionId={}, mainTaskId={}",
                session.getSessionId(), session.getMainTaskId());

        } catch (Exception e) {
            log.error("[AgentOrchestrator-API] 任务执行失败: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        // TODO: 实现双向流式调用
        return new StreamObserver<>() {
            @Override public void onNext(StreamChatRequest value) {}
            @Override public void onError(Throwable t) { responseObserver.onError(t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
